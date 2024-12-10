package com.example.droid_share.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.util.Log
import com.example.droid_share.NotificationInterface
import com.example.droid_share.TxFilePackDescriptor
import com.example.droid_share.connection.BluetoothController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class BluetoothDataTransceiver(
    private var notifier : NotificationInterface
) {

    companion object {
        private const val TAG = "BluetoothDataTransceiver"
    }

    private var socketJob: Job? = null
    private var fileJob: Job? = null

    private var dataTransceiver: DataTransceiver? = null
    private var bltClientServer: BluetoothClientServer? = null

    var bluetoothController: BluetoothController? = null

    init {
        dataTransceiver = DataTransceiver(notifier)
        bltClientServer = BluetoothClientServer()
    }

    fun isConnectionEstablished(): Boolean {
        Log.d(TAG, "isConnectionEstablished(), isJobActive(fileJob) = ${isJobActive(fileJob)}")
        return isJobActive(fileJob)
    }

    suspend fun startServer(server: BluetoothServerSocket) {
        socketJob = CoroutineScope(Dispatchers.IO).launch {
            bltClientServer!!.runServer(server)
        }
        socketJob!!.join()
        if (bltClientServer!!.isClientConnected()) {
            Log.d(TAG, "wifiClientServer!!.isSocketCreated() = true")

            dataTransceiver!!.setStreams(
                bltClientServer!!.getInputStream(),
                bltClientServer!!.getOutputStream()
            )
            fileJob = CoroutineScope(Dispatchers.IO).launch {
                dataTransceiver!!.doInBackground(TxFilePackDescriptor())
            }
            Log.d(TAG, "startServer(), isJobActive(fileJob) = ${isJobActive(fileJob)}")
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun startClient(device: BluetoothDevice, uuid: UUID) {
        Log.d(TAG,"run createRfcommSocketToServiceRecord")
        val client = device.createRfcommSocketToServiceRecord(uuid)

        socketJob = CoroutineScope(Dispatchers.IO).launch {
            bltClientServer!!.runClient(client)
        }
        socketJob!!.join()

        if (bltClientServer!!.isClientConnected()) {
            Log.d(TAG, "wifiClientServer!!.isSocketCreated() = true")

            dataTransceiver!!.setStreams(
                bltClientServer!!.getInputStream(),
                bltClientServer!!.getOutputStream()
            )
            fileJob = CoroutineScope(Dispatchers.IO).launch {
                dataTransceiver!!.doInBackground(TxFilePackDescriptor())
            }
            Log.d(TAG, "startClient(), isJobActive(fileJob) = ${isJobActive(fileJob)}")
        }
    }

    fun destroySocket() {
        if (isJobActive(socketJob)) {
            Log.d(TAG, "run socketJob.cancel()")
            socketJob!!.cancel("Software stop file reception job")
        }
        if (isJobActive(fileJob)) {
            Log.d(TAG, "run fileJob.cancel()")
            fileJob!!.cancel("Software stop file reception job")
        }

        if (bltClientServer!!.isClientConnected()) {
            dataTransceiver!!.shutdown()
            bltClientServer!!.shutdown()
        }
    }

    private fun isJobActive(job: Job?) : Boolean {
        return (job != null) && job.isActive
    }

    suspend fun sendData(filePack: TxFilePackDescriptor) {
        dataTransceiver!!.initiateDataTransmission(filePack)
    }

}