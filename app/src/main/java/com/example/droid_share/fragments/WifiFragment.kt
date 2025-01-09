package com.example.droid_share

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.net.nsd.NsdManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity.NSD_SERVICE
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.example.droid_share.connection.LnsController
import com.example.droid_share.connection.WifiP2pBroadcastReceiver
import com.example.droid_share.connection.WifiP2pController
import com.example.droid_share.data.NsdDataTransceiver
import com.example.droid_share.data.WifiDataTransceiver
import com.example.droid_share.fragments.BaseFragment
import com.example.droid_share.grid.DeviceGridView
import com.example.droid_share.grid.DeviceInfo
import com.example.droid_share.grid.InfoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiFragment : BaseFragment() {

    companion object {
        private const val TAG = "WifiFragment"
    }

    private var peers = mutableListOf<WifiP2pDevice>()
    private var services = mutableListOf<WifiP2pDevice>()

    lateinit var gridView: DeviceGridView

    private lateinit var wifiP2pController: WifiP2pController
    private lateinit var lnsController: LnsController

    private lateinit var wifiDataTransceiver: WifiDataTransceiver
    private lateinit var nsdDataTransceiver: NsdDataTransceiver

    private lateinit var receiver: WifiP2pBroadcastReceiver

    private val statusUpdater = object : StatusUpdater {
        override fun onDeviceInfoUpdate(newDeviceInfo: DeviceInfo?) {
            selectedDeviceInfo = newDeviceInfo

            if (selectedDeviceInfo == null) {
                requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).visibility = View.GONE
                requireActivity().findViewById<View>(R.id.wifi_fragment_btn_connect).visibility = View.GONE
                requireActivity().findViewById<TextView>(R.id.wifi_fragment_device_address).text = resources.getString(R.string.empty)
                requireActivity().findViewById<TextView>(R.id.wifi_fragment_device_info).text = resources.getString(R.string.empty)
                requireActivity().findViewById<TextView>(R.id.wifi_fragment_group_owner).text = resources.getString(R.string.empty)
                requireActivity().findViewById<TextView>(R.id.wifi_fragment_status_text).text = resources.getString(R.string.empty)
                return
            }
            val info = selectedDeviceInfo as DeviceInfo

//            Log.d(TAG, "device info type: ${info.type}")
            when (info.type){
                InfoType.TEST -> {
                    requireActivity().findViewById<View>(R.id.wifi_fragment_btn_connect).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).visibility = View.VISIBLE
                }

                InfoType.WIFI_DIRECT_PEER,
                InfoType.WIFI_DIRECT_SERVICE -> {
                    val buttonConnect = requireActivity().findViewById<Button>(R.id.wifi_fragment_btn_connect)
                    when (info.wifiP2pDevice?.status) {
                        WifiP2pDevice.CONNECTED,
                        WifiP2pDevice.INVITED -> {
                            buttonConnect.text = resources.getString(R.string.disconnect_peer_button)
                        }
                        WifiP2pDevice.FAILED,
                        WifiP2pDevice.AVAILABLE,
                        WifiP2pDevice.UNAVAILABLE -> {
                            buttonConnect.text = resources.getString(R.string.connect_peer_button)
                        }
                    }
                    requireActivity().findViewById<View>(R.id.wifi_fragment_btn_connect).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).visibility = View.VISIBLE
                }

                InfoType.NSD -> {
                    requireActivity().findViewById<View>(R.id.wifi_fragment_btn_connect).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).visibility = View.VISIBLE
                }

                else -> {
                    throw Exception("Unknown info type for Wi-Fi fragment")
                }
            }
        }
    }

    override fun config(notifier: NotificationInterface) {
        this.notifier = object: NotificationInterface {
            override suspend fun showProgressDialog(title: String, message: String,
                                                    listener: DialogInterface.OnClickListener) {
                notifier.showProgressDialog(title, message, listener)
            }

            override suspend fun updateProgressDialog(message: String) {
                notifier.updateProgressDialog(message)
            }

            override suspend fun dismissProgressDialog() {
                notifier.dismissProgressDialog()
            }

            override fun showToast(message: String) {
                notifier.showToast(message)
            }

            override suspend fun showAlertDialog(message: String,
                                                 negativeListener: DialogInterface.OnClickListener,
                                                 positiveListener: DialogInterface.OnClickListener
            ) {
                notifier.showAlertDialog(message, negativeListener, positiveListener)
            }

            override fun dismissAlertDialog() {
                notifier.dismissAlertDialog()
            }

            override fun cancelConnection() {
                TODO("Not yet implemented")
            }

            override suspend fun disconnect() {
                Log.d(TAG, "WifiFragment, disconnect()")
                // TODO("Not yet implemented")
            }

            override fun onDeviceListUpdate(deviceList: List<DeviceInfo>) {
                CoroutineScope(Dispatchers.Main).launch{
                    gridView.updateDataSet(deviceList)
                }
            }

            override fun onWifiP2pConnection(info: WifiP2pInfo) {
                Log.d(TAG, "WifiFragment, onWifiP2pConnection()")
                // The owner IP is now known.
                var view = requireActivity().findViewById<View>(R.id.wifi_fragment_group_owner) as TextView
                view.text = if (info.isGroupOwner) {
                    resources.getString(R.string.group_owner_text)
                } else {
                    resources.getString(R.string.not_group_owner_text)
                }
                view = requireActivity().findViewById<View>(R.id.wifi_fragment_device_info) as TextView
                view.text = "Group Owner IP - " + info.groupOwnerAddress.hostAddress

                requireActivity().findViewById<View>(R.id.wifi_fragment_btn_connect).isEnabled = true
                requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).isEnabled = true

                CoroutineScope(Dispatchers.IO).launch {
                    wifiDataTransceiver.createSocket(info, txFilePackDscr)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "WifiFragment, start onCreate()")
        super.onCreate(savedInstanceState)

        val manager = requireActivity().getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(context, Looper.getMainLooper(), null)
        wifiP2pController = WifiP2pController(manager, channel, notifier)
        receiver = WifiP2pBroadcastReceiver(wifiP2pController, requireActivity())
        wifiDataTransceiver = WifiDataTransceiver(notifier)

        wifiDataTransceiver = WifiDataTransceiver(notifier)
        nsdDataTransceiver = NsdDataTransceiver(notifier)

        lnsController = LnsController(requireActivity().getSystemService(NSD_SERVICE) as NsdManager, notifier)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        Log.d(TAG, "WifiFragment, start onCreateView()")
        return inflater.inflate(R.layout.fragment_wifi, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "WifiFragment, start onViewCreated()")
        super.onViewCreated(view, savedInstanceState)

        gridView = DeviceGridView(requireContext(), requireActivity()
            .findViewById(R.id.wifi_fragment_recycler_list), statusUpdater, 3)

        requireActivity().findViewById<View>(R.id.wifi_fragment_btn_connect).setOnClickListener { view ->
            if (selectedDeviceInfo == null) {
                return@setOnClickListener
            }
            val info = selectedDeviceInfo as DeviceInfo

            when (info.type)
            {
                InfoType.TEST -> { }

                InfoType.WIFI_DIRECT_PEER,
                InfoType.WIFI_DIRECT_SERVICE -> {
                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        wifiP2pController.connectP2pDevice(info.wifiP2pDevice!!, info.deviceAddress, info.deviceName)
                        val device = info.wifiP2pDevice!!
                        connectedDevices[device.deviceAddress] = info
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        wifiDataTransceiver.destroySocket()
                        wifiP2pController.disconnect(info.wifiP2pDevice)
                        requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }

                InfoType.NSD -> {
                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        CoroutineScope(Dispatchers.IO).launch {
                            nsdDataTransceiver.createSocket(false, info.nsdServiceInfo!!, txFilePackDscr)
                        }
                        requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).isEnabled = true
                        val service = info.nsdServiceInfo!!
                        connectedDevices[service.serviceName] = info

                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        nsdDataTransceiver.destroySocket()
                        requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }

                else -> {
                    throw Exception("Unknown info type for Wi-Fi fragment")
                }
            }
        }

        requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).isEnabled = false
        requireActivity().findViewById<View>(R.id.wifi_fragment_btn_send_file).setOnClickListener {
            startActionGetContent()
        }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.wifi_menu, menu)
            }
            override fun onMenuItemSelected(item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.wifi_menu_item_wifi_settings -> {
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        return true
                    }

                    R.id.wifi_menu_item_create_service -> {
                        if (item.title == resources.getString(R.string.create_wifi_direct_service_button)) {
                            wifiP2pController.registerP2pService()
                            item.title = resources.getString(R.string.remove_wifi_direct_service_button)
                        } else {
                            wifiP2pController.unregisterP2pService()
                            item.title = resources.getString(R.string.create_wifi_direct_service_button)
                        }
                        return true
                    }

                    R.id.wifi_menu_item_discover_wifi_direct_services -> {
                        if (item.title == resources.getString(R.string.start_discover_wifi_direct_services_button)) {
                            wifiP2pController.startDiscoverP2pService()
                            item.title = resources.getString(R.string.stop_discover_wifi_direct_services_button)
                        } else {
                            wifiP2pController.stopDiscoverP2pServices()
                            item.title = resources.getString(R.string.start_discover_wifi_direct_services_button)
                        }
                        return true
                    }

                    R.id.wifi_menu_item_wifi_direct_peer_discovery -> {
                        if (item.title == resources.getString(R.string.start_discover_wifi_peers_button)) {
                            wifiP2pController.startDiscoverP2pPeers()
                            item.title = resources.getString(R.string.stop_discover_wifi_peers_button)
                        } else {
                            wifiP2pController.stopDiscoverP2pPeers()
                            item.title = resources.getString(R.string.start_discover_wifi_peers_button)
                        }
                        return true
                    }

                    R.id.wifi_menu_item_discover_local_network_service -> {
                        if (item.title == resources.getString(R.string.start_discover_lns_button)) {
                            lnsController.startDiscoverLocalNetworkServices()
                            item.title = resources.getString(R.string.stop_discover_lns_button)
                        } else {
                            lnsController.stopDiscoverLocalNetworkServices()
                            item.title = resources.getString(R.string.start_discover_lns_button)
                        }
                        return true
                    }

                    R.id.wifi_menu_item_create_local_network_service -> {
                        if (item.title == resources.getString(R.string.create_local_service_button)) {
                            lnsController.registerLocalNetworkService()
                            CoroutineScope(Dispatchers.IO).launch {
                                nsdDataTransceiver.createSocket(true, lnsController.getServiceInfo(), txFilePackDscr)
                            }
                            item.title = resources.getString(R.string.remove_local_service_button)
                        } else {
                            lnsController.unregisterLocalNetworkService()
                            nsdDataTransceiver.destroySocket()
                            item.title = resources.getString(R.string.create_local_service_button)
                        }
                        return true
                    }

                    else -> return false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    /** register the BroadcastReceiver with the intent values to be matched  */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "WifiFragment, start onResume()")

        val intentFilter = IntentFilter().apply {
            addAction(WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        requireActivity().registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "WifiFragment, start onPause()")
        requireActivity().unregisterReceiver(receiver)
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "WifiFragment, start onStop()")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "WifiFragment, start onDestroyView()")
        connectedDevices.forEach { device ->
            val info = device.value
            if (info.type == InfoType.WIFI_DIRECT_PEER) {
                wifiP2pController.disconnect(info.wifiP2pDevice)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WifiFragment, start onDestroy()")
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(TAG, "WifiFragment, start onDetach()")
    }

    override fun resolveActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
        Log.d(TAG, "WifiFragment, start resolveActivityResult()")
        Log.d(TAG, "intent.action = ${intent.action}")
        resolveIntent(intent)
    }

    override fun resolveNewIntent(intent: Intent) {
        Log.d(TAG, "WifiFragment, start on resolveNewIntent()")
        Log.d(TAG, "intent.action = ${intent.action}")
        resolveIntent(intent)
    }

    private fun resolveIntent(intent: Intent) {
        Log.d(TAG, "WifiFragment, start on resolveIntent()")
        Log.d(TAG, "intent.action = ${intent.action}")

        getUriFromIntent(intent)
        getFilePackDscr()

        if (txFilePackDscr.isEmpty()) {
            return
        }

        Log.d(TAG, "wifiDataTransceiver.isConnectionEstablished() " +
                "= ${wifiDataTransceiver.isConnectionEstablished()}")
        Log.d(TAG, "nsdDataTransceiver.isConnectionEstablished() " +
                "= ${nsdDataTransceiver.isConnectionEstablished()}")

        if (wifiDataTransceiver.isConnectionEstablished()) {
            Log.d(TAG, "wifiDataTransceiver.sendData(txFilePackDscr)")
            wifiDataTransceiver.sendData(txFilePackDscr.copy())
        } else if (nsdDataTransceiver.isConnectionEstablished()) {
            Log.d(TAG, "nsdDataTransceiver.sendData(txFilePackDscr)")
            nsdDataTransceiver.sendData(txFilePackDscr.copy())
        }
    }

}