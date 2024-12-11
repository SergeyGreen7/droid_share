package com.example.droid_share.connection

import android.net.wifi.p2p.WifiP2pManager.BUSY
import android.net.wifi.p2p.WifiP2pManager.ERROR
import android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS
import android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED

class WifiUtils {
    companion object {
        fun getErrorCodeDescription(code: Int): String {
            when (code) {
                P2P_UNSUPPORTED -> return "operation failed because p2p is unsupported on the device"
                ERROR -> return "operation failed due to an internal error"
                BUSY -> return "operation failed because the framework is busy and unable to service the request"
                NO_SERVICE_REQUESTS -> return "the discoverServices failed because no service requests are added"
            }
            return "Error code description is not found"
        }
    }
}