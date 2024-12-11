package com.example.droid_share

import android.Manifest
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
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
import android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.tech.NfcF
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.droid_share.DeviceDetailFragment.Companion.CHOOSE_FILE_RESULT_CODE
import com.example.droid_share.DeviceDetailFragment.Companion.getTxFileDescriptor
import com.example.droid_share.DeviceDetailFragment.Companion.txFilePackDscr
import com.example.droid_share.connection.BluetoothController
import com.example.droid_share.connection.GattClient
import com.example.droid_share.connection.GattServer
import com.example.droid_share.connection.LnsController
import com.example.droid_share.connection.WifiP2pBroadcastReceiver
import com.example.droid_share.connection.WifiP2pController
import com.example.droid_share.data.BluetoothClientServer
import com.example.droid_share.data.BluetoothDataTransceiver
import com.example.droid_share.data.NsdDataTransceiver
import com.example.droid_share.data.WifiDataTransceiver
import com.example.droid_share.grid.DeviceGridView
import com.example.droid_share.grid.DeviceInfo
import com.example.droid_share.grid.InfoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID


class MainActivity : AppCompatActivity(), ConnectionInfoListener {
    private var manager: WifiP2pManager? = null
    private var progressDialog: ProgressDialog? = null

    private lateinit var wifiP2pController: WifiP2pController
    private lateinit var bluetoothController: BluetoothController
    private lateinit var bluetoothClientServer: BluetoothClientServer

    // tmp
    var bluetoothServer: BluetoothServerSocket? = null
    private lateinit var gattClient: GattClient
    private lateinit var gattServer: GattServer

    // tmp2
    var nfcAdapter: NfcAdapter? = null
    lateinit var pendingIntent: PendingIntent
    lateinit var intentFilters: Array<IntentFilter>
    var techLists: Array<Array<String>>? = null


    private lateinit var wifiDataTransceiver: WifiDataTransceiver
    private lateinit var bltDataTransceiver: BluetoothDataTransceiver
    private lateinit var nsdDataTransceiver: NsdDataTransceiver

    private lateinit var lnsController: LnsController

    lateinit var gridView: DeviceGridView
    lateinit var detailView: View

    private var deviceInfo: DeviceInfo? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    private var channel: Channel? = null
    private var receiver: WifiP2pBroadcastReceiver? = null

    private val statusUpdater = object : StatusUpdater {
        override fun onDeviceInfoUpdate(newDeviceInfo: DeviceInfo?) {
            deviceInfo = newDeviceInfo
            if (deviceInfo == null) {
                findViewById<View>(R.id.btn_connect_dd).visibility = View.GONE
                findViewById<View>(R.id.btn_send_file_dd).visibility = View.GONE
                findViewById<TextView>(R.id.device_address).text = resources.getString(R.string.empty)
                findViewById<TextView>(R.id.device_info).text = resources.getString(R.string.empty)
                findViewById<TextView>(R.id.group_owner).text = resources.getString(R.string.empty)
                findViewById<TextView>(R.id.status_text).text = resources.getString(R.string.empty)
                return
            }
            val info = deviceInfo as DeviceInfo

//            Log.d(TAG, "device info type: ${info.type}")
            when (info.type) {
                InfoType.TEST -> {
                    findViewById<View>(R.id.btn_connect_dd).visibility = View.VISIBLE
                    findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
                }
                InfoType.WIFI_DIRECT_PEER,
                InfoType.WIFI_DIRECT_SERVICE -> {
                    when (info.wifiP2pDevice?.status) {
                        WifiP2pDevice.CONNECTED,
                        WifiP2pDevice.INVITED -> {
                            findViewById<Button>(R.id.btn_connect_dd).text = resources.getString(R.string.disconnect_peer_button)
                        }
                        WifiP2pDevice.FAILED,
                        WifiP2pDevice.AVAILABLE,
                        WifiP2pDevice.UNAVAILABLE -> {
                            findViewById<Button>(R.id.btn_connect_dd).text = resources.getString(R.string.connect_peer_button)
                        }
                    }
                    findViewById<View>(R.id.btn_connect_dd).visibility = View.VISIBLE
                    findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
                }
                InfoType.BLUETOOTH -> {
                    findViewById<View>(R.id.btn_connect_dd).visibility = View.VISIBLE
                    findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
                }
                InfoType.NSD -> {
                    findViewById<View>(R.id.btn_connect_dd).visibility = View.VISIBLE
                    findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
                }
                InfoType.BLE -> {
                    findViewById<View>(R.id.btn_connect_dd).visibility = View.VISIBLE
                    findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
                }
            }

        }
    }

    private val notifier = object: NotificationInterface{
        override suspend fun showProgressDialog(title: String, message: String, listener: DialogInterface.OnClickListener) {
            withContext(Dispatchers.Main) {
                progressDialog = ProgressDialog(this@MainActivity)
                with(progressDialog!!) {
                    setTitle(title)
                    setMessage(message)
                    setCancelable(true)
                    setButton(android.content.DialogInterface.BUTTON_NEGATIVE, "Cancel", listener)
                    show()
                }
            }
        }

        override suspend fun updateProgressDialog(message: String) {
            withContext(Dispatchers.Main) {
                progressDialog?.setMessage(message)
            }
        }

        override suspend fun dismissProgressDialog() {
            withContext(Dispatchers.Main) {
                progressDialog?.dismiss()
            }
        }

        override fun showToast(message: String) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }

        override suspend fun showAlertDialog(message: String,
                                             negativeListener: DialogInterface.OnClickListener,
                                             positiveListener: DialogInterface.OnClickListener) {
            withContext(Dispatchers.Main) {
                val builder = AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setNegativeButton("Dismiss", negativeListener)
                    .setPositiveButton("Accept", positiveListener)
                val dialog = builder.create()
                dialog.show()
            }
        }

        override suspend fun disconnect() {
            withContext(Dispatchers.Main) {
                // TODO: This function should be implemented
                // (activity as DeviceActionListener).cancelConnect()
                // fileAsyncTask.shutdown()
            }
        }

        override fun onDeviceListUpdate(deviceList: List<DeviceInfo>) {
            CoroutineScope(Dispatchers.Main).launch{
                gridView.updateDataSet(deviceList)
            }
        }
    }

    @SuppressLint("MissingPermission", "InflateParams", "UnspecifiedImmutableFlag")
    public override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "start onCreate" )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        // setContentView(R.layout.activity_main)

        gridView = DeviceGridView(this, findViewById(R.id.recycler_list), statusUpdater, 3)
        detailView = LayoutInflater.from(this).inflate(R.layout.device_detail, null)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager?.initialize(this, mainLooper, null)
        wifiP2pController = WifiP2pController(manager!!, channel!!, notifier)
        receiver = WifiP2pBroadcastReceiver(wifiP2pController, this)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothController = BluetoothController(this, bluetoothManager, notifier)
        registerReceiver(bluetoothController.receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        bluetoothClientServer = BluetoothClientServer()

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(TAG, "current device doesn't support BLE functionality")
            finish();
        }
        gattClient = GattClient(this, bluetoothManager)
        gattServer = GattServer(this, bluetoothManager)

        lnsController = LnsController(getSystemService(NSD_SERVICE) as NsdManager, notifier)


        if (!bluetoothController.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            var request = 0
            ActivityCompat.startActivityForResult(this, enableBtIntent, REQUEST_ENABLE_BT, null)
        }
        else {
            bluetoothController.printPairedDevices()

            val requestCode = 1;
            val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            startActivityForResult(discoverableIntent, requestCode)
//            bluetoothController.startDiscovery()
        }

        bltDataTransceiver = BluetoothDataTransceiver(notifier)
        wifiDataTransceiver = WifiDataTransceiver(notifier)
        nsdDataTransceiver = NsdDataTransceiver(notifier)

        findViewById<View>(R.id.btn_connect_dd).setOnClickListener { view ->
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

                        wifiP2pController.connectP2pDevice(info.deviceAddress, info.deviceName)
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                        // iew.isEnabled = false
                    } else {
                        wifiDataTransceiver.destroySocket()
                        wifiP2pController.disconnect(deviceInfo?.wifiP2pDevice)
                        findViewById<View>(R.id.btn_send_file_dd).isEnabled = false
                    }
                }
                InfoType.BLUETOOTH -> {
                    val uuid = UUID.fromString("94c838f1-8ef1-4f2d-8b97-9b94675d139a")

                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        Log.d(TAG, "uuid: $uuid")
                        CoroutineScope(Dispatchers.IO).launch {
                            bltDataTransceiver.startClient(info.bluetoothDevice!!, uuid)
                        }
                        findViewById<View>(R.id.btn_send_file_dd).isEnabled = true
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        bltDataTransceiver.destroySocket()
                        findViewById<View>(R.id.btn_send_file_dd).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }
                InfoType.NSD -> {
                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        CoroutineScope(Dispatchers.IO).launch {
                            nsdDataTransceiver.createSocket(false, info.nsdServiceInfo!!, txFilePackDscr)
                        }
                        findViewById<View>(R.id.btn_send_file_dd).isEnabled = true
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        nsdDataTransceiver.destroySocket()
                        findViewById<View>(R.id.btn_send_file_dd).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }
                InfoType.BLE -> {
                    Log.d(TAG, "ble info: ${info.scanResult!!}")

                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        gattClient.connect(info.scanResult!!)
                        findViewById<View>(R.id.btn_send_file_dd).isEnabled = true
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        gattClient.disconnect()
                        findViewById<View>(R.id.btn_send_file_dd).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }
            }

        }

        findViewById<View>(R.id.btn_send_file_dd).isEnabled = false
        findViewById<View>(R.id.btn_send_file_dd).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE)
        }

//        manager?.requestConnectionInfo(channel, .
//            getViewById(R.id.fragment_detail) as ConnectionInfoListener)

        requestPerms()

        // Get intent, action and MIME type
        Log.d(TAG, "onCreate(), intent = $intent")
        Log.d(TAG, "onCreate(), intent.action = ${intent.action}")

        val action = intent.action
        if (Intent.ACTION_SEND == action || Intent.ACTION_SEND_MULTIPLE == action) {
            Log.d(TAG, "ACTION_SEND")
            handleSelectionIntent(intent)
        }

//        CoroutineScope(Dispatchers.IO).launch {
//            while (true) {
//                manager?.requestDeviceInfo(
//                    channel!!, supportFragmentManager
//                        .findFragmentById(R.id.fragment_list) as DeviceInfoListener
//                )
//
//                manager!!.discoverPeers(channel, object : WifiP2pManager.ActionListener {
//                    override fun onSuccess() {
//                        Log.d(TAG, "Discovery Initiated")
//                    }
//                    override fun onFailure(reasonCode: Int) {
//                        Log.d(TAG, "Discovery Failed:$reasonCode")
//                    }
//                })
//
//                manager?.requestPeers(
//                    channel,
//                    supportFragmentManager.findFragmentById(R.id.fragment_list) as PeerListListener
//                )
//                delay(5000)
//            }
//        }

        // tmp
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            Toast.makeText(
                this, "The device does have the NFC hardware.",
                Toast.LENGTH_SHORT
            ).show()
        }

        // create an intent with tag data and deliver to this activity
        pendingIntent = PendingIntent.getActivity( this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)


        // set an intent filter for all MIME data
        val ndefIntent = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            ndefIntent.addDataType("*/*")
            intentFilters = arrayOf(ndefIntent)
        } catch (e: Exception) {
            Log.e("TagDispatch", e.toString())
        }

        techLists = arrayOf(arrayOf(NfcF::class.java.name))
        Log.d(TAG, "NfcF.class.getName() = ${NfcF::class.java.name}, $techLists")
    }

    /** register the BroadcastReceiver with the intent values to be matched  */
    public override fun onResume() {
        super.onResume()
        Log.d(TAG, "start onResume()")
        receiver?.also { receiver ->
            registerReceiver(receiver, intentFilter)
        }
        registerReceiver(bluetoothController.receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        if (nfcAdapter != null) {
            Log.d(TAG,"run NFC enableForegroundDispatch")
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
        }
    }

    public override fun onPause() {
        super.onPause()
        receiver?.also { receiver ->
            unregisterReceiver(receiver)
        }
        unregisterReceiver(bluetoothController.receiver)

        if (nfcAdapter != null) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    fun resetData() {
//        val fragmentList = supportFragmentManager
//            .findFragmentById(R.id.fragment_list) as DeviceListFragment
//        val fragmentDetails = supportFragmentManager
//            .findFragmentById(R.id.fragment_detail) as DeviceDetailFragment
//        fragmentList.clearPeers()
//        fragmentDetails.resetViews()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "start onCreateOptionsMenu" )
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    @SuppressLint("MissingPermission")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.atn_wifi_settings -> {
                // Since this is the system wireless settings activity, it's
                // not going to send us a result. We will be notified by
                // WiFiDeviceBroadcastReceiver instead.
                // startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                return true
            }

            R.id.atn_create_service -> {
                if (item.title == resources.getString(R.string.create_wifi_direct_service_button)) {
                    wifiP2pController.registerP2pService()
                    item.title = resources.getString(R.string.remove_wifi_direct_service_button)
                } else {
                    wifiP2pController.unregisterP2pService()
                    item.title = resources.getString(R.string.create_wifi_direct_service_button)
                }
                return true
            }

            R.id.atn_discover_wifi_direct_services -> {
                if (item.title == resources.getString(R.string.start_discover_wifi_direct_services_button)) {
                    wifiP2pController.startDiscoverP2pService()
                    item.title = resources.getString(R.string.stop_discover_wifi_direct_services_button)
                } else {
                    wifiP2pController.stopDiscoverP2pServices()
                    item.title = resources.getString(R.string.start_discover_wifi_direct_services_button)
                }
                return true
            }

            R.id.atn_wifi_direct_peer_discovery -> {
                if (item.title == resources.getString(R.string.start_discover_wifi_peers_button)) {
                    wifiP2pController.startDiscoverP2pPeers()
                    item.title = resources.getString(R.string.stop_discover_wifi_peers_button)
                } else {
                    wifiP2pController.stopDiscoverP2pPeers()
                    item.title = resources.getString(R.string.start_discover_wifi_peers_button)
                }
                return true
            }

            R.id.atn_bluetooth_discovery -> {
                bluetoothController.startDiscovery()
                Toast.makeText(this@MainActivity,
                    R.string.discovery_bluetooth_initiated, Toast.LENGTH_SHORT).show()
                return true
            }

            R.id.atn_bluetooth_server -> {
                if (item.title == resources.getString(R.string.create_server_bluetooth_button)) {
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

            R.id.atn_discover_local_network_service -> {
                if (item.title == resources.getString(R.string.start_discover_lns_button)) {
                    lnsController.startDiscoverLocalNetworkServices()
                    item.title = resources.getString(R.string.stop_discover_lns_button)
                } else {
                    lnsController.stopDiscoverLocalNetworkServices()
                    item.title = resources.getString(R.string.start_discover_lns_button)
                }
                return true
            }

            R.id.atn_create_local_network_service -> {
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

            R.id.atn_send_nfc_message -> {

                return true
            }

            R.id.atn_discover_ble_devices -> {
                bluetoothController.startBleDiscovery()
                return true
            }

            R.id.atn_start_ble_advertising -> {
//                CoroutineScope(Dispatchers.IO).launch {
//                    bluetoothController.startBleAdvertising()
//                }
                return true
            }

            R.id.atn_start_ble_service -> {
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

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun requestPerms() {
        val requestCode = 0
        val permissions = arrayListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        ActivityCompat.requestPermissions(this@MainActivity,
            permissions.toTypedArray(), requestCode)
    }

    private fun handleSelectionIntent(intent : Intent) {
        val txFilePackDscr = DeviceDetailFragment.txFilePackDscr
        txFilePackDscr.clear()
        if (intent.clipData != null) {
            for (i in 0..<intent.clipData!!.itemCount) {
                val uri = intent.clipData!!.getItemAt(i).uri
                Log.d(TAG, "multiple URIs: = $uri")
                txFilePackDscr.add(getTxFileDescriptor(this, uri))
            }
        } else {
            val uri = intent.data
            Log.d(TAG, "single URI: = $uri")
            if (uri != null) {
                txFilePackDscr.add(getTxFileDescriptor(this, uri))
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BT = 1
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        Log.d(TAG, "onConnectionInfoAvailable(), info:\n$info")
        if (info == null || !info.groupFormed) {
            return
        }

        // The owner IP is now known.
        var view = findViewById<View>(R.id.group_owner) as TextView
        view.text = if (info.isGroupOwner) {
            resources.getString(R.string.group_owner_text)
        } else {
            resources.getString(R.string.not_group_owner_text)
        }
        view = findViewById<View>(R.id.device_info) as TextView
        view.text = "Group Owner IP - " + info.groupOwnerAddress.hostAddress

        findViewById<View>(R.id.btn_connect_dd).isEnabled = true
        findViewById<View>(R.id.btn_send_file_dd).isEnabled = true

//        // After the group negotiation, we assign the group owner as the file
//        // server. The file server is single threaded, single connection server
//        // socket.
//        if (info.groupFormed && info.isGroupOwner) {
//            mContentView!!.findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
//            (mContentView!!.findViewById<View>(R.id.status_text) as TextView).text = resources
//                .getString(R.string.server_text) + resources.getString(R.string.select_file_to_transmit)
//        } else if (info.groupFormed) {
//            // The other device acts as the client. In this case, we enable the
//            // get file button.
//            mContentView!!.findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
//            (mContentView!!.findViewById<View>(R.id.status_text) as TextView).text = resources
//                .getString(R.string.client_text) + resources.getString(R.string.select_file_to_transmit)
//        }

        // hide the connect button
//        mContentView!!.findViewById<View>(R.id.btn_connect_dd).visibility = View.GONE
//        mContentView!!.findViewById<View>(R.id.btn_disconnect).visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            wifiDataTransceiver.createSocket(info, txFilePackDscr)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) {
            return
        }
        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        txFilePackDscr.clear()
        if (data.data != null) {
            txFilePackDscr.add(getTxFileDescriptor(baseContext, data.data!!))
        } else if (data.clipData != null) {
            val clipData = data.clipData!!
            for (i in 0..< clipData.itemCount) {
                txFilePackDscr.add(getTxFileDescriptor(baseContext, clipData.getItemAt(i).uri))
            }
        }

        if (wifiDataTransceiver.isConnectionEstablished()) {
            CoroutineScope(Dispatchers.IO).launch {
                wifiDataTransceiver.sendData(txFilePackDscr)
                txFilePackDscr.clear()
            }
        } else if (bltDataTransceiver.isConnectionEstablished()) {
            Log.d(TAG, "bltDataTransceiver.sendData(txFilePackDscr)")
            CoroutineScope(Dispatchers.IO).launch {
                bltDataTransceiver.sendData(txFilePackDscr)
                txFilePackDscr.clear()
            }
        } else if (nsdDataTransceiver.isConnectionEstablished()) {
            Log.d(TAG, "nsdDataTransceiver.sendData(txFilePackDscr)")
            CoroutineScope(Dispatchers.IO).launch {
                nsdDataTransceiver.sendData(txFilePackDscr)
                txFilePackDscr.clear()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "start on onNewIntent()")
        Log.d(TAG, "intent.action = ${intent.action}")

        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.also { rawMessages ->
                val messages: List<NdefMessage> = rawMessages.map { it as NdefMessage }
                // Process the messages array.
                Log.d(TAG, "NDEF message = $messages")
            }

//            val msgs: List<String> = NFCUtils.getStringsFromNfcIntent(intent)
//
//            Toast.makeText(this, "Message received : " + msgs[0], Toast.LENGTH_LONG).show()
        }
    }
}

interface StatusUpdater {
    fun onDeviceInfoUpdate(newDeviceInfo: DeviceInfo?)
//    fun showLongToast(message: String)
}

interface NotificationInterface {
    suspend fun showProgressDialog(title: String, message: String, listener: DialogInterface.OnClickListener)
    suspend fun updateProgressDialog(message: String)
    suspend fun dismissProgressDialog()
    fun showToast(message: String)
    suspend fun showAlertDialog(message: String,
                                negativeListener: DialogInterface.OnClickListener,
                                positiveListener: DialogInterface.OnClickListener)
    suspend fun disconnect()
    fun onDeviceListUpdate(deviceList: List<DeviceInfo>)
}

