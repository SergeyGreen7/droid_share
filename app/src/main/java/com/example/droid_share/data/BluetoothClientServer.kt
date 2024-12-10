package com.example.droid_share.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
import com.example.droid_share.data.BluetoothDataTransceiver.Companion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class BluetoothClientServer {

    companion object {
        private const val TAG = "BluetoothClientServer"
        const val PORT_NUMBER = 8889
        private val CLIENT_CONNECTION_TIMEOUT_MS = 30000

        private const val BLUETOOTH_SERVER_NAME = "ns220re_bluetooth_server"
    }
    private var client: BluetoothSocket? = null
    private var server: BluetoothServerSocket? = null

    fun shutdown() {
        if (server != null) {
            try {
                server?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Could not close the server socket, $e")
            }
        }
        if (client != null) {
            try {
                client!!.close()
            } catch (e: Exception) {
                Log.e(TAG, "Could not close the client socket, $e")
            }
        }
    }

    fun getInputStream(): InputStream {
        return client!!.inputStream
    }

    fun getOutputStream(): OutputStream {
        return client!!.outputStream
    }

    fun isClientConnected(): Boolean {
        return client != null && client!!.isConnected
    }

    fun runServer(bluetoothServerSocket: BluetoothServerSocket) {
        server = bluetoothServerSocket
        try {
            client = server?.accept()
            Log.d(TAG,"bluetooth server connection done")
        } catch (e: Exception) {
            Log.e(TAG, "Server socket's accept() method failed, $e")
        }
    }

    @SuppressLint("MissingPermission")
    fun runClient(bluetoothSocket: BluetoothSocket) {
        try {
            client = bluetoothSocket
            Log.d(TAG,"run connect")
            client!!.connect()
            Log.d(TAG,"bluetooth client connection done")
        } catch (e: Exception) {
            Log.e(TAG, "Client socket's connect method failed, $e")
        }
    }

}