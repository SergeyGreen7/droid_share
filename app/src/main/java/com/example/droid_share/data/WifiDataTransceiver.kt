package com.example.droid_share.data

import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import com.example.droid_share.NotificationInterface
import com.example.droid_share.TxFilePackDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

class WifiDataTransceiver(
    private var notifier : NotificationInterface
) {
    companion object {
        private const val TAG = "WifiDataTransceiver"
    }

    private var socketJob: Job? = null
    private var fileJob: Job? = null

    private var dataTransceiver: DataTransceiver? = null
    private var wifiClientServer: WifiClientServer? = null

    init {
        dataTransceiver = DataTransceiver(notifier)
        wifiClientServer = WifiClientServer()
    }

    fun isConnectionEstablished(): Boolean {
        return isJobActive(fileJob)
    }

    suspend fun createSocket(info: WifiP2pInfo, txFilePack: TxFilePackDescriptor) {
        if (!wifiClientServer!!.isSocketCreated()) {
            if (socketJob != null && socketJob!!.isActive) {
                return
            }
            socketJob = CoroutineScope(Dispatchers.IO).launch {
                wifiClientServer!!.doInBackground(info)
            }
            socketJob!!.join()
            Log.d(TAG, "socketJob!!.join()")

            if (wifiClientServer!!.isClientConnected()) {
                Log.d(TAG, "wifiClientServer!!.isSocketCreated() = true")

                dataTransceiver!!.setStreams(
                    wifiClientServer!!.getInputStream(),
                    wifiClientServer!!.getOutputStream()
                )
                fileJob = CoroutineScope(Dispatchers.IO).launch {
                    dataTransceiver!!.doInBackground(txFilePack)
                }
            }
        }
    }

    fun destroySocket() {
        if (isJobActive(socketJob)) {
            socketJob!!.cancel("Software stop file socket creation job")
        }
        if (isJobActive(fileJob)) {
            fileJob!!.cancel("Software stop file reception job")
        }

        if (wifiClientServer!!.isSocketCreated()) {
            dataTransceiver!!.shutdown()
            wifiClientServer!!.shutdown()
        }
    }

    private fun isJobActive(job: Job?) : Boolean {
        return (job != null) && job.isActive
    }

    suspend fun sendData(filePack: TxFilePackDescriptor) {
        dataTransceiver!!.initiateDataTransmission(filePack)
    }
}