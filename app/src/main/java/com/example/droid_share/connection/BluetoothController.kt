package com.example.droid_share.connection

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.os.Parcelable
import android.util.Log
import com.example.droid_share.grid.DeviceInfo
import com.example.droid_share.grid.GridUpdater
import kotlinx.coroutines.delay
import java.util.UUID


class BluetoothController(
    private val context: Context,
    private val manager : BluetoothManager,
    private val gridUpdater: GridUpdater
)
{
    private lateinit var advertiser: BluetoothLeAdvertiser
    private var currentAdvertisingSet: AdvertisingSet? = null

    private var devices = HashMap<String, BluetoothDevice>()
    private var leDevices = HashMap<String, ScanResult>()

    companion object {
        private const val TAG = "BluetoothController"

        private val BLE_SCAN_PERIOD = 10000L

        val GATT_SERVICE_UUID = UUID.fromString("5116c812-ad72-449f-a503-f8662bc21cde")
        val GATT_CHARACT_UUID = UUID.fromString("330fb1d7-afb6-4b00-b5da-3b0feeef9816")
    }

    private var bluetoothSupport = false
    lateinit var receiver: BroadcastReceiver
    private lateinit var adapter : BluetoothAdapter
    private lateinit var bleScanner : BluetoothLeScanner

    // tmp
    private lateinit var gattServer: BluetoothGattServer

    // private val leDeviceListAdapter = LeDeviceListAdapter()
    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result != null) {
                if (result.device.name != null) {
                    // Log.d(TAG, "leScanCallback, onScanResult")
                    // Log.d(TAG, "    result: $result")
                    leDevices[result.device.address] = result
                }
            }
            gridUpdater.onDeviceListUpdate(leDevices.map{DeviceInfo(it.value)})
        }

        override fun onBatchScanResults(results: List<android.bluetooth.le.ScanResult?>?) {
            Log.d(TAG, "leScanCallback, onBatchScanResults")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d(TAG, "leScanCallback, onScanFailed")
        }
//        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            super.onScanResult(callbackType, result)
//            // leDeviceListAdapter.addDevice(result.device)
//            // leDeviceListAdapter.notifyDataSetChanged()
//        }
    }

    init {
        bluetoothSupport = manager.adapter != null
        if (bluetoothSupport) {
            adapter = manager.adapter
            bleScanner = adapter.bluetoothLeScanner

            Log.d(TAG, "isLe2MPhySupported: ${adapter.isLe2MPhySupported}")
            Log.d(TAG, "isLeCodedPhySupported: ${adapter.isLeCodedPhySupported}")
            Log.d(TAG, "isLeExtendedAdvertisingSupported: ${adapter.isLeExtendedAdvertisingSupported}")
            Log.d(TAG, "isLePeriodicAdvertisingSupported: ${adapter.isLePeriodicAdvertisingSupported}")

            advertiser = adapter.bluetoothLeAdvertiser

        } else {
            Log.d(TAG, "Device doesn't support Bluetooth")
        }

        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                    Log.d(TAG, "BluetoothDevice.ACTION_FOUND")
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device = intent.getParcelableExtra<Parcelable>(
                        BluetoothDevice.EXTRA_DEVICE)!! as BluetoothDevice

                    if (device.name != null) {
                        Log.d(TAG, " found new bluetooth device:")
                        Log.d(TAG, "    name:           ${device.name}");
                        Log.d(TAG, "    address:        ${device.address}")
                        Log.d(TAG, "    type:           ${device.type}")
                        Log.d(TAG, "    class:          ${device.bluetoothClass}")
                        Log.d(TAG, "    alias:          ${device.alias}")
                        Log.d(TAG, "    bondState:      ${device.bondState}")
                        Log.d(TAG, "    uuids:          ${device.uuids}")

                        devices[device.address] = device
                    }

                    gridUpdater.onDeviceListUpdate(devices.map{DeviceInfo(it.value)})
                }
            }

        }
    }

    fun isBluetoothEnabled(): Boolean {
        return adapter.isEnabled
    }

    @SuppressLint("MissingPermission")
    fun printPairedDevices() {
        val pairedDevices: Set<BluetoothDevice>? = adapter.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            Log.d(TAG, "This device doesn't have any paired bluetooth devices")
        }
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
            Log.d(TAG, "Bluetooth device: $deviceName - $deviceHardwareAddress")
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!adapter.startDiscovery()) {
            Log.d(TAG, "Bluetooth discovery procedure can not be initiated.")
        } else {
            Log.d(TAG, "Bluetooth discovery procedure is initiated.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (!adapter.cancelDiscovery()) {
            Log.d(TAG, "Bluetooth discovery procedure can not be canceled.")
        } else {
            Log.d(TAG, "Bluetooth discovery procedure is canceled.")
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleDiscovery() {
        bleScanner.startScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopBleDiscovery() {
        bleScanner.stopScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun createServer(name: String, uuid: UUID) : BluetoothServerSocket {
        return adapter.listenUsingInsecureRfcommWithServiceRecord(name, uuid)
    }

    @SuppressLint("MissingPermission", "NewApi")
    suspend fun startBleAdvertising() {
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(true) // True by default, but set here as a reminder.
            .setConnectable(true)
            .setScannable(true)
            // .setIncludeTxPower(true)
            .setAnonymous(false)
            .setPrimaryPhy(1)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            .build()

        Log.d(TAG, "parameters: $parameters")

        val uuid = UUID.randomUUID()
        Log.d(TAG, "random UUID: $uuid")

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
//            .addServiceData(ParcelUuid(uuid), "data1".toByteArray(Charsets.US_ASCII))
//            .addTransportDiscoveryData(TransportDiscoveryData("transport discovery data: HO-HO-HO".toByteArray(Charsets.US_ASCII)))
            .addManufacturerData(42, "data2".toByteArray(Charsets.US_ASCII))
            .build()

        Log.d(TAG, "data: $data")

        val callback: AdvertisingSetCallback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet,
                txPower: Int,
                status: Int
            ) {
                Log.d(TAG, "AdvertisingSetCallback(), onAdvertisingSetStarted: " +
                        "txPower:" + txPower + " , status: " + status)
                currentAdvertisingSet = advertisingSet
            }

            override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet, status: Int) {
                Log.d(TAG, "AdvertisingSetCallback(), onAdvertisingDataSet :status:$status")
            }

            override fun onScanResponseDataSet(advertisingSet: AdvertisingSet, status: Int) {
                Log.d(TAG, "AdvertisingSetCallback(), onScanResponseDataSet: status:$status")
            }

            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet) {
                Log.d(TAG, "AdvertisingSetCallback(), onAdvertisingSetStopped")
            }

            override fun onAdvertisingEnabled(
                advertisingSet: AdvertisingSet?,
                enable: Boolean,
                status: Int
            ) {
                Log.d(TAG, "AdvertisingSetCallback(), onAdvertisingEnabled")
            }

//            override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet?, status: Int) {
//                Log.d(TAG, "AdvertisingSetCallback(), onAdvertisingDataSet")
//            }

            override fun onAdvertisingParametersUpdated(
                advertisingSet: AdvertisingSet?, txPower: Int, status: Int
            ) {
                Log.d(TAG, "AdvertisingSetCallback(), onAdvertisingParametersUpdated")
            }

            override fun onPeriodicAdvertisingParametersUpdated(
                advertisingSet: AdvertisingSet?,
                status: Int
            ) {
                Log.d(TAG, "AdvertisingSetCallback(), onPeriodicAdvertisingParametersUpdated")
            }

            override fun onPeriodicAdvertisingDataSet(
                advertisingSet: AdvertisingSet?,
                status: Int
            ) {
                Log.d(TAG, "AdvertisingSetCallback(), onPeriodicAdvertisingDataSet")
            }

            override fun onPeriodicAdvertisingEnabled(
                advertisingSet: AdvertisingSet?, enable: Boolean, status: Int
            ) {
                Log.d(TAG, "AdvertisingSetCallback(), onPeriodicAdvertisingEnabled")
            }

            fun onOwnAddressRead(
                advertisingSet: AdvertisingSet?,
                addressType: Int,
                address: String?
            ) {
                Log.d(TAG, "AdvertisingSetCallback(), onOwnAddressRead")
            }
        }

        advertiser.startAdvertisingSet(parameters, data, null, null, null, callback)

        delay(5000)
        // After onAdvertisingSetStarted callback is called, you can modify the
        // advertising data and scan response data:
        currentAdvertisingSet?.setAdvertisingData(
            AdvertiseData.Builder().setIncludeDeviceName
                (true).setIncludeTxPowerLevel(true).build()
        )

        delay(5000)
        // Wait for onAdvertisingDataSet callback...
        currentAdvertisingSet?.setScanResponseData(
            AdvertiseData.Builder().addServiceUuid(ParcelUuid(UUID.randomUUID())).build()
        )


        // Wait for onScanResponseDataSet callback...

        // When done with the advertising:
        // advertiser.stopAdvertisingSet(callback)
        delay(100000)
    }

    @SuppressLint("MissingPermission")
    fun startBleService() {

        val gattServerCallback = GattServerCallback()
        gattServer = manager.openGattServer(context, gattServerCallback)
        setupServer()
        startGattServerAdvertising()
    }

    @SuppressLint("MissingPermission")
    fun setupServer() {
        val gattService = BluetoothGattService(GATT_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val writeCharacteristic = BluetoothGattCharacteristic(
            GATT_CHARACT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE)
        gattService.addCharacteristic(writeCharacteristic)

        gattServer.addService(gattService)
    }

    @SuppressLint("MissingPermission")
    fun startGattServerAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val parcelUuid = ParcelUuid(GATT_SERVICE_UUID)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(parcelUuid)
            .build()

        val mAdvertiseCallback: AdvertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(TAG, "Peripheral advertising started.")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.d(TAG, "Peripheral advertising failed: $errorCode")
            }
        }
        advertiser.startAdvertising(settings, data, null, mAdvertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopBleService() {
        // mBluetoothLeAdvertiser
        gattServer.close()
    }

}

class GattServerCallback : BluetoothGattServerCallback() {

}

class BleService : Service() {
    companion object {
        private const val TAG = "BleService"
    }

    private val binder = LocalBinder()
    private var adapter : BluetoothAdapter? = null

    fun initialize(): Boolean {
        adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Log.d(TAG, "Unable to obtain bluetooth adapter")
            return false
        }
        return true
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }
    inner class LocalBinder : Binder() {
        fun getService() : BleService {
            return this@BleService
        }
    }
}