package com.example.droid_share.data

import android.annotation.SuppressLint
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.droid_share.NotificationInterface
import com.example.droid_share.TxFilePackDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NsdDataTransceiver(
    private var notifier : NotificationInterface
) : BaseDataTransceiver()
{
    companion object {
        private const val TAG = "WifiDataTransceiver"
        private const val CLIENT_CONNECTION_TIMEOUT_MS = 30000
    }

    private var tcpP2pConnector: TcpP2pConnector? = null

    init {
        dataTransceiver = DataTransceiver(notifier)
        tcpP2pConnector = TcpP2pConnector()
    }

    fun isConnectionEstablished(): Boolean {
        Log.d(TAG, "isConnectionEstablished(), isJobActive(rxJob) = ${isJobActive(rxJob)}")
        return isJobActive(rxJob)
    }

    fun cancelDataTransmission() {
        Log.d(TAG, "cancelDataTransmission")
        dataTransceiver?.cancelDataTransmission()
    }

    suspend fun createSocket(serverFlag: Boolean, info: NsdServiceInfo) {
        createSocket(serverFlag, info, TxFilePackDescriptor())
    }
    @SuppressLint("NewApi")
    suspend fun createSocket(serverFlag: Boolean, info: NsdServiceInfo, txFilePack: TxFilePackDescriptor ) {
        this.txFilePack = txFilePack.copy()

        if (!tcpP2pConnector!!.isSocketCreated()) {
            if (isJobActive(socketJob)) {
                Log.d(TAG, "socketJob is active, skip createSocket function")
                return
            }

            socketJob = CoroutineScope(Dispatchers.IO).launch {
                if (serverFlag) {
                    Log.d(TAG, "createSocket(), run createServer, info.port = ${info.port}")
                    tcpP2pConnector!!.createServer(info.port);
                } else {
                    val address = info.getHost()
                    Log.d(TAG, "createClient(), run createServer, address = $address, info.port = ${info.port}")
                    tcpP2pConnector!!.createClient(address, info.port, CLIENT_CONNECTION_TIMEOUT_MS);
                }
            }
            socketJob!!.join()
            Log.d(TAG, "socketJob!!.join()")
            Log.d(TAG, "nsdClientServer!!.isClientConnected() = ${tcpP2pConnector!!.isClientConnected()}")


            if (tcpP2pConnector!!.isClientConnected()) {
                Log.d(TAG, "wifiClientServer!!.isSocketCreated() = true")

                dataTransceiver!!.setStreams(
                    tcpP2pConnector!!.getInputStream(),
                    tcpP2pConnector!!.getOutputStream()
                )
                CoroutineScope(Dispatchers.IO).launch {
                    dataTransceiver!!.transmissionFlow(txFilePack)
                }
                rxJob = CoroutineScope(Dispatchers.IO).launch {
                    dataTransceiver!!.receptionFlow()
                }
            }
        } else {
            Log.d(TAG, "socket is not created")
        }
    }

    fun destroySocket() {
        Log.d(TAG, "NsdDataTransceiver, destroySocket(), run stopActiveJobs()")
        stopActiveJobs()
        Log.d(TAG, "NsdDataTransceiver, destroySocket(), run dataTransceiver!!.shutdown()")
        dataTransceiver!!.shutdown()
        Log.d(TAG, "NsdDataTransceiver, destroySocket(), run tcpP2pConnector!!.shutdown()")
        tcpP2pConnector!!.shutdown()

        dataTransceiver = DataTransceiver(notifier)
        tcpP2pConnector = TcpP2pConnector()
    }
}