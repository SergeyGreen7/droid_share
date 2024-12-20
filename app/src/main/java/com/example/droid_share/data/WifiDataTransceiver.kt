package com.example.droid_share.data

import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import com.example.droid_share.NotificationInterface
import com.example.droid_share.TxFilePackDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WifiDataTransceiver(
    private var notifier : NotificationInterface
) : BaseDataTransceiver()
{
    companion object {
        private const val TAG = "WifiDataTransceiver"
        private const val PORT_NUMBER = 8888
        private val CLIENT_CONNECTION_TIMEOUT_MS = 30000
    }

    private var wifiClientServer: TcpP2pConnector? = null

    init {
        dataTransceiver = DataTransceiver(notifier)
        wifiClientServer = TcpP2pConnector()
    }

    fun isConnectionEstablished(): Boolean {
        return wifiClientServer?.isConnectionEstablished() ?: false
    }

    suspend fun createSocket(info: WifiP2pInfo, txFilePack: TxFilePackDescriptor) {
        Log.d(TAG, "WifiDataTransceiver, start createSocket(), " +
                "!wifiClientServer!!.isSocketCreated() = ${!wifiClientServer!!.isSocketCreated()}")
        this.txFilePack = txFilePack.copy()

        if (!isJobActive(rxJob)) {

            socketJob = CoroutineScope(Dispatchers.IO).launch {
                if (info.isGroupOwner) {
                    wifiClientServer!!.createServer(PORT_NUMBER)
                } else {
                    wifiClientServer!!.createClient(info.groupOwnerAddress,
                        PORT_NUMBER, CLIENT_CONNECTION_TIMEOUT_MS)
                }
            }
            socketJob!!.join()
            Log.d(TAG, "socketJob!!.join()")

            if (wifiClientServer!!.isClientConnected()) {
                Log.d(TAG, "wifiClientServer!!.isSocketCreated() = true")

                dataTransceiver!!.setStreams(
                    wifiClientServer!!.getInputStream(),
                    wifiClientServer!!.getOutputStream()
                )
                txJob = CoroutineScope(Dispatchers.IO).launch {
                    dataTransceiver!!.transmissionFlow(txFilePack)
                }
                rxJob = CoroutineScope(Dispatchers.IO).launch {
                    dataTransceiver!!.receptionFlow()
                }
            }
        }
    }

    fun destroySocket() {
        CoroutineScope(Dispatchers.IO).launch {
            stopActiveJobs()
            dataTransceiver!!.shutdown()
            wifiClientServer!!.shutdown()
        }
    }

}