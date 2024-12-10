package com.example.droid_share.connection

import android.annotation.SuppressLint
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.text.TextUtils
import android.util.Log
import com.example.droid_share.grid.DeviceInfo
import com.example.droid_share.grid.GridUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

class NsdController (
    private val manager: NsdManager,
    private val gridUpdater: GridUpdater
)
{

    companion object {
        private const val TAG = "NsdController"
        private const val SERVICE_TYPE = "_wifinsd._tcp."
        private val SERVICE_PORT = 8890
        private val NSD_PROTOCOL = NsdManager.PROTOCOL_DNS_SD
    }

    private var services = HashMap<String, NsdServiceInfo>()

    // private var serviceList = mutableListOf<NsdServiceInfo>()
    private var serviceInfo: NsdServiceInfo = NsdServiceInfo()

    init {
        serviceInfo.serviceName = "NSD_AQUARIUS"
        serviceInfo.serviceType = SERVICE_TYPE
    }
    fun getServiceInfo(): NsdServiceInfo {
        return serviceInfo
    }

    private val resolveListener = object : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Log.d(TAG, "Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Resolve Succeeded. $serviceInfo")
            services[serviceInfo.serviceType] = serviceInfo

            CoroutineScope(Dispatchers.Main).launch{
                gridUpdater.onDeviceListUpdate(services.map{ DeviceInfo(it.value) })
            }

        }
    }

    @SuppressLint("NewApi")
    fun registerNsdService() {
        Log.d(TAG, "start of registerNsdService()")

//        val wlanInetAddress = getInetAddress("wlan")
//        val p2pInetAddress = getInetAddress("p2p")

//        if (wlanInetAddress != null) {
//            serviceInfo.setAttribute("WLAN_HOST", wlanInetAddress.hostAddress)
//        }
//        if (p2pInetAddress != null) {
//            serviceInfo.setAttribute("P2P_HOST", p2pInetAddress.hostAddress)
//        }
//        if (p2pInetAddress != null) {
//            serviceInfo.setHostAddresses(listOf(p2pInetAddress))
//        } else if (wlanInetAddress != null)  {
//            serviceInfo.setHostAddresses(listOf(wlanInetAddress))
//        }
        serviceInfo.port = SERVICE_PORT

        manager.registerService(serviceInfo, NSD_PROTOCOL, object: NsdManager.RegistrationListener{
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

        })
    }

    fun discoveryNsdService() {
        Log.d(TAG, "start of discoveryNsdService()")
        manager.discoverServices(SERVICE_TYPE, NSD_PROTOCOL, object: NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.d(TAG, "discoverServices(), onStartDiscoveryFailed")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.d(TAG, "discoverServices(), onStopDiscoveryFailed")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "discoverServices(), onDiscoveryStarted")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "discoverServices(), onDiscoveryStopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "discoverServices(), onServiceFound")
                if (serviceInfo != null) {
                    Log.d(TAG, "serviceInfo: $serviceInfo")
                }

                manager.resolveService(serviceInfo, resolveListener)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "discoverServices(), onServiceLost")
            }

        })
    }

    private fun getInetAddress(networkInterface: String): InetAddress? {
        val inetAddressResult: InetAddress? = null
        try {
            val networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces()
            val networkInterfaceList = networkInterfaceEnumeration.toList()
            for (item in networkInterfaceList) {
                if (item.name.contains(networkInterface)) {
                    val inetAddress = getInetAddress(item)
                    if (inetAddress != null && TextUtils.isEmpty(inetAddress.hostName)) {
                        Log.d(TAG, "NetworkInterface name: ${item.name}")
                        Log.d(TAG, "NetworkInterface HostAddres: ${inetAddress.hostAddress}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Exception in getInetAddress, $e")
        }
        return inetAddressResult
    }

    private fun getInetAddress(networkInterface: NetworkInterface): InetAddress? {
        var inetAddess: InetAddress? = null
        val addresses = networkInterface.inetAddresses
        var address4: Inet4Address? = null
        var address6: Inet6Address? = null
        while (addresses.hasMoreElements()) {
            val addr = addresses.nextElement()
            if (address6 == null && addr is Inet6Address) {
                try {
                    address6 = Inet6Address.getByAddress(null, addr.address) as Inet6Address
                } catch (_: Exception) {
                }
            } else if (address4 == null && addr is Inet4Address) {
                address4 = addr

            }
        }

        if (address4 != null) {
            inetAddess = address4
        } else if (address6 != null) {
            inetAddess = address6
        }
        return inetAddess
    }
}



