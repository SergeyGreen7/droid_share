package com.example.droid_share.connection

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.os.Parcelable
import android.util.Log
import com.example.droid_share.MainActivity

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
class WifiP2pBroadcastReceiver(
    private val controller: WifiP2pController,
    private val activity: MainActivity
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "WifiDirectReceiver"
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
            // UI update to indicate wifi p2p status.
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wifi Direct mode is enabled
                activity.setIsWifiP2pEnabled(true)
            } else {
                activity.setIsWifiP2pEnabled(false)
                activity.resetData()
            }
            Log.d(TAG, "WIFI_P2P_STATE_CHANGED_ACTION, P2P state changed - $state")
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {

            // request available peers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            Log.d(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION, P2P peers changed")

             controller.requestPeers(activity.gridView.customAdapter as PeerListListener)

//            val groupInfo = intent.getParcelableExtra(String).
//                .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP) as WifiP2pGroup
//            Log.d(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION, P2P group info $groupInfo")

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION == action) {
            val networkInfo = intent
                .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo
            Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION, P2P connection changed:\n$networkInfo")

//            val groupInfo = intent
//                .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP) as WifiP2pGroup
//            Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION, P2P group info $groupInfo")

            if (networkInfo.isConnected) {
                // we are connected with the other device, request connection
                // info to find group owner IP
                controller.requestConnectionInfo(activity as ConnectionInfoListener)
            } else {
                // It's a disconnect
                activity.resetData()
            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action) {
            val wifiP2pDevice = intent.getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice
            Log.d(TAG, "WIFI_P2P_THIS_DEVICE_CHANGED_ACTION, P2P this device changed $wifiP2pDevice")
//            val fragment = activity.supportFragmentManager
//                .findFragmentById(R.id.fragment_list) as DeviceListFragment
//            fragment.updateThisDevice(wifiP2pDevice)

        }
    }
}