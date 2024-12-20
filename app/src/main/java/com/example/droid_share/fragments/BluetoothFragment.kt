package com.example.droid_share.fragments

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Bundle
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
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.example.droid_share.MainActivity
import com.example.droid_share.NotificationInterface
import com.example.droid_share.R
import com.example.droid_share.StatusUpdater
import com.example.droid_share.connection.BluetoothController
import com.example.droid_share.connection.GattClient
import com.example.droid_share.connection.GattServer
import com.example.droid_share.data.BluetoothDataTransceiver
import com.example.droid_share.grid.DeviceGridView
import com.example.droid_share.grid.DeviceInfo
import com.example.droid_share.grid.InfoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class BluetoothFragment : BaseFragment() {

    companion object {
        private const val TAG = "BluetoothFragment"
        const val REQUEST_ENABLE_BT = 1
    }

    lateinit var gridView: DeviceGridView

    private lateinit var bluetoothController: BluetoothController
    private lateinit var gattClient: GattClient
    private lateinit var gattServer: GattServer

    private lateinit var bltDataTransceiver: BluetoothDataTransceiver

    private val statusUpdater = object : StatusUpdater {
        override fun onDeviceInfoUpdate(newDeviceInfo: DeviceInfo?) {
            selectedDeviceInfo = newDeviceInfo

            if (selectedDeviceInfo == null) {
                requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).visibility = View.GONE
                requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_connect).visibility = View.GONE
                requireActivity().findViewById<TextView>(R.id.bluetooth_fragment_device_address).text = resources.getString(
                    R.string.empty)
                requireActivity().findViewById<TextView>(R.id.bluetooth_fragment_device_info).text = resources.getString(
                    R.string.empty)
                requireActivity().findViewById<TextView>(R.id.bluetooth_fragment_group_owner).text = resources.getString(
                    R.string.empty)
                requireActivity().findViewById<TextView>(R.id.bluetooth_fragment_status_text).text = resources.getString(
                    R.string.empty)
                return
            }
            val info = selectedDeviceInfo as DeviceInfo

//            Log.d(TAG, "device info type: ${info.type}")
            when (info.type){
                InfoType.TEST -> {
                    requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_connect).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).visibility = View.VISIBLE
                }

                InfoType.BLUETOOTH -> {
                    requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_connect).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).visibility = View.VISIBLE
                }
                InfoType.BLE -> {
                    requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_connect).visibility = View.VISIBLE
                    requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).visibility = View.VISIBLE
                }

                else -> {
                    throw Exception("Unknown info type for bluetooth fragment")
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
                throw Exception("Unhandled callback happened")
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothController = BluetoothController(requireContext(), bluetoothManager, notifier)
        requireActivity().registerReceiver(bluetoothController.receiver, IntentFilter(
            BluetoothDevice.ACTION_FOUND)
        )

        if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(TAG, "current device doesn't support BLE functionality")
            requireActivity().finish();
        }
        gattClient = GattClient(requireContext(), bluetoothManager)
        gattServer = GattServer(requireContext(), bluetoothManager)

        if (!bluetoothController.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            ActivityCompat.startActivityForResult(requireActivity(), enableBtIntent, REQUEST_ENABLE_BT, null)
        }
        else {
//            bluetoothController.startDiscovery()
        }
        bltDataTransceiver = BluetoothDataTransceiver(notifier)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bluetooth, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
// setContentView(R.layout.activity_main)

        gridView = DeviceGridView(requireContext(), requireActivity()
            .findViewById(R.id.bluetooth_fragment_recycler_list), statusUpdater, 3)
//        detailView = LayoutInflater.from(requireContext())
//            .inflate(R.layout.device_detail, null)

        requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_connect).setOnClickListener { view ->
            if (selectedDeviceInfo == null) {
                return@setOnClickListener
            }
            val info = selectedDeviceInfo as DeviceInfo

            when (info.type)
            {
                InfoType.TEST -> {

                }

                InfoType.BLUETOOTH -> {
                    val uuid = UUID.fromString("94c838f1-8ef1-4f2d-8b97-9b94675d139a")

                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        Log.d(TAG, "uuid: $uuid")
                        CoroutineScope(Dispatchers.IO).launch {
                            bltDataTransceiver.startClient(info.bluetoothDevice!!, uuid)
                        }
                        requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).isEnabled = true
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        bltDataTransceiver.destroySocket()
                        requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }

                InfoType.BLE -> {
                    Log.d(TAG, "ble info: ${info.bleScanResult!!}")

                    if ((view as Button).text == resources.getString(R.string.connect_peer_button)) {
                        gattClient.connect(info.bleScanResult!!)
                        requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).isEnabled = true
                        (view as Button).text = resources.getString(R.string.disconnect_peer_button)
                    } else {
                        gattClient.disconnect()
                        requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).isEnabled = false
                        (view as Button).text = resources.getString(R.string.connect_peer_button)
                    }
                }

                else -> {
                    throw Exception("Unknown info type for bluetooth fragment")
                }
            }

        }

        requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).isEnabled = false
        requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).setOnClickListener {
//            val intent = Intent(Intent.ACTION_GET_CONTENT)
//            intent.type = "*/*"
//            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
//            startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE)
            startActionGetContent()
        }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.bluetooth_menu, menu)
            }
            override fun onMenuItemSelected(item: MenuItem): Boolean {
                // Handle the menu selection

                when (item.itemId) {
                    R.id.bluetooth_menu_item_bluetooth_settings -> {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        return true
                    }

                    R.id.bluetooth_menu_item_bluetooth_discovery -> {
                        bluetoothController.startDiscovery()
                        Toast.makeText(requireActivity(),
                            R.string.discovery_bluetooth_initiated, Toast.LENGTH_SHORT).show()
                        return true
                    }

                    R.id.bluetooth_menu_item_bluetooth_server -> {
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

                    R.id.bluetooth_menu_item_discover_ble_devices -> {
                        bluetoothController.startBleDiscovery()
                        return true
                    }

                    R.id.bluetooth_menu_item_start_ble_advertising -> {
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
        requireActivity().registerReceiver(bluetoothController.receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "start onPause()")
        requireActivity().unregisterReceiver(bluetoothController.receiver)
    }

    override fun resolveActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
        Log.d(TAG, "BluetoothFragment, start resolveActivityResult()")
        Log.d(TAG, "intent.action = ${intent.action}")
        resolveIntent(intent)
    }

    override fun resolveNewIntent(intent: Intent) {
        Log.d(TAG, "BluetoothFragment, start on onNewIntent()")
        Log.d(TAG, "intent.action = ${intent.action}")
        resolveIntent(intent)
    }

    fun resolveIntent(intent: Intent) {
        Log.d(TAG, "BluetoothFragment, start on resolveIntent()")
        Log.d(TAG, "intent.action = ${intent.action}")

        getUriFromIntent(intent)
        getFilePackDscr()

        if (txFilePackDscr.isEmpty()) {
            return
        }

        Log.d(TAG, "bltDataTransceiver.isConnectionEstablished() " +
                "= ${bltDataTransceiver.isConnectionEstablished()}")
        if (bltDataTransceiver.isConnectionEstablished()) {
            bltDataTransceiver.sendData(txFilePackDscr)
        }
        txFilePackDscr.clear()
    }

}