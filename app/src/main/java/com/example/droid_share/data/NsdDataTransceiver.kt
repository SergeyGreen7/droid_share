package com.example.droid_share.data

import android.annotation.SuppressLint
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresExtension
import com.example.droid_share.NotificationInterface
import com.example.droid_share.TxFilePackDescriptor
import com.example.droid_share.data.BluetoothDataTransceiver.Companion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.InetAddress

class NsdDataTransceiver(
    private var notifier : NotificationInterface
) {
    companion object {
        private const val TAG = "WifiDataTransceiver"
    }

    private var socketJob: Job? = null
    private var rxJob: Job? = null

    private var dataTransceiver: DataTransceiver? = null
    private var nsdClientServer: NsdClientServer? = null

    init {
        dataTransceiver = DataTransceiver(notifier)
        nsdClientServer = NsdClientServer()
    }

    fun isConnectionEstablished(): Boolean {
        Log.d(TAG, "isConnectionEstablished(), isJobActive(rxJob) = ${isJobActive(rxJob)}")
        return isJobActive(rxJob)
    }

    @SuppressLint("NewApi")
    suspend fun createSocket(serverFlag: Boolean, info: NsdServiceInfo, txFilePack: TxFilePackDescriptor ) {
        if (!nsdClientServer!!.isSocketCreated()) {
            if (socketJob != null && socketJob!!.isActive) {
                return
            }
            socketJob = CoroutineScope(Dispatchers.IO).launch {
                if (serverFlag) {
                    nsdClientServer!!.createServer(info.port);
                } else {
                    val address = info.getHost()
                    nsdClientServer!!.createClient(address, info.port);
                }
            }
            socketJob!!.join()
            Log.d(TAG, "socketJob!!.join()")

            if (nsdClientServer!!.isClientConnected()) {
                Log.d(TAG, "wifiClientServer!!.isSocketCreated() = true")

                dataTransceiver!!.setStreams(
                    nsdClientServer!!.getInputStream(),
                    nsdClientServer!!.getOutputStream()
                )
                rxJob = CoroutineScope(Dispatchers.IO).launch {
                    dataTransceiver!!.doInBackground(txFilePack)
                }
            }
        }
    }

    fun destroySocket() {
        if (isJobActive(socketJob)) {
            socketJob!!.cancel("Software stop file socket creation job")
        }
        if (isJobActive(rxJob)) {
            rxJob!!.cancel("Software stop file reception job")
        }

        if (nsdClientServer!!.isSocketCreated()) {
            dataTransceiver!!.shutdown()
            nsdClientServer!!.shutdown()
        }
    }

    private fun isJobActive(job: Job?): Boolean {
        return (job != null) && job.isActive
    }

    suspend fun sendData(filePack: TxFilePackDescriptor) {
        dataTransceiver!!.initiateDataTransmission(filePack)
    }
}