package com.example.droid_share.data

import android.content.Context
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import com.example.droid_share.DataConverter
import com.example.droid_share.FileManager
import com.example.droid_share.NotificationInterface
import com.example.droid_share.RxFileDescriptor
import com.example.droid_share.RxFilePackDescriptor
import com.example.droid_share.TxFileDescriptor
import com.example.droid_share.TxFilePackDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.math.min


class FileAsyncTask(
    private val context: Context,
    private var notifier : NotificationInterface
) {

    companion object {
        private const val TAG = "DeviceDetailFragment"

        const val PORT_NUMBER = 8888
        const val NUM_BYTES_PER_SIZE = 4
        const val CHUNK_SIZE = 4096
//         const val CHUNK_SIZE = 4096 * 4096

        private const val CONTINUE_TX_STR = "CONTINUE_TX"
        private val CONTINUE_TX_BYTES = DataConverter.string2Utf8(CONTINUE_TX_STR)
        private const val CANCEL_TX_STR = "CANCEL_TX"
        private val CANCEL_TX_BYTES = DataConverter.string2Utf8(CANCEL_TX_STR)

        private val txBuffer = ByteArray(CHUNK_SIZE)
        private val rxBuffer = ByteArray(CHUNK_SIZE)
    }

    var txState = DataTransferState.IDLE
    var rxState = DataTransferState.IDLE

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    var receptionProgressValue = 0f

    private var groupOwner = false
    private lateinit var ownerAddress: InetAddress
    var socketStarted = false
    private var client = Socket()
    private var server = ServerSocket()
    // private var fileList = mutableListOf<TxFileDescriptor>()
    var txFilePackDscr = TxFilePackDescriptor()
    var rxFilePackDscr = RxFilePackDescriptor()

    fun shutdown() {
        if (groupOwner) {
            server.close()
        }
        client.close()
    }

    private fun isServerOpened(): Boolean {
        return server.isBound && !server.isClosed
    }
    private fun isClientConnected(): Boolean {
//        Log.d(TAG, "sending message, client.isClosed: " +
//            "${client.isClosed}, client.isConnected: ${client.isConnected}")
        return client.isConnected && !client.isClosed
    }

    suspend fun initiateDataTransmission(filePack: TxFilePackDescriptor) {
        Log.d(TAG, "initiateDataTransmission(), txState = $txState")
        if (txState == DataTransferState.IDLE) {
            txFilePackDscr = filePack.copy()
            txState = DataTransferState.READY_TO_TRANSMIT
            sendTxRequest()
        }
    }

    private suspend fun sendReceptionProgress(rxProgress: String) {
        if (!isClientConnected()) {
            return
        }
        val outputStream =  withContext(Dispatchers.IO) {
            client.getOutputStream()
        }
        Log.d(TAG, "sending reception progress update, rxProgress = '$rxProgress'")

        // Add message type
        writeMessageType(outputStream, MessageType.PROGRESS_RX)
        writeStringUtf8(outputStream, rxProgress)
        outputStream.flush()
    }

    private suspend fun sendCancelRx() {
        sendControlData(MessageType.CANCEL_RX)
    }

    private suspend fun sendReceptionDone() {
        sendControlData(MessageType.RECEPTION_DONE)
    }

    private suspend fun sendTxRequest() {
        sendControlData(MessageType.TX_REQUEST)
    }

    private suspend fun sendAcceptTx() {
        sendControlData(MessageType.ACCEPT_TX)
    }

    private suspend fun sendDismissTx() {
        sendControlData(MessageType.DISMISS_TX)
    }

    private suspend fun sendControlData(type: MessageType) {
        if (!isMessageControl(type)) {
            throw Exception("Message type $type is not a control message type")
        }
        if (!isClientConnected()) {
            return
        }
        val outputStream =  withContext(Dispatchers.IO) {
            client.getOutputStream()
        }
        Log.d(TAG, "sending message type: $type")
        writeMessageType(outputStream, type)
        withContext(Dispatchers.IO) {
            outputStream.flush()
        }
    }

    private suspend fun sendFilePack() {
        if (!isClientConnected() || txFilePackDscr.isEmpty()) {
            return
        }
        val outputStream = withContext(Dispatchers.IO) {
            client.getOutputStream()
        }

        notifier.showProgressDialog("Sending data",
            "Sending data 0.00 %") { dialog, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                notifier.showToast("Data transmission is canceled")
            }
            CoroutineScope(Dispatchers.IO).launch {
                notifier.disconnect()
            }
            txState = DataTransferState.CANCEL_BY_TX
            dialog.dismiss()
        }

        sendFilePackDscr(outputStream)

        run breakLabel@ {
            txFilePackDscr.dscrs.forEach { txFileDscr ->
                val status = sendFile(outputStream, txFileDscr)

                when (status) {
                    DataTransferStatus.DONE -> {
                        Log.d(TAG, "the file '${txFileDscr.fileName}' is sent")
                        notifier.showToast("File '${txFileDscr.fileName}' is sent")
                        txState = DataTransferState.IDLE
                    }

                    DataTransferStatus.CANCELED_BY_TX -> {
                        Log.d(TAG, "File transferring is canceled by transmitter side")
                        notifier.showToast("File transferring is canceled")
                        txState = DataTransferState.IDLE
                        return@breakLabel
                    }

                    DataTransferStatus.CANCELED_BY_RX -> {
                        Log.d(TAG, "File transferring is canceled by receiver side")
                        notifier.dismissProgressDialog()
                        notifier.showToast("File transferring is canceled by receiver")
                        txState = DataTransferState.IDLE
                        return@breakLabel
                    }

                    DataTransferStatus.ERROR -> {
                        Log.d(TAG, "unknown error occurred during data transmission")
                        throw Exception("unknown error occurred during data transmission")
                    }
                }
            }
        }
    }

    private suspend fun sendFilePackDscr(outputStream: OutputStream) {
        // Add message type
        writeMessageType(outputStream, MessageType.FILE_PACK_DSCR)
        writeSize(outputStream, txFilePackDscr.size)
        writeSize(outputStream, txFilePackDscr.dscrs.size)
        Log.d(TAG, "txFilePackDscr.size = ${txFilePackDscr.size}")
        Log.d(TAG, "txFilePackDscr.dscrs.size = ${txFilePackDscr.dscrs.size}")
    }

    private suspend fun sendFile(outputStream: OutputStream, txFileDscr: TxFileDescriptor): DataTransferStatus {
        Log.d(TAG, "sending file: '${txFileDscr.fileName}', file size: ${txFileDscr.fileSize}")
        // Add message type
        writeMessageType(outputStream, MessageType.FILE)
        // Add file metadata
        writeFileMetadata(outputStream, txFileDscr)
        // Add file chunk by chunk
        val status = copyDataToStream(outputStream, txFileDscr)

        withContext(Dispatchers.IO) {
            txFileDscr.inputStream.close()
        }
        return status
    }

    private suspend fun receiveMessage() {
        val inputStream = withContext(Dispatchers.IO) {
            client.getInputStream()
        }

        while (true) {
            when (readMessageType(inputStream)) {
                MessageType.FILE_PACK_DSCR -> {
                    receiveFilePackDscr(inputStream)
                }
                MessageType.FILE -> {
                    receiveFile(inputStream)
                }
                MessageType.PROGRESS_RX -> {
                    receiveProgressRx(inputStream)
                }
                MessageType.CANCEL_RX -> {
                    receiveCancelRx()
                }
                MessageType.RECEPTION_DONE -> {
                    receiveReceptionDone(inputStream)
                }
                MessageType.TX_REQUEST -> {
                    receiveTxRequest(inputStream)
                }
                MessageType.ACCEPT_TX -> {
                    receiveAcceptTx(inputStream)
                }
                MessageType.DISMISS_TX -> {
                    receiveDismissTx(inputStream)
                }
            }
        }
    }

    private fun receiveFilePackDscr(inputStream: InputStream) {
        Log.d(TAG,"receive file pack descripptor, rxState = $rxState")
        if (rxState == DataTransferState.READY_TO_RECEIVE)
        {
            rxState = DataTransferState.ACTIVE
            rxFilePackDscr.clear()
            rxFilePackDscr.sizeTotal = readSize(inputStream)
            rxFilePackDscr.numFiles = readSize(inputStream)
            Log.d(TAG, "rxFilePackDscr.sizeTotal = ${rxFilePackDscr.sizeTotal}")
            Log.d(TAG, "rxFilePackDscr.numFiles = ${rxFilePackDscr.numFiles}")
        }
    }

    private suspend fun receiveFile(inputStream: InputStream) {
        if (rxState != DataTransferState.ACTIVE) {
            throw Exception("File reception on rx state = $rxState")
        }

        val rxFileDscr = RxFileDescriptor()
        rxFileDscr.fileNameReceived = readStringUtf8(inputStream)
        if (rxFileDscr.fileNameReceived.isEmpty()) {
            throw Exception("empty file name")
        }
        Log.d(TAG,"rxFileDscr.fileNameReceived = ${rxFileDscr.fileNameReceived}")
        rxFileDscr.fileNameSaved = FileManager.getSaveFileName(rxFileDscr.fileNameReceived)
        Log.d(TAG,"rxFileDscr.fileNameSaved = ${rxFileDscr.fileNameSaved}")
        rxFileDscr.fileSize = readSize(inputStream)
        Log.d(TAG,"rxFileDscr.fileSize = ${rxFileDscr.fileSize}")

        Log.d(TAG, "receiving file: '${rxFileDscr.fileNameReceived}', file size: ${rxFileDscr.fileSize}")

        notifier.showProgressDialog("Receiving data",
            "Receiving data 0.00%") { dialog, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                sendCancelRx()
            }
            rxState = DataTransferState.CANCEL_BY_RX
            dialog.dismiss()
        }

        val outputStream = FileManager.getOutFileStream(rxFileDscr.fileNameSaved)
        when (copyStreamToData(inputStream, outputStream, rxFileDscr.fileSize)) {
            DataTransferStatus.DONE -> {
                Log.d(TAG, "the file '${{rxFileDscr.fileNameReceived}}' is received")
                Log.d(TAG, "new file '${rxFileDscr.fileNameReceived}' received ands saved as '${rxFileDscr.fileNameSaved}'")
                notifier.showToast("File '${rxFileDscr.fileNameReceived}' received")
                rxFilePackDscr.add(rxFileDscr)

                if (rxFilePackDscr.isReceptionFinished()) {
                    rxState = DataTransferState.IDLE
                    sendReceptionDone()
                }
            }
            DataTransferStatus.CANCELED_BY_TX -> {
                Log.d(TAG, "File transferring '${rxFileDscr.fileNameReceived}' is canceled by transmitter side")
                notifier.showToast("Data transmission is canceled by transmitter")
                FileManager.deleteReceivedFiles(rxFilePackDscr.dscrs)
                FileManager.deleteFile(rxFileDscr.fileNameReceived)
                rxState = DataTransferState.IDLE
            }
            DataTransferStatus.CANCELED_BY_RX -> {
                Log.d(TAG, "File transferring '${rxFileDscr.fileNameReceived}' is canceled by receiver side")
                notifier.showToast("Data transmission is canceled by receiver")
                FileManager.deleteReceivedFiles(rxFilePackDscr.dscrs)
                FileManager.deleteFile(rxFileDscr.fileNameReceived)
                rxState = DataTransferState.IDLE
            }
            DataTransferStatus.ERROR -> {
                Log.d(TAG, "unknown data transfer status error during data reception")
                FileManager.deleteFile(rxFileDscr.fileNameReceived)
                throw Exception("unknown data transfer status error during data reception")
            }
        }

        notifier.dismissProgressDialog()
        withContext(Dispatchers.IO) {
            outputStream.close()
        }
    }

    private suspend fun receiveProgressRx(inputStream: InputStream) {
        val rxProgress = readStringUtf8(inputStream)
        receptionProgressValue = rxProgress.toFloat()
        Log.d(TAG, "receive reception progress update, receptionProgressValue = $receptionProgressValue")
        if (txState == DataTransferState.ACTIVE) {
            notifier.updateProgressDialog("Sending data ${"%.2f".format(receptionProgressValue)} %")
        }
    }

    private suspend fun receiveCancelRx() {
        Log.d(TAG, "Receive cancel rx flag, txState = $txState")
        if (txState == DataTransferState.ACTIVE) {
            txState = DataTransferState.CANCEL_BY_RX
            notifier.showToast("Data transmission is canceled by receiver")
            notifier.disconnect()
        }
    }

    private suspend fun receiveReceptionDone(inputStream: InputStream) {
        Log.d(TAG, "receive reception flag, txState = $txState")
        if (txState == DataTransferState.ACTIVE) {
            notifier.dismissProgressDialog()
            txState = DataTransferState.IDLE
            txFilePackDscr.clear()
            notifier.disconnect()
        }
    }

    private suspend fun receiveTxRequest(inputStream: InputStream) {
        Log.d(TAG, "receive tx request, rxState = $rxState")
        if (rxState == DataTransferState.IDLE) {
            notifier.showAlertDialog(
                "Do you want to receive data from 'transmitter'?",
                { dialog, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        sendDismissTx()
                    }
                    dialog.dismiss()
                },
                { dialog, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        sendAcceptTx()
                    }
                    dialog.dismiss()
                }
            )
            rxState = DataTransferState.READY_TO_RECEIVE
        }
    }

    private suspend fun receiveAcceptTx(inputStream: InputStream) {
        Log.d(TAG, "receive accept tx, txState = $txState")
        if (txState == DataTransferState.READY_TO_TRANSMIT) {
            txState = DataTransferState.ACTIVE
            CoroutineScope(Dispatchers.IO).launch {
                sendFilePack()
            }
        }
    }

    private suspend fun receiveDismissTx(inputStream: InputStream) {
        Log.d(TAG, "receive dismiss tx, txState = $txState")
        if (txState == DataTransferState.READY_TO_TRANSMIT) {
            txState = DataTransferState.IDLE
            txFilePackDscr.clear()
        }
    }

    suspend fun doInBackground(info: WifiP2pInfo, txFilePack: TxFilePackDescriptor) {
        if (socketStarted) {
            return
        }

        socketStarted = true
        txFilePackDscr = txFilePack

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                while (!isClientConnected()) {
                    txState = DataTransferState.IDLE
                    rxState = DataTransferState.IDLE

                    groupOwner = info.isGroupOwner
                    ownerAddress = info.groupOwnerAddress
                    try {
                        if (groupOwner) {
                            if (!isServerOpened()) {
                                server = ServerSocket(PORT_NUMBER)
                                Log.d(TAG, "Server: Socket opened")
                            } else {
                                Log.d(TAG, "Server: Socket is already opened")
                            }
                            client = server.accept()
                            Log.d(TAG, "Server: connection done")
                        } else {
                            while (true) {
                                try {
                                    client = Socket(ownerAddress, PORT_NUMBER)
                                    Log.d(TAG, "Client: connection done")
                                    break
                                } catch (_: Exception) {
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Connection process exception: " + e.message.toString())
                        client.close()
                    }
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                if (isClientConnected()) {
                    if (txFilePackDscr.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            initiateDataTransmission(txFilePackDscr)
                        }
                    }

                    try {
                        receiveMessage()
                    } catch (e: Exception) {
                        Log.e(TAG, "Reception process exception: " + e.message.toString())
                        client.close()
                    }
                }
            }
        }

    }

    private fun readMessageType(inputStream: InputStream) : MessageType {
        val str = readStringUtf8(inputStream)
//        Log.d(TAG, "received message type = $str")
        return MessageType.valueOf(str)
    }

    private fun readSize(inputStream : InputStream) : Int {
        return DataConverter.bytes2value(readData(inputStream, NUM_BYTES_PER_SIZE))
    }

    private fun readStringUtf8(inputStream : InputStream) : String {
        return readData(inputStream, readSize(inputStream)).toString(Charsets.UTF_8)
    }

    private fun readData(inputStream : InputStream, numBytes :Int) : ByteArray {
        if (numBytes < 0) {
            throw Exception("Negative number of read bytes: $numBytes")
        }
        if (numBytes > CHUNK_SIZE) {
            throw Exception("Number of read bytes is greater than CHUNK_SIZE," +
                    "numBytes = $numBytes, CHUNK_SIZE = $CHUNK_SIZE")
        }
//        Log.d(TAG, "start readData(), numBytes = $numBytes")

        var numBytesRead = 0
        while (numBytesRead < numBytes) {
            val num = inputStream.read(rxBuffer, numBytesRead, numBytes - numBytesRead)
//            Log.d(TAG, "num read byte: $num, numBytesRead: $numBytesRead, numBytes: $numBytes")
            if (num <= 0) {
                throw Exception("Something is wrong with the connection...")
            }
            numBytesRead += num
        }

        return if (rxBuffer.size == numBytes) {
            rxBuffer
        } else {
            rxBuffer.slice(0..<numBytes).toByteArray()
        }
    }

    private suspend fun copyDataToStream(outputStream: OutputStream,
                                         txFileDscr: TxFileDescriptor
    ): DataTransferStatus {
        val fileSize = txFileDscr.fileSize
        val inputStream = txFileDscr.inputStream
        var status = DataTransferStatus.DONE
        var numBytesTotal = 0
        try {
            while (numBytesTotal < fileSize) {
                val num = min(fileSize - numBytesTotal, CHUNK_SIZE)
                withContext(Dispatchers.IO) {
                    inputStream.read(txBuffer, 0, num)
                }

                when (txState) {
                    DataTransferState.CANCEL_BY_TX,
                    DataTransferState.CANCEL_BY_RX -> {
                        Log.d(TAG, "cancelling file transmission")
                        writeBytes(outputStream, CANCEL_TX_BYTES)

                        status = if (txState == DataTransferState.CANCEL_BY_TX) {
                            DataTransferStatus.CANCELED_BY_TX
                        } else {
                            DataTransferStatus.CANCELED_BY_RX
                        }
                        break
                    }
                    else -> {
                        writeFileChunk(outputStream, num)
                    }
                }
                withContext(Dispatchers.IO) {
                    outputStream.flush()
                }

                numBytesTotal += num

//                val ratio = (numBytesTotal.toFloat() / fileSize.toFloat()) * 100;
//                notifier.updateProgressDialog("Sending file ${txFileDscr.fileName} ${"%.2f".format(ratio)} %")
//                notifier.updateProgressDialog("Sending file ${txFileDscr.fileName} ${"%.2f".format(receptionProgressValue)} %")
            }

        } catch (e: IOException) {
            Log.d(TAG, e.toString())
            status = DataTransferStatus.ERROR
        }

        return status
    }

    private suspend fun copyStreamToData(inputStream: InputStream, outputStream: OutputStream,
                                 fileSize: Int): DataTransferStatus {
        var status = DataTransferStatus.DONE
        var numBytesTotal = 0
        try {
            while (numBytesTotal < fileSize) {
                val tag = readStringUtf8(inputStream)
                if (tag == CONTINUE_TX_STR) {
                    val num = readSize(inputStream)
                    readData(inputStream, num)
                    withContext(Dispatchers.IO) {
                        outputStream.write(rxBuffer, 0, num)
                    }
                    numBytesTotal += num
                    rxFilePackDscr.sizeReceived += num
                } else if (tag == CANCEL_TX_STR) {
                    Log.d(TAG, "file transmission is canceled")
                    status = if (rxState == DataTransferState.CANCEL_BY_RX) {
                        DataTransferStatus.CANCELED_BY_RX
                    } else {
                        DataTransferStatus.CANCELED_BY_TX
                    }
                    break
                }

                val ratio = rxFilePackDscr.getReceivedPercent()
                sendReceptionProgress(ratio.toString())
                notifier.updateProgressDialog("Receiving data ${"%.2f".format(ratio)} %")
            }
        } catch (e: IOException) {
            Log.d(TAG, e.toString())
            status = DataTransferStatus.ERROR
        }

        return status
    }

    private fun writeFileMetadata(outputStream: OutputStream, txFileDscr: TxFileDescriptor) {
        writeStringUtf8(outputStream, txFileDscr.fileName)
        writeSize(outputStream, txFileDscr.fileSize)
    }

    private fun writeMessageType(outputStream: OutputStream, type: MessageType) {
        val typeStr = type.toString()
        writeStringUtf8(outputStream, typeStr)
    }

    private fun writeSize(outputStream: OutputStream, value: Int) {
         outputStream.write(DataConverter.value2bytes(value, NUM_BYTES_PER_SIZE))
    }

    private fun writeStringUtf8(outputStream: OutputStream, string: String) {
        val strUtf8 = DataConverter.string2Utf8((string))
        writeSize(outputStream, strUtf8.size)
        outputStream.write(strUtf8)
    }

    private fun writeBytes(outputStream: OutputStream, bytes: ByteArray) {
        writeBytes(outputStream, bytes, bytes.size)
    }

    private fun writeBytes(outputStream: OutputStream, bytes: ByteArray, numBytes: Int) {
        writeSize(outputStream, numBytes)
        outputStream.write(bytes)
    }

    private fun writeFileChunk(outputStream: OutputStream, numBytes: Int) {
        writeBytes(outputStream, CONTINUE_TX_BYTES)
        writeSize(outputStream, numBytes)
        outputStream.write(txBuffer, 0, numBytes)
        outputStream.flush()
    }
}