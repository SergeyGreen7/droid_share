package com.example.droid_share.data

import android.content.Context
import android.net.wifi.p2p.WifiP2pInfo
import android.util.Log
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

class WifiClientServer {

    companion object {
        private const val TAG = "WifiClientServer"
        const val PORT_NUMBER = 8888
        private val CLIENT_CONNECTION_TIMEOUT_MS = 30000
    }
    private var groupOwner = false
    private lateinit var ownerAddress: InetAddress
    private var client: Socket? = null
    private var server: ServerSocket? = null

    private var serverJob: Job? = null
    private var clientJob: Job? = null

    fun shutdown() {
        if (groupOwner) {
            server?.close()
        }
        client?.close()
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

    suspend fun doInBackground(info: WifiP2pInfo) {

        groupOwner = info.isGroupOwner
        ownerAddress = info.groupOwnerAddress

        if (groupOwner) {
           createServer()
        } else {
            createClient()
        }
    }

    private suspend fun createClient() {
        try {
            client = Socket()
            withContext(Dispatchers.IO) {
                client?.connect(InetSocketAddress(ownerAddress, PORT_NUMBER),
                    CLIENT_CONNECTION_TIMEOUT_MS
                )
            }
            Log.d(TAG, "Client: connection to server is done")
        } catch (e: Exception) {
            Log.d(TAG, "Exception during connection to the server: $e")
        }
    }

    private suspend fun createServer() {
        withContext(Dispatchers.IO) {
            if (!isServerOpened()) {
                server = ServerSocket(PORT_NUMBER)
                Log.d(TAG, "Server: socket created")
            } else {
                Log.d(TAG, "Server: Socket is already opened")
            }
            client = server?.accept()
            Log.d(TAG, "Server: accept done")
        }
    }

}