package com.example.droid_share.connection

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.droid_share.NotificationInterface

class LnsController (
    manager: NsdManager,
    notifier: NotificationInterface
)
{
    companion object {
        private const val TAG = "NsdController"
    }

    private val service = LnsService(manager)
    private val scanner = LnsScanner(manager, notifier)

    fun getServiceInfo(): NsdServiceInfo {
        return service.serviceInfo
    }

    fun registerLocalNetworkService() {
        Log.d(TAG, "LncController, registerLocalNetworkService()")
        service.registerService()
    }

    fun unregisterLocalNetworkService() {
        Log.d(TAG, "LncController, unregisterLocalNetworkService()")
        service.unregisterService()
    }

    fun startDiscoverLocalNetworkServices() {
        Log.d(TAG, "LncController, startDiscoverLocalNetworkServices()")
        scanner.startScan()
    }

    fun stopDiscoverLocalNetworkServices() {
        Log.d(TAG, "LncController, stopDiscoverLocalNetworkServices()")
        scanner.stopScan()
    }
}



