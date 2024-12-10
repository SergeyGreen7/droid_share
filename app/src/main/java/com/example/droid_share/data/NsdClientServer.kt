package com.example.droid_share.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.droid_share.data.WifiClientServer.Companion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class NsdClientServer {

    companion object {
        private const val TAG = "BluetoothClientServer"
        const val PORT_NUMBER = 8889
        private val CLIENT_CONNECTION_TIMEOUT_MS = 30000

        private const val BLUETOOTH_SERVER_NAME = "ns220re_bluetooth_server"
    }
    private var client: Socket? = null
    private var server: ServerSocket? = null

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

    fun isSocketCreated(): Boolean {
        return isServerOpened() || isClientConnected()
    }

    fun getInputStream(): InputStream {
        return client!!.getInputStream()
    }

    fun getOutputStream(): OutputStream {
        return client!!.getOutputStream()
    }

    fun isServerOpened(): Boolean {
        return server != null && server!!.isBound && !server!!.isClosed
    }

    fun isClientConnected(): Boolean {
        return client != null && client!!.isConnected && !client!!.isClosed
    }

    fun getStreams() : Pair<InputStream, OutputStream>? {
        if (isClientConnected()) {
            return Pair(client!!.getInputStream(), client!!.getOutputStream())
        }
        return null
    }

    suspend fun createClient(address: InetAddress, port: Int) {
        try {
            client = Socket()
            withContext(Dispatchers.IO) {
                client?.connect(
                    InetSocketAddress(address, port), CLIENT_CONNECTION_TIMEOUT_MS)
            }
            Log.d(TAG, "Client: connection to server is done")
        } catch (e: Exception) {
            Log.d(TAG, "Exception during connection to the server: $e")
        }
    }

    suspend fun createServer(port: Int) {
        withContext(Dispatchers.IO) {
            if (!isServerOpened()) {
                server = ServerSocket(port)
                Log.d(TAG, "Server: socket created")
            } else {
                Log.d(TAG, "Server: Socket is already opened")
            }
            client = server?.accept()
            Log.d(TAG, "Server: accept done")
        }
    }

}