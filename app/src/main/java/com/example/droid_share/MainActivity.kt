package com.example.droid_share

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
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
import com.example.droid_share.connection.NsdController
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
    private var isWifiP2pEnabled = false
    private var progressDialog: ProgressDialog? = null

    private lateinit var p2pController: WifiP2pController
    private lateinit var bluetoothController: BluetoothController
    private lateinit var bluetoothClientServer: BluetoothClientServer

    // tmp
    var bluetoothServer: BluetoothServerSocket? = null
    var gattCharacteristics = mutableListOf<BluetoothGattCharacteristic>()

    private lateinit var wifiDataTransceiver: WifiDataTransceiver
    private lateinit var bltDataTransceiver: BluetoothDataTransceiver
    private lateinit var nsdDataTransceiver: NsdDataTransceiver

    private lateinit var nsdController: NsdController

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
                InfoType.WIFI -> {
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

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    fun setIsWifiP2pEnabled(isWifiP2pEnabled: Boolean) {
        this.isWifiP2pEnabled = isWifiP2pEnabled
    }

    @SuppressLint("MissingPermission", "InflateParams")
    public override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "start onCreate" )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        // setContentView(R.layout.activity_main)

        gridView = DeviceGridView(this, findViewById(R.id.recycler_list), statusUpdater, 3)
        detailView = LayoutInflater.from(this).inflate(R.layout.device_detail, null)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager?.initialize(this, mainLooper, null)
        p2pController = WifiP2pController(manager!!, channel!!)
        receiver = WifiP2pBroadcastReceiver(p2pController, this)

        bluetoothController = BluetoothController(this,
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager,
            gridView.gridUpdater)
        registerReceiver(bluetoothController.receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        bluetoothClientServer = BluetoothClientServer()

        nsdController = NsdController(getSystemService(NSD_SERVICE) as NsdManager,
            gridView.gridUpdater)


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

        val notifier = object: NotificationInterface{
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

            override suspend fun showToast(message: String) {
                withContext(Dispatchers.Main) {
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
                    // (activity as DeviceActionListener).cancelConnect()
                    // fileAsyncTask.shutdown()
                }
            }
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
                InfoType.WIFI -> {
                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {

                        p2pController.connectP2pDevice(info.deviceAddress, info.deviceName)
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                        // iew.isEnabled = false
                    } else {
                        wifiDataTransceiver.destroySocket()
                        p2pController.disconnect(deviceInfo?.wifiP2pDevice)
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
                    val callback = object : BluetoothGattCallback() {
                        override fun onPhyUpdate(
                            gatt: BluetoothGatt?,
                            txPhy: Int,
                            rxPhy: Int,
                            status: Int
                        ) {
                            Log.d(TAG, "BluetoothGattCallback(), onPhyUpdate")
                        }

                        override fun onPhyRead(
                            gatt: BluetoothGatt?,
                            txPhy: Int,
                            rxPhy: Int,
                            status: Int
                        ) {
                            Log.d(TAG, "BluetoothGattCallback(), onPhyRead")
                        }

                        override fun onConnectionStateChange(
                            gatt: BluetoothGatt?,
                            status: Int,
                            newState: Int
                        ) {
                            Log.d(TAG, "BluetoothGattCallback(), onConnectionStateChange"
                                    + "status $status, newState: $newState")

                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                // successfully connected to the GATT Server
                                // broadcastUpdate(ACTION_GATT_CONNECTED)
                                // connectionState = STATE_CONNECTED
                                // Attempts to discover services after successful connection.
                                gatt?.discoverServices()
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                // disconnected from the GATT Server
                                // broadcastUpdate(ACTION_GATT_DISCONNECTED)
                                // connectionState = STATE_DISCONNECTED
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                            Log.d(TAG, "BluetoothGattCallback(), onServicesDiscovered")
                            if (gatt == null) {
                                return
                            }

                            for (service in gatt.getServices()) {
                                Log.d(TAG, "Service: $service")
                                for (mCharacteristic in service.getCharacteristics()) {
                                    Log.i(TAG, "android    Found Characteristic: " + mCharacteristic.uuid.toString())
                                    gattCharacteristics.add(mCharacteristic)
                                }
                            }

                            // tmp
                            if (gattCharacteristics.isNotEmpty()) {
                                gatt.readCharacteristic(gattCharacteristics[0])
                                gattCharacteristics.removeAt(0)
                            }
                        }

                        override fun onCharacteristicRead(
                            gatt: BluetoothGatt?,
                            characteristic: BluetoothGattCharacteristic?,
                            status: Int
                        ) {
                            // onCharacteristicRead(gatt, characteristic, status)
                            Log.d(TAG, "BluetoothGattCallback(), onCharacteristicRead")
                            Log.d(TAG,"characteristic: $characteristic")
                            if (gattCharacteristics.isNotEmpty()) {
                                gatt?.readCharacteristic(gattCharacteristics[0])
                                gattCharacteristics.removeAt(0)
                            }
                        }

                        override fun onCharacteristicRead(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            value: ByteArray,
                            status: Int
                        ) {
                            // onCharacteristicRead(gatt, characteristic, status)
                            Log.d(TAG, "BluetoothGattCallback(), onCharacteristicRead")
                            Log.d(TAG,"characteristic: $characteristic")
                            if (gattCharacteristics.isNotEmpty()) {
                                gatt.readCharacteristic(gattCharacteristics[0])
                                gattCharacteristics.removeAt(0)
                            }
                        }

                        override fun onCharacteristicWrite(
                            gatt: BluetoothGatt?,
                            characteristic: BluetoothGattCharacteristic?,
                            status: Int
                        ) {
                        }

                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?
                        ) {
                        }

                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            value: ByteArray
                        ) {
                            onCharacteristicChanged(gatt, characteristic)
                        }

                        override fun onDescriptorRead(
                            gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int
                        ) {
                        }

                        override fun onDescriptorRead(
                            gatt: BluetoothGatt,
                            descriptor: BluetoothGattDescriptor,
                            status: Int,
                            value: ByteArray
                        ) {
                            onDescriptorRead(gatt, descriptor, status)
                        }

                        override fun onDescriptorWrite(
                            gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int
                        ) {
                        }

                        override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {}

                        override fun onReadRemoteRssi(
                            gatt: BluetoothGatt?,
                            rssi: Int,
                            status: Int
                        ) {
                        }

                        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {}

                        fun onConnectionUpdated(
                            gatt: BluetoothGatt?,
                            interval: Int,
                            latency: Int,
                            timeout: Int,
                            status: Int
                        ) {
                        }

                        override fun onServiceChanged(gatt: BluetoothGatt) {}

                        fun onSubrateChange(
                            gatt: BluetoothGatt?,
                            subrateFactor: Int,
                            latency: Int,
                            contNum: Int,
                            timeout: Int,
                            status: Int
                        ) {
                        }
                    }
                    val gatt :BluetoothGatt = info.scanResult!!.device.connectGatt(
                        this, false, callback, BluetoothDevice.TRANSPORT_LE)
                    Log.d(TAG, "gatt: $gatt")

                    // gatt.readCharacteristic(0)

                    val r = 0
                    Log.d(TAG, "r = $r")

                    // gatt.close()
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
        Log.d(TAG, "intent = $intent")

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
    }

    /** register the BroadcastReceiver with the intent values to be matched  */
    public override fun onResume() {
        super.onResume()
        receiver?.also { receiver ->
            registerReceiver(receiver, intentFilter)
        }
        registerReceiver(bluetoothController.receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    public override fun onPause() {
        super.onPause()
        receiver?.also { receiver ->
            unregisterReceiver(receiver)
        }
        unregisterReceiver(bluetoothController.receiver)
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
            R.id.atn_direct_enable -> {
                // Since this is the system wireless settings activity, it's
                // not going to send us a result. We will be notified by
                // WiFiDeviceBroadcastReceiver instead.
                // startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                return true
            }

            R.id.atn_create_service -> {
                p2pController.registerP2rService()
                return true
            }

            R.id.atn_discover_services -> {
                p2pController.discoverP2pService()
                return true
            }

            R.id.atn_direct_discovery -> {
                if (!isWifiP2pEnabled) {
                    Toast.makeText(this@MainActivity, R.string.p2p_off_warning,
                        Toast.LENGTH_SHORT).show()
                    return true
                }
//                val fragment = supportFragmentManager
//                    .findFragmentById(R.id.fragment_list) as DeviceListFragment
//                 fragment.onInitiateDiscovery()

                bluetoothController.stopDiscovery()
                p2pController.discoverP2pPeers()
                Toast.makeText(this@MainActivity, R.string.discovery_initiated, Toast.LENGTH_SHORT).show()
//                manager!!.discoverPeers(channel, object : WifiP2pManager.ActionListener {
//
//                    override fun onSuccess() {
//                        Log.d(TAG, "Discovery Initiated")
//                        Toast.makeText(this@MainActivity, "Discovery Initiated",
//                            Toast.LENGTH_SHORT).show()
//                    }
//
//                    override fun onFailure(reasonCode: Int) {
//                        Log.d(TAG, "Discovery Failed:$reasonCode")
//                        Toast.makeText(this@MainActivity, "Discovery Failed : $reasonCode",
//                            Toast.LENGTH_SHORT).show()
//                    }
//                })
                return true
            }

            R.id.atn_bluetooth_discovery -> {
                bluetoothController.startDiscovery()
                Toast.makeText(this@MainActivity,
                    R.string.discovery_bluetooth_initiated, Toast.LENGTH_SHORT).show()
                return true
            }

            R.id.atn_bluetooth_server -> {
                val name = "test_bluetooth_server"
                val uuid = UUID.fromString("94c838f1-8ef1-4f2d-8b97-9b94675d139a")
                Log.d(TAG, "run bluetooth server with uuid = $uuid")
                CoroutineScope(Dispatchers.IO).launch {
                    bltDataTransceiver.startServer(bluetoothController.createServer(name, uuid))
                }
                return true
            }

            R.id.atn_nsd_discovery -> {
                nsdController.discoveryNsdService()
                return true
            }

            R.id.atn_create_local_service -> {
                nsdController.registerNsdService()
                CoroutineScope(Dispatchers.IO).launch {
                    nsdDataTransceiver.createSocket(true, nsdController.getServiceInfo(), txFilePackDscr)
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
                CoroutineScope(Dispatchers.IO).launch {
                    bluetoothController.startBleAdvertising()
                }
                return true
            }

            R.id.atn_start_ble_service -> {
                CoroutineScope(Dispatchers.IO).launch {
                    bluetoothController.startBleService()
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
    suspend fun showToast(message: String)
    suspend fun showAlertDialog(message: String,
                                negativeListener: DialogInterface.OnClickListener,
                                positiveListener: DialogInterface.OnClickListener)
    suspend fun disconnect()
}

