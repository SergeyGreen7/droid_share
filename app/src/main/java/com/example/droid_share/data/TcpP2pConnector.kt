package com.example.droid_share.data

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

open class TcpP2pConnector {

    companion object {
        private const val TAG = "BaserClientServer"
    }

    protected var client: Socket? = null
    protected var server: ServerSocket? = null

    // protected var clientJob: Job? = null
    // protected var serverJob: Job? = null

    fun shutdown() {
        try {
            server?.close()
            Log.i(TAG, "Server socket is closed")
        } catch (e: Exception) {
            Log.e(TAG, "Could not close the server socket, $e")
        }
        try {
            client?.close()
            Log.i(TAG, "Client socket is closed")
        } catch (e: Exception) {
            Log.e(TAG, "Could not close the client socket, $e")
        }

        Log.d(TAG, "TcpP2pConnector, shutdown(), isSocketCreated() = ${isSocketCreated()}")
    }

    fun isSocketCreated(): Boolean {
        Log.d(TAG, "TcpP2pConnector, isSocketCreated():")
        Log.d(TAG, "    server == null = ${server == null}")
        if (server != null) {
            Log.d(TAG, "    server!!.isBound = ${server!!.isBound}")
            Log.d(TAG, "    !server!!.isClosed = ${!server!!.isClosed}")
        }
        Log.d(TAG, "    client == null = ${client == null}")
        if (client != null) {
            Log.d(TAG, "    client!!.isConnected = ${client!!.isConnected}")
            Log.d(TAG, "    !client!!.isClosed = ${!client!!.isClosed}")
        }

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

    fun isConnectionEstablished(): Boolean {
        return if (server != null) {
            isServerOpened() && isClientConnected()
        } else {
            isClientConnected()
        }
    }

    protected fun isJobActive(job: Job?) : Boolean {
        return (job != null) && job.isActive
    }

    fun createClient(address: InetAddress, port: Int, timeout: Int) {
//        if (isJobActive(clientJob)) {
//            return
//        }

//        clientJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                client = Socket()
                client?.connect(InetSocketAddress(address, port), timeout)
                Log.d(TAG, "createClient, client = $client")
                Log.d(TAG, "Client: connection to server is done")
            } catch (e: Exception) {
                Log.d(TAG, "Exception during connection to the server: $e")
            }
//        }
    }

    fun createServer(port: Int) {
//        if (isJobActive(serverJob)) {
//            return
//        }

//        serverJob = CoroutineScope(Dispatchers.IO).launch {
            if (!isServerOpened()) {
                server = ServerSocket(port)
                Log.d(TAG, "Server: socket created")
            } else {
                Log.d(TAG, "Server: Socket is already opened")
            }
            try {
                Log.d(TAG, "Start server.accept()...")
                client = server?.accept()
                Log.d(TAG, "Server: accept done")
                Log.d(TAG, "createServer(), client = $client")
            } catch (e: Exception) {
                Log.d(TAG, "TcpP2pConnector, createServer, exception happened: $e")
            }
//        }
    }
}