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
) : BaseDataTransceiver()
{

    companion object {
        private const val TAG = "BluetoothDataTransceiver"
    }

    private var bltClientServer: BluetoothClientServer? = null

    var bluetoothController: BluetoothController? = null

    init {
        dataTransceiver = DataTransceiver(notifier)
        bltClientServer = BluetoothClientServer()
    }

    fun isConnectionEstablished(): Boolean {
        Log.d(TAG, "isConnectionEstablished(), isJobActive(fileJob) = ${isJobActive(rxJob)}")
        return isJobActive(rxJob)
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
            rxJob = CoroutineScope(Dispatchers.IO).launch {
                dataTransceiver!!.receptionFlow()
            }
            Log.d(TAG, "startServer(), isJobActive(fileJob) = ${isJobActive(rxJob)}")
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
            rxJob = CoroutineScope(Dispatchers.IO).launch {
                dataTransceiver!!.receptionFlow()
            }
            Log.d(TAG, "startClient(), isJobActive(fileJob) = ${isJobActive(rxJob)}")
        }
    }

    fun destroySocket() {
        CoroutineScope(Dispatchers.IO).launch {
            stopActiveJobs()
            dataTransceiver!!.shutdown()
            bltClientServer!!.shutdown()
        }
    }
}