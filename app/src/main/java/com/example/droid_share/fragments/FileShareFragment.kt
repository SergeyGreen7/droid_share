package com.example.droid_share.fragments

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
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
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.example.droid_share.MainActivity
import com.example.droid_share.NotificationInterface
import com.example.droid_share.R
import com.example.droid_share.StatusUpdater
import com.example.droid_share.WifiFragment
import com.example.droid_share.WifiFragment.Companion
import com.example.droid_share.connection.BluetoothController
import com.example.droid_share.connection.GattClient
import com.example.droid_share.connection.GattServer
import com.example.droid_share.connection.LnsController
import com.example.droid_share.connection.LnsScanner
import com.example.droid_share.connection.LnsService
import com.example.droid_share.data.BluetoothDataTransceiver
import com.example.droid_share.data.DataTransferState
import com.example.droid_share.data.NsdDataTransceiver
import com.example.droid_share.databinding.FragmentFileShareBinding
import com.example.droid_share.grid.DeviceGridView
import com.example.droid_share.grid.DeviceInfo
import com.example.droid_share.grid.InfoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ln

enum class FileSharingRole {
    FILE_TRANSMITTER,
    FILE_RECEIVER
}

class FileShareFragment(
    private val role: FileSharingRole
): BaseFragment() {

    companion object {
        private const val TAG = "FileShareFragment"
        const val REQUEST_ENABLE_BT = 1
        private const val REFERENCE_DATA = "1234567890"
        private const val FS_SERVICED_NAME_BASE = "fs_service"
    }

    private lateinit var binding: FragmentFileShareBinding

    lateinit var gridView: DeviceGridView

    private lateinit var gattClient: GattClient
    private lateinit var gattServer: GattServer

    private lateinit var lnsService: LnsService
    private lateinit var lnsScanner: LnsScanner

    private lateinit var lnsController: LnsController
    private lateinit var bluetoothController: BluetoothController

    private lateinit var nsdDataTransceiver: NsdDataTransceiver

    private val statusUpdater = object : StatusUpdater {
        override fun onDeviceInfoUpdate(newDeviceInfo: DeviceInfo?) {
            selectedDeviceInfo = newDeviceInfo

            if (selectedDeviceInfo == null) {
                binding.fileShareFragmentBtnSend.isEnabled = false
                return
            }

            val info = selectedDeviceInfo as DeviceInfo
            when (info.type){
                InfoType.BLE -> {
                    binding.fileShareFragmentBtnSend.isEnabled = true
                }

                else -> {
                    throw Exception("Unknown info type for File Share fragment")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "FileShareFragment, start onCreate()")
        super.onCreate(savedInstanceState)

        val sb = StringBuilder("MODEL: "+android.os.Build.MODEL
        +"\nDEVICE: "+android.os.Build.DEVICE
        +"\nBRAND: "+android.os.Build.BRAND
        +"\nDISPLAY: "+android.os.Build.DISPLAY
        +"\nBOARD: "+android.os.Build.BOARD
        +"\nHOST: "+android.os.Build.HOST
        +"\nMANUFACTURER: "+android.os.Build.MANUFACTURER
        +"\nPRODUCT: "+android.os.Build.PRODUCT);
        Log.d(TAG, "$sb")

        nsdDataTransceiver = NsdDataTransceiver(notifier)

        val nsdManager = requireActivity().getSystemService(NSD_SERVICE) as NsdManager
        lnsController = LnsController(nsdManager, notifier)

        lnsService = LnsService(nsdManager)
        lnsScanner = LnsScanner(nsdManager, notifier)

        val bluetoothManager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothController = BluetoothController(requireContext(), bluetoothManager, notifier)
        requireActivity().registerReceiver(bluetoothController.receiver, IntentFilter(
            BluetoothDevice.ACTION_FOUND)
        )

        gattClient = GattClient(requireContext(), bluetoothManager)
        gattServer = GattServer(requireContext(), bluetoothManager)

        if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(TAG, "current device doesn't support BLE functionality")
            requireActivity().finish();
        }

        if (!bluetoothController.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            ActivityCompat.startActivityForResult(requireActivity(), enableBtIntent, REQUEST_ENABLE_BT, null)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        Log.d(TAG, "FileShareFragment, start onCreateView()")
        binding = FragmentFileShareBinding.inflate(layoutInflater,container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "FileShareFragment, start onViewCreated()")
        super.onViewCreated(view, savedInstanceState)

        gridView = DeviceGridView(requireContext(), requireActivity()
            .findViewById(R.id.wifi_fragment_recycler_list), statusUpdater, 3)

        with (binding) {
            when (role) {
                FileSharingRole.FILE_TRANSMITTER -> {
                    fileShareFragmentBtnSend.isEnabled = false
                    fileShareFragmentBtnEnableBle.visibility = View.GONE
                }

                FileSharingRole.FILE_RECEIVER -> {
                    fileShareFragmentBtnSend.visibility = View.GONE
                    fileShareFragmentBtnCancel.visibility = View.GONE
                }
            }
            fileShareFragmentTextView.text = "Your name: ${bluetoothController.getName()}"
        }

        with (binding) {
            fileShareFragmentBtnSend.setOnClickListener {
                if (selectedDeviceInfo == null) {
                    return@setOnClickListener
                }

                bluetoothController.stopBleDiscovery()
                val info = selectedDeviceInfo as DeviceInfo

                CoroutineScope(Dispatchers.Main).launch {
                    notifier.showProgressDialog("Sending data", "Sending data 0.00 %") { dialog, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            Log.d(TAG, "run cancelDataTransmission()")
                            nsdDataTransceiver.cancelDataTransmission()
                        }
                        notifier.showToast("Data transmission is canceled")
                        dialog.dismiss()
                    }
                }

                val serviceName = "$FS_SERVICED_NAME_BASE-${(Math.random() * 10000).toInt().toString()}"
                gattClient.dataToSend = "$REFERENCE_DATA@$serviceName"
                gattClient.callbackOnDataSend = { flag:Boolean ->
                    if (flag) {
                        notifier.showToast("GATT data is sent")
                        gattClient.disconnect()

                        lnsScanner.callbackOnRefServiceFind = { serviceInfo: NsdServiceInfo ->
                            Log.d(TAG, "run callbackOnRefServiceFind")
                            lnsScanner.stopScan()
                            CoroutineScope(Dispatchers.IO).launch {
                                getFilePackDscr()
                                delay(1000)
                                nsdDataTransceiver.createSocket(false, serviceInfo, txFilePackDscr)
                            }
                        }

                        lnsScanner.scanForService(serviceName)
                    } else {
                        notifier.showToast("GATT data is failed to send")
                    }
                }

                gattClient.connect(info.bleScanResult!!)
//                    requireActivity().findViewById<View>(R.id.bluetooth_fragment_btn_send_file).isEnabled = true
            }

            fileShareFragmentBtnCancel.setOnClickListener {
                bluetoothController.stopBleDiscovery()
                lnsScanner.stopScan()
                nsdDataTransceiver.destroySocket()
                requireActivity().finish()
            }

            fileShareFragmentBtnEnableBle.setOnClickListener {
                val button = fileShareFragmentBtnEnableBle
                if (button.text == resources.getString(R.string.file_share_enable_ble_button)) {

                    gattServer.referenceData = REFERENCE_DATA
                    gattServer.callbackOnDataReception = { flag: Boolean, name:String ->
                        Log.d(TAG, "start callbackOnDataReception(), flag = $flag")

                        if (flag) {
                            lnsService.serviceName = name
                            Log.d(TAG, "run registerService(), serviceName = ${lnsService.serviceName}")
                            lnsService.registerService()
                            CoroutineScope(Dispatchers.IO).launch {
                                nsdDataTransceiver.createSocket(true, lnsController.getServiceInfo())
                            }
                        }
                    }

                    gattServer.startBleService()
                    button.text = resources.getString(R.string.file_share_disable_ble_button)
                } else {
                    gattServer.stopBleService()
                    lnsService.unregisterService()
                    button.text = resources.getString(R.string.file_share_enable_ble_button)
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
                Log.d(TAG, "FileShareFragment, start cancelConnection(), role = $role")
                nsdDataTransceiver.destroySocket()
                when (role) {
                    FileSharingRole.FILE_TRANSMITTER -> {
                        Log.d(TAG, "run bluetoothController.startBleDiscovery(true)")
                        bluetoothController.startBleDiscovery(true)
                        Log.d(TAG, "lnsScanner.stopScan()")
                        lnsScanner.stopScan()
                    }
                    FileSharingRole.FILE_RECEIVER -> {
                        Log.d(TAG, "lnsService.unregisterService()")
                        lnsService.unregisterService()
                    }
                }
                Log.d(TAG, "FileShareFragment, finish cancelConnection()")
            }

            override suspend fun disconnect() {
                Log.d(TAG, "FileShareFragment, disconnect()")
                bluetoothController.stopBleDiscovery()
                nsdDataTransceiver.destroySocket()
                lnsService.unregisterService()
                lnsScanner.stopScan()
            }

            override fun onDeviceListUpdate(deviceList: List<DeviceInfo>) {
                CoroutineScope(Dispatchers.Main).launch{
                    gridView.updateDataSet(deviceList)
                }
            }

            override fun onWifiP2pConnection(info: WifiP2pInfo) {
                // TODO("Not yet implemented")
            }
        }
    }

    override fun resolveActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {

    }

    override fun resolveNewIntent(intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)){
            return
        }
        getUriFromIntent(intent)
        bluetoothController.startBleDiscovery(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "FileShareFragment, start onDestroyView()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FileShareFragment, start onDestroy()")
    }

    override fun onDetach() {
        super.onDetach()
        Log.d(TAG, "FileShareFragment, start onDetach()")
    }

}