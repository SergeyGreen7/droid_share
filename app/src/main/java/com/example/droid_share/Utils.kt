package com.example.droid_share

import android.text.TextUtils
import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

class Utils {
    companion object {

        private const val TAG = "Utils"

        fun getInetAddress(networkInterface: String): InetAddress? {
            val inetAddressResult: InetAddress? = null
            try {
                val networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces()
                val networkInterfaceList = networkInterfaceEnumeration.toList()
                for (item in networkInterfaceList) {
                    if (item.name.contains(networkInterface)) {
                        val inetAddress = getInetAddress(item)
                        if (inetAddress != null && TextUtils.isEmpty(inetAddress.hostName)) {
                            Log.d(TAG, "NetworkInterface name: ${item.name}")
                            Log.d(TAG, "NetworkInterface HostAddress: ${inetAddress.hostAddress}")
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
}