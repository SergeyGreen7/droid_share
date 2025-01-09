package com.example.droid_share

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
import android.nfc.NfcAdapter
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity.NSD_SERVICE
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.example.droid_share.connection.BluetoothController
import com.example.droid_share.connection.GattClient
import com.example.droid_share.connection.GattServer
import com.example.droid_share.connection.LnsController
import com.example.droid_share.connection.NfcController
import com.example.droid_share.connection.WifiP2pBroadcastReceiver
import com.example.droid_share.connection.WifiP2pController
import com.example.droid_share.connection.nfc.NfcHostApduService
import com.example.droid_share.data.BluetoothDataTransceiver
import com.example.droid_share.data.NsdDataTransceiver
import com.example.droid_share.data.WifiDataTransceiver
import com.example.droid_share.fragments.BaseFragment
import com.example.droid_share.grid.DeviceGridView
import com.example.droid_share.grid.DeviceInfo
import com.example.droid_share.grid.InfoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class OldFragment : BaseFragment() {

    companion object {
        private const val TAG = "OldFragment"
        private const val REQUEST_ENABLE_BT = 1
    }

    // private var manager: WifiP2pManager? = null
    // private var progressDialog: ProgressDialog? = null

    private lateinit var wifiP2pController: WifiP2pController
    private lateinit var lnsController: LnsController
    private lateinit var bluetoothController: BluetoothController
//    private lateinit var bluetoothClientServer: BluetoothClientServer

    // tmp
    // var bluetoothServer: BluetoothServerSocket? = null
    private lateinit var gattClient: GattClient
    private lateinit var gattServer: GattServer

    // tmp2
    private var nfcController: NfcController? = null
     var nfcAdapter: NfcAdapter? = null
    // lateinit var pendingIntent: PendingIntent
    // lateinit var intentFilters: Array<IntentFilter>
    // var techLists: Array<Array<String>>? = null


    private lateinit var wifiDataTransceiver: WifiDataTransceiver
    private lateinit var bltDataTransceiver: BluetoothDataTransceiver
    private lateinit var nsdDataTransceiver: NsdDataTransceiver

    lateinit var gridView: DeviceGridView
//    lateinit var detailView: View

    private var deviceInfo: DeviceInfo? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    // private var channel: Channel? = null
    private var receiver: WifiP2pBroadcastReceiver? = null

    private val statusUpdater = object : StatusUpdater {
        override fun onDeviceInfoUpdate(newDeviceInfo: DeviceInfo?) {
            deviceInfo = newDeviceInfo

            if (deviceInfo == null) {
                requireActivity().findViewById<View>(R.id.btn_send_file_of).visibility = View.GONE
                requireActivity().findViewById<View>(R.id.btn_connect_of).visibility = View.GONE
                requireActivity().findViewById<TextView>(R.id.device_address_of).text = resources.getString(R.string.empty)
                requireActivity().findViewById<TextView>(R.id.device_info_of).text = resources.getString(R.string.empty)
                requireActivity().findViewById<TextView>(R.id.group_owner_of).text = resources.getString(R.string.empty)
                requireActivity().findViewById<TextView>(R.id.status_text_of).text = resources.getString(R.string.empty)
                return
            }
            val info = deviceInfo as DeviceInfo

//            Log.d(TAG, "device info type: ${info.type}")
            when (info.type) {
                InfoType.TEST -> {
                    requireActivity().findViewById<View>(R.id.btn_connect_of).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.btn_send_file_of).visibility = View.VISIBLE
                }
                InfoType.WIFI_DIRECT_PEER,
                InfoType.WIFI_DIRECT_SERVICE -> {
                    when (info.wifiP2pDevice?.status) {
                        WifiP2pDevice.CONNECTED,
                        WifiP2pDevice.INVITED -> {
                            requireActivity().findViewById<Button>(R.id.btn_connect_of).text = resources.getString(R.string.disconnect_peer_button)
                        }
                        WifiP2pDevice.FAILED,
                        WifiP2pDevice.AVAILABLE,
                        WifiP2pDevice.UNAVAILABLE -> {
                            requireActivity().findViewById<Button>(R.id.btn_connect_of).text = resources.getString(R.string.connect_peer_button)
                        }
                    }
                    requireActivity().findViewById<View>(R.id.btn_connect_of).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.btn_send_file_of).visibility = View.VISIBLE
                }
                InfoType.BLUETOOTH -> {
                    requireActivity().findViewById<View>(R.id.btn_connect_of).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.btn_send_file_of).visibility = View.VISIBLE
                }
                InfoType.NSD -> {
                    requireActivity().findViewById<View>(R.id.btn_connect_of).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.btn_send_file_of).visibility = View.VISIBLE
                }
                InfoType.BLE -> {
                    requireActivity().findViewById<View>(R.id.btn_connect_of).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.btn_send_file_of).visibility = View.VISIBLE
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
                TODO("Not yet implemented")
            }

            override fun onDeviceListUpdate(deviceList: List<DeviceInfo>) {
                CoroutineScope(Dispatchers.Main).launch{
                    gridView.updateDataSet(deviceList)
                }
            }

            override fun onWifiP2pConnection(info: WifiP2pInfo) {
                // The owner IP is now known.
                var view = requireActivity().findViewById<View>(R.id.group_owner_of) as TextView
                view.text = if (info.isGroupOwner) {
                    resources.getString(R.string.group_owner_text)
                } else {
                    resources.getString(R.string.not_group_owner_text)
                }
                view = requireActivity().findViewById<View>(R.id.device_info_of) as TextView
                view.text = "Group Owner IP - " + info.groupOwnerAddress.hostAddress

                requireActivity().findViewById<View>(R.id.btn_connect_of).isEnabled = true
                requireActivity().findViewById<View>(R.id.btn_send_file_of).isEnabled = true

                CoroutineScope(Dispatchers.IO).launch {
                    wifiDataTransceiver.createSocket(info, txFilePackDscr)
                }
            }
        }
    }

    @SuppressLint("MissingPermission", "InflateParams", "UnspecifiedImmutableFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "start onCreate" )
//        Log.d(TAG, "intent = $intent")

        super.onCreate(savedInstanceState)
        // setContentView(R.layout.main)
        setHasOptionsMenu(true)

        val manager = requireActivity().getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(context, Looper.getMainLooper(), null)
        wifiP2pController = WifiP2pController(manager, channel, notifier)
        receiver = WifiP2pBroadcastReceiver(wifiP2pController, requireActivity())
//
        val bluetoothManager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothController = BluetoothController(requireContext(), bluetoothManager, notifier)
        requireActivity().registerReceiver(bluetoothController.receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
//        bluetoothClientServer = BluetoothClientServer()

        if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(TAG, "current device doesn't support BLE functionality")
            requireActivity().finish();
        }
        gattClient = GattClient(requireContext(), bluetoothManager)
        gattServer = GattServer(requireContext(), bluetoothManager)

        lnsController = LnsController(requireActivity().getSystemService(NSD_SERVICE) as NsdManager, notifier)

        // tmp
        if (requireActivity().packageManager?.hasSystemFeature(PackageManager.FEATURE_NFC) == true) {
            nfcController = NfcController(requireContext(), NfcAdapter.getDefaultAdapter(requireContext()), notifier)
        } else {
            notifier.showToast("The device doesn't have the NFC hardware")
        }
//
        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        if (!bluetoothController.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            ActivityCompat.startActivityForResult(requireActivity(), enableBtIntent, REQUEST_ENABLE_BT, null)
        }
        else {
            // bluetoothController.printPairedDevices()

//            bluetoothController.startDiscovery()
        }
//
        bltDataTransceiver = BluetoothDataTransceiver(notifier)
        wifiDataTransceiver = WifiDataTransceiver(notifier)
        nsdDataTransceiver = NsdDataTransceiver(notifier)

        // requireActivity().requestPerms()

//        // Get intent, action and MIME type
//        Log.d(TAG, "onCreate(), intent = $intent")
//        Log.d(TAG, "onCreate(), intent.action = ${intent.action}")
//        resolveIntent(intent)

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_old, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // setContentView(R.layout.activity_main)

        gridView = DeviceGridView(requireContext(), requireActivity()
            .findViewById(R.id.recycler_list_of), statusUpdater, 3)
//        detailView = LayoutInflater.from(requireContext())
//            .inflate(R.layout.device_detail, null)

        requireActivity().findViewById<View>(R.id.btn_connect_of).setOnClickListener { view ->
            if (deviceInfo == null) {
                return@setOnClickListener
            }
            val info = deviceInfo!!

            when (info.type)
            {
                InfoType.TEST -> {

                }
                InfoType.WIFI_DIRECT_PEER,
                InfoType.WIFI_DIRECT_SERVICE -> {
                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {

                        wifiP2pController.connectP2pDevice(info.wifiP2pDevice!!, info.deviceAddress, info.deviceName)
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                        // iew.isEnabled = false
                    } else {
                        wifiDataTransceiver.destroySocket()
                        wifiP2pController.disconnect(deviceInfo?.wifiP2pDevice)
                        requireActivity().findViewById<View>(R.id.btn_send_file_of).isEnabled = false
                    }
                }
                InfoType.BLUETOOTH -> {
                    val uuid = UUID.fromString("94c838f1-8ef1-4f2d-8b97-9b94675d139a")

                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        Log.d(TAG, "uuid: $uuid")
                        CoroutineScope(Dispatchers.IO).launch {
                            bltDataTransceiver.startClient(info.bluetoothDevice!!, uuid)
                        }
                        requireActivity().findViewById<View>(R.id.btn_send_file_of).isEnabled = true
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        bltDataTransceiver.destroySocket()
                        requireActivity().findViewById<View>(R.id.btn_send_file_of).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }
                InfoType.NSD -> {
                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        CoroutineScope(Dispatchers.IO).launch {
                            nsdDataTransceiver.createSocket(false, info.nsdServiceInfo!!, txFilePackDscr)
                        }
                        requireActivity().findViewById<View>(R.id.btn_send_file_of).isEnabled = true
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        nsdDataTransceiver.destroySocket()
                        requireActivity().findViewById<View>(R.id.btn_send_file_of).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }
                InfoType.BLE -> {
                    Log.d(TAG, "ble info: ${info.bleScanResult!!}")

                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        gattClient.connect(info.bleScanResult!!)
                        requireActivity().findViewById<View>(R.id.btn_send_file_of).isEnabled = true
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        gattClient.disconnect()
                        requireActivity().findViewById<View>(R.id.btn_send_file_of).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }
            }

        }

        requireActivity().findViewById<View>(R.id.btn_send_file_of).isEnabled = false
        requireActivity().findViewById<View>(R.id.btn_send_file_of).setOnClickListener {
//            val intent = Intent(Intent.ACTION_GET_CONTENT)
//            intent.type = "*/*"
//            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
//            startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE)
            startActionGetContent()
        }

        requireActivity().addMenuProvider(object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    // Add menu items here
                    menuInflater.inflate(R.menu.old_menu, menu)
                }
            override fun onMenuItemSelected(item: MenuItem): Boolean {
                // Handle the menu selection

                when (item.itemId) {
                    R.id.old_menu_item_wifi_settings -> {
                        // Since this is the system wireless settings activity, it's
                        // not going to send us a result. We will be notified by
                        // WiFiDeviceBroadcastReceiver instead.
                        // startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        return true
                    }

                    R.id.old_menu_item_create_service -> {
                        if (item.title == resources.getString(R.string.create_wifi_direct_service_button)) {
                            wifiP2pController.registerP2pService()
                            item.title = resources.getString(R.string.remove_wifi_direct_service_button)
                        } else {
                            wifiP2pController.unregisterP2pService()
                            item.title = resources.getString(R.string.create_wifi_direct_service_button)
                        }
                        return true
                    }

                    R.id.old_menu_item_discover_wifi_direct_services -> {
                        if (item.title == resources.getString(R.string.start_discover_wifi_direct_services_button)) {
                            wifiP2pController.startDiscoverP2pService()
                            item.title = resources.getString(R.string.stop_discover_wifi_direct_services_button)
                        } else {
                            wifiP2pController.stopDiscoverP2pServices()
                            item.title = resources.getString(R.string.start_discover_wifi_direct_services_button)
                        }
                        return true
                    }

                    R.id.old_menu_item_wifi_direct_peer_discovery -> {
                        if (item.title == resources.getString(R.string.start_discover_wifi_peers_button)) {
                            wifiP2pController.startDiscoverP2pPeers()
                            item.title = resources.getString(R.string.stop_discover_wifi_peers_button)
                        } else {
                            wifiP2pController.stopDiscoverP2pPeers()
                            item.title = resources.getString(R.string.start_discover_wifi_peers_button)
                        }
                        return true
                    }

                    R.id.old_menu_item_bluetooth_discovery -> {
                        bluetoothController.startDiscovery()
                        Toast.makeText(requireActivity(),
                            R.string.discovery_bluetooth_initiated, Toast.LENGTH_SHORT).show()
                        return true
                    }

                    R.id.old_menu_item_bluetooth_server -> {
                        if (item.title == resources.getString(R.string.create_server_bluetooth_button)) {
                            val requestCode = 1;
                            val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                            }
                            startActivityForResult(discoverableIntent, requestCode)

                            val name = "test_bluetooth_server"
                            val uuid = UUID.fromString("94c838f1-8ef1-4f2d-8b97-9b94675d139a")
                            Log.d(TAG, "run bluetooth server with uuid = $uuid")
                            CoroutineScope(Dispatchers.IO).launch {
                                bltDataTransceiver.startServer(bluetoothController.createServer(name, uuid))
                            }
                            item.title = resources.getString(R.string.remove_server_bluetooth_button)
                        } else {
                            bltDataTransceiver.destroySocket()
                            item.title = resources.getString(R.string.create_server_bluetooth_button)
                        }

                        return true
                    }

                    R.id.old_menu_item_discover_local_network_service -> {
                        if (item.title == resources.getString(R.string.start_discover_lns_button)) {
                            lnsController.startDiscoverLocalNetworkServices()
                            item.title = resources.getString(R.string.stop_discover_lns_button)
                        } else {
                            lnsController.stopDiscoverLocalNetworkServices()
                            item.title = resources.getString(R.string.start_discover_lns_button)
                        }
                        return true
                    }

                    R.id.old_menu_item_create_local_network_service -> {
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

                    R.id.old_menu_item_send_nfc_message -> {
                        CoroutineScope(Dispatchers.IO).launch {

                            val message = "Test data transmitted over NFC"
                            val nfcIntent = Intent(requireActivity(), NfcHostApduService::class.java)
                            nfcIntent.putExtra("ndefMessage", message)
                            notifier.showToast("Message is send as NDEF message: '$message'")
                            val res = requireActivity().startService(nfcIntent)
                            Log.d(TAG, "res on 'startService' = $res")

                            // val myService = Intent(this@MainActivity, NfcHostApduService::class.java)
                            // stopService(myService)
                        }
                        return true
                    }

                    R.id.old_menu_item_discover_ble_devices -> {
                        bluetoothController.startBleDiscovery()
                        return true
                    }

                    R.id.old_menu_item_start_ble_advertising -> {
//                CoroutineScope(Dispatchers.IO).launch {
//                    bluetoothController.startBleAdvertising()
//                }
                        return true
                    }

                    R.id.old_menu_item_start_ble_service -> {
                        if (item.title == resources.getString(R.string.start_ble_service)) {
                            // CoroutineScope(Dispatchers.IO).launch {
                            gattServer.startBleService()
                            // }
                            item.title = resources.getString(R.string.stop_ble_service)
                        } else {
                            gattServer.stopBleService()
                            item.title = resources.getString(R.string.start_ble_service)
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
        Log.d(TAG, "start onResume()")
        receiver?.also { receiver ->
            requireActivity().registerReceiver(receiver, intentFilter)
        }
        requireActivity().registerReceiver(bluetoothController.receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE)

        nfcController?.adapter?.enableForegroundDispatch(requireActivity(), pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "start onPause()")

        receiver?.also { receiver ->
            requireActivity().unregisterReceiver(receiver)
        }
        requireActivity().unregisterReceiver(bluetoothController.receiver)

        nfcController?.adapter?.disableForegroundDispatch(requireActivity())
    }

    override fun resolveActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
        Log.d(TAG, "OldFragment, start resolveActivityResult()")
        Log.d(TAG, "intent.action = ${intent.action}")
        resolveIntent(intent)
    }

    override fun resolveNewIntent(intent: Intent) {
        Log.d(TAG, "OldFragment, start on resolveNewIntent()")
        Log.d(TAG, "intent.action = ${intent.action}")
        resolveIntent(intent)
    }

    private fun resolveIntent(intent: Intent) {
        Log.d(TAG, "OldFragment, start on resolveIntent()")
        Log.d(TAG, "intent.action = ${intent.action}")

        getUriFromIntent(intent)
        getFilePackDscr()

        if (txFilePackDscr.isEmpty()) {
            return
        }

        Log.d(TAG, "wifiDataTransceiver.isConnectionEstablished() " +
                "= ${wifiDataTransceiver.isConnectionEstablished()}")
        Log.d(TAG, "bltDataTransceiver.isConnectionEstablished() " +
                "= ${bltDataTransceiver.isConnectionEstablished()}")
        Log.d(TAG, "nsdDataTransceiver.isConnectionEstablished() " +
                "= ${nsdDataTransceiver.isConnectionEstablished()}")

        if (wifiDataTransceiver.isConnectionEstablished()) {
            Log.d(TAG, "wifiDataTransceiver.sendData(txFilePackDscr)")
            wifiDataTransceiver.sendData(txFilePackDscr)
        } else if (bltDataTransceiver.isConnectionEstablished()) {
            Log.d(TAG, "bltDataTransceiver.sendData(txFilePackDscr)")
            bltDataTransceiver.sendData(txFilePackDscr)
        } else if (nsdDataTransceiver.isConnectionEstablished()) {
            Log.d(TAG, "nsdDataTransceiver.sendData(txFilePackDscr)")
            nsdDataTransceiver.sendData(txFilePackDscr)
        }
    }
}