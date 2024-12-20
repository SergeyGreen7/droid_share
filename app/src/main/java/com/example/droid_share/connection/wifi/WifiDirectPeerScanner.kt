package com.example.droid_share.connection

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.util.Log
import com.example.droid_share.NotificationInterface
import com.example.droid_share.grid.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WifiDirectPeerScanner(
    private val manager: WifiP2pManager,
    private val channel: Channel,
    private var notifier: NotificationInterface,
) : PeerListListener {

    companion object {
        private const val TAG = "WifiDirectPeerScanner"
        private const val WIFI_DIRECT_PEER_SCAN_PERIOD = 30000L
    }

    private var isActive = false
    private var stopTimer: Job? = null
    private var devices = mutableListOf<WifiP2pDevice>()

    private val discoveryListener = object : ActionListener {
        override fun onSuccess() {
            Log.d(TAG, "discoveryListener, onSuccess")
        }
        override fun onFailure(code: Int) {
            Log.d(TAG, "discoveryListener, onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isActive) {
            stopScan()
        }

        Log.d(TAG, "WifiDirectPeerScanner, startScan()")
        isActive = true
        devices.clear()
        showDiscoveredDevices()
        manager.discoverPeers(channel, discoveryListener)

//        stopTimer = CoroutineScope(Dispatchers.IO).launch {
//            delay(WIFI_DIRECT_PEER_SCAN_PERIOD)
//            stopScan()
//        }
        // CoroutineScope(Dispatchers.IO).launch {
        notifier.showToast("Wi-Fi direct peers discovery started")
        //}
    }

    fun stopScan() {
        if (!isActive) {
            return
        }
        Log.d(TAG, "WifiDirectPeerScanner, stopScan()")
        manager.stopPeerDiscovery(channel, discoveryListener)
        showDiscoveredDevices()
        isActive = false
    }

    override fun onPeersAvailable(peers: WifiP2pDeviceList?) {

        peers?.let {
            if (isActive) {
                devices.clear()
                devices.addAll(peers.deviceList)
                showDiscoveredDevices()
            }
        }
    }

    private fun showDiscoveredDevices() {
        CoroutineScope(Dispatchers.Main).launch {
            notifier.onDeviceListUpdate(devices.map { DeviceInfo(it) })
        }
    }
}