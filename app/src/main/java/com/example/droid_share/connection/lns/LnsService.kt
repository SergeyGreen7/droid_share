package com.example.droid_share.connection

import android.annotation.SuppressLint
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.text.TextUtils
import android.util.Log
import com.example.droid_share.NotificationInterface
import com.example.droid_share.grid.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executor

class LnsService (
    private val manager: NsdManager,
)
{
    companion object {
        const val SERVICE_NAME = "NSD_AQUARIUS"
        const val SERVICE_TYPE = "_wifi_lns_fs._tcp."
        const val NSD_PROTOCOL = NsdManager.PROTOCOL_DNS_SD

        private const val TAG = "LnsService"
        private const val SERVICE_PORT = 8889
    }

    // private var serviceList = mutableListOf<NsdServiceInfo>()
    private var serviceRegistered = false

    var serviceInfo: NsdServiceInfo = NsdServiceInfo()
    var serviceName = ""

    init {
        serviceInfo.serviceName = "LNS_AQUARIUS-" + (Math.random() * 1000).toInt().toString()

        serviceInfo.serviceType = SERVICE_TYPE
        serviceInfo.port        = SERVICE_PORT
    }

    private val registrationListener = object: NsdManager.RegistrationListener {
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.d(TAG, "registerService(), onRegistrationFailed")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.d(TAG, "registerService(), onUnregistrationFailed")
        }

        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
            Log.d(TAG, "registerService(), onServiceRegistered")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
            Log.d(TAG, "registerService(), onServiceUnregistered")
        }
    }

    fun registerService() {
        if (serviceName.isNotEmpty()) {
            serviceInfo.serviceName = serviceName
        }
        serviceRegistered = true
        manager.registerService(serviceInfo, NSD_PROTOCOL, registrationListener)
    }

    fun unregisterService() {
        Log.d(TAG, "start unregisterService()")
        if (serviceRegistered) {
            Log.d(TAG, "    do unregisterService()")
            manager.unregisterService(registrationListener)
            serviceRegistered = false
        }
    }
}



