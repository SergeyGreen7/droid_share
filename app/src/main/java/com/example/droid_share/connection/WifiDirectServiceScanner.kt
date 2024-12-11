package com.example.droid_share.connection

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.*
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import com.example.droid_share.NotificationInterface
import com.example.droid_share.connection.WifiP2pController.Companion
import com.example.droid_share.grid.DeviceInfo
import com.example.droid_share.grid.WifiDirectServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiDirectServiceScanner(
    private val manager: WifiP2pManager,
    private val channel: Channel,
    private var notifier: NotificationInterface,
) {

    companion object {
        private const val TAG = "WifiDirectServiceScanner"
    }

    private var isActive = false
    private val serviceFilter = WifiDirectService.SERVICE_TYPE
    private val devices = HashMap<String, WifiDirectServiceInfo>()

    private val txtListener = DnsSdTxtRecordListener { fullDomain, record, device ->
        Log.d(TAG, "txtListener, DnsSdTxtRecord available:")
        Log.d(TAG, "    fullDomain: $fullDomain")
        Log.d(TAG, "    record: $record")
        Log.d(TAG, "    device: $device")

//            record["buddyname"]?.also {
//                buddies[device.deviceAddress] = it
//            }
    }

    private val servListener = DnsSdServiceResponseListener { instanceName, registrationType, resourceType ->
        // Update the device name with the human-friendly version from
        // the DnsTxtRecord, assuming one arrived.
        Log.d(TAG, "servListener, DnsSdService available:")
        Log.d(TAG, "    instanceName: $instanceName")
        Log.d(TAG, "    registrationType: $registrationType")
        Log.d(TAG, "    resourceType: $resourceType")

        if (registrationType.contains(serviceFilter)) {
            devices[instanceName] = WifiDirectServiceInfo(instanceName, registrationType, resourceType)
            showDiscoveredDevices()
        }

        // resourceType.deviceName = buddies[resourceType.deviceAddress] ?: resourceType.deviceName

        // Add to the custom adapter defined specifically for showing
        // wifi devices.
//            val fragment = fragmentManager
//                .findFragmentById(R.id.frag_peerlist) as WiFiDirectServicesList
//            (fragment.listAdapter as WiFiDevicesAdapter).apply {
//                add(resourceType)
//                notifyDataSetChanged()
//            }

//            Log.d(TAG, "onBonjourServiceAvailable $instanceName")
    }

    @SuppressLint("MissingPermission")
    fun startScan() {

        if (isActive) {
            stopScan()
        }

        devices.clear()
        isActive = true
        Log.d(TAG, "WifiDirectServiceScanner, startScan()")
//        manager.discoverPeers(channel, object : ActionListener {
//            override fun onSuccess() {
//                Log.d(TAG, "WifiDirectServiceScanner, discoverPeers, onSuccess")
//            }
//            override fun onFailure(code: Int) {
//                Log.d(TAG, "WifiDirectServiceScanner, discoverPeers, onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
//            }
//        })

        manager.setDnsSdResponseListeners(channel, servListener, txtListener)

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(channel, serviceRequest, object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "WifiDirectServiceScanner, addServiceRequest(), onSuccess")
            }
            override fun onFailure(code: Int) {
                Log.d(TAG, "WifiDirectServiceScanner, addServiceRequest(), onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
            }
        })
        manager.discoverServices(channel, object : ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "WifiDirectServiceScanner, discoverServices(), onSuccess")
            }
            override fun onFailure(code: Int) {
                Log.d(TAG, "WifiDirectServiceScanner, discoverServices(), onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
            }
        })
        notifier.showToast("Wi-Fi direct service discovery started")
    }

    fun stopScan() {
        Log.d(TAG, "WifiDirectServiceScanner, stopScan()")
//        manager.stopPeerDiscovery(channel, object : ActionListener {
//            override fun onSuccess() {
//                Log.d(TAG, "WifiDirectServiceScanner, stopPeerDiscovery, onSuccess")
//            }
//            override fun onFailure(code: Int) {
//                Log.d(TAG, "WifiDirectServiceScanner, stopPeerDiscovery, onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
//            }
//        })

        isActive = false
        manager.stopPeerDiscovery(channel,  object : ActionListener {
            override fun onSuccess() {
                // Success!
                Log.d(TAG, "WifiDirectServiceScanner, stopPeerDiscovery(), onSuccess")
            }
            override fun onFailure(code: Int) {
                // Command failed. Check for P2P_UNSUPPORTED, ERROR, or BUSY
                Log.d(TAG, "WifiDirectServiceScanner, stopPeerDiscovery(), onFailure, ${WifiUtils.getErrorCodeDescription(code)}")
            }
        })
    }

    fun showDiscoveredDevices() {
        CoroutineScope(Dispatchers.Main).launch {
            notifier.onDeviceListUpdate(devices.map { DeviceInfo(it.value) })
        }
    }
}