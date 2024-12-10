package com.example.droid_share

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.WifiP2pManager.DeviceInfoListener
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.ListFragment
import java.util.ArrayList

class DeviceListFragment : ListFragment(),
    PeerListListener, ConnectionInfoListener, DeviceInfoListener  {
    companion object {
        private const val TAG = "DeviceListFragment"
    }

    private val peers = ArrayList<WifiP2pDevice>()
    private var progressDialog: ProgressDialog? = null
    private var mContentView: View? = null
    /**
     * @return this device
     */
    var device: WifiP2pDevice? = null
        private set

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        this.listAdapter = WiFiPeerListAdapter(requireActivity(), R.layout.row_devices, peers)
        this.listView.choiceMode = ListView.CHOICE_MODE_SINGLE
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mContentView = inflater.inflate(R.layout.device_list, null)
        return mContentView
    }

    private fun getDeviceStatus(deviceStatus: Int): String {
        // Log.d(TAG, "Peer status :$deviceStatus")
        return when (deviceStatus) {
            WifiP2pDevice.AVAILABLE -> "Available"
            WifiP2pDevice.INVITED -> "Invited"
            WifiP2pDevice.CONNECTED -> "Connected"
            WifiP2pDevice.FAILED -> "Failed"
            WifiP2pDevice.UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val device = listAdapter!!.getItem(position) as WifiP2pDevice
//        (activity as DeviceActionListener).showDetails(device)
    }

    /**
     * Array adapter for ListFragment that maintains WifiP2pDevice list.
     */
    private inner class WiFiPeerListAdapter (
        context: Context,
        textViewResourceId: Int,
        private val items: List<WifiP2pDevice>) : ArrayAdapter<WifiP2pDevice>(context, textViewResourceId, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var v = convertView
            if (v == null) {
                val vi = requireActivity().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                v = vi.inflate(R.layout.row_devices, null)
            }
            v!!

            val device = items[position]
            val top = v.findViewById<View>(R.id.device_name) as TextView
            val bottom = v.findViewById<View>(R.id.device_details) as TextView
            top.text = device.deviceName
            bottom.text = getDeviceStatus(device.status)
            // Log.d(TAG, "WiFiPeerListAdapter getView")

            return v

        }
    }

    fun updateThisDevice(device: WifiP2pDevice) {
        // Log.d(TAG, "updateThisDevice device:$device")
        this.device = device
        var view = mContentView!!.findViewById<View>(R.id.my_name) as TextView
        view.text = buildString {
            append(device.deviceName)
            append(" - ")
            append(device.deviceAddress)
        }
        view = mContentView!!.findViewById<View>(R.id.my_status) as TextView
        view.text = getDeviceStatus(device.status)
    }

    override fun onPeersAvailable(peerList: WifiP2pDeviceList?) {
        dismissProgressDialog()
        peers.clear()
        peers.addAll(peerList!!.deviceList)
//        for (s in peerList.deviceList) {
//            Log.d(TAG, "onPeersAvailable, $s")
//        }
        // Log.d(TAG, "\nDiscovered peers:")
        // Log.d(TAG, "\nDiscovered peers:")
//        peers.forEach { peer ->
//            Log.d(TAG, " discovered peer: " + peer.deviceName + " - " + peer.deviceAddress)
//        }
        (listAdapter as WiFiPeerListAdapter).notifyDataSetChanged()
        if (peers.size == 0) {
            Log.d(TAG, "No devices found")
            return
        } else {
            this.listView.setSelection(0)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info == null || !info.groupFormed) {
            return
        }
        Log.d(TAG, "onConnectionInfoAvailable, info: $info")
    }

    fun onInitiateDiscovery() {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
        progressDialog = ProgressDialog.show(activity, "Press back to cancel", "finding peers", true,
            true) { }
    }

    private fun dismissProgressDialog() {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
    }

    fun clearPeers() {
        peers.clear()
        (listAdapter as WiFiPeerListAdapter).notifyDataSetChanged()
    }

//    interface DeviceActionListener {
//        fun showDetails(device: WifiP2pDevice)
//        fun cancelConnect()
//        fun connect(deviceAddress: String)
//        fun createGroup(deviceAddress: String)
//        fun disconnect()
//    }

    override fun onDeviceInfoAvailable(wifiP2pDevice: WifiP2pDevice?) {
        wifiP2pDevice?.also {device -> updateThisDevice(device)}
    }
}