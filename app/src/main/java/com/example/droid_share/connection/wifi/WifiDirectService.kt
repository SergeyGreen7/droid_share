package com.example.droid_share.connection

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.util.Log

class WifiDirectService(
    private val manager: WifiP2pManager,
    private val channel: Channel,
) {

    companion object {
        private const val TAG = "WifiDirectService"
        const val SERVICE_TYPE = "_wifi_p2p_test._tcp"
    }

    private val localServiceListener = object: ActionListener {
        override fun onSuccess() {
            // Command successful! Code isn't necessarily needed here,
            // Unless you want to update the UI or add logging statements.
            Log.d(TAG, "addLocalService(), onSuccess")
        }
        override fun onFailure(code: Int) {
            Log.d(TAG, "addLocalService(), onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
        }
    }

    @SuppressLint("MissingPermission")
    fun registerP2pService() {
        manager.discoverPeers(channel, object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "WifiDirectService, discoverPeers, onSuccess")
            }
            override fun onFailure(code: Int) {
                Log.d(TAG, "WifiDirectService, discoverPeers, onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
            }
        })

        Log.d(TAG, "WifiDirectService, registerP2pService()")
        val record = HashMap<String, String>();

//        val interfaces: List<NetworkInterface> =
//            Collections.list(NetworkInterface.getNetworkInterfaces())

        // record["buddyname"] = "NS220RE-${(Math.random() * 1000000).toInt()}"
        record["available"] = "visible"

        val instanceName = "NS220RE-${(Math.random() * 1000000).toInt()}"

        val serviceInfo = WifiP2pDnsSdServiceInfo
            .newInstance(instanceName, SERVICE_TYPE, record)

        manager.addLocalService(channel, serviceInfo, localServiceListener)
    }

    fun unregisterP2pService() {
        Log.d(TAG, "WifiDirectService, unregisterP2pService()")
        manager.stopPeerDiscovery(channel, object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "WifiDirectService, stopPeerDiscovery, onSuccess")
            }
            override fun onFailure(code: Int) {
                Log.d(TAG, "WifiDirectService, stopPeerDiscovery, onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
            }
        })

        manager.clearLocalServices(channel, object: ActionListener {

            override fun onSuccess() {
                // Command successful! Code isn't necessarily needed here,
                // Unless you want to update the UI or add logging statements.
                Log.d(TAG, "clearLocalServices(), onSuccess")
            }

            override fun onFailure(code: Int) {
                Log.d(TAG, "clearLocalServices(), onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
            }
        })
    }
}