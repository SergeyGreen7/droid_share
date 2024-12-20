package com.example.droid_share.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import com.example.droid_share.NotificationInterface
import com.example.droid_share.grid.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GattScanner (
    private val scanner: BluetoothLeScanner,
    private var notifier : NotificationInterface
) {

    companion object {
        private const val TAG = "GattScanner"
        private val BLE_SCAN_PERIOD_SINGLE = 5000L
        private val BLE_SCAN_PERIOD_MULTIPLE = 1000L
        private val LIST_UPDATE_TIME = 5000L
        private val NUM_SCAN_PERIODS = 1000
    }

    private var isActive = false
    private var stopTimer: Job? = null
    private var periodicScan: Job? = null
    private var devices = HashMap<String, ScanResult>()

    private val scanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result == null) {
                return
            }

            Log.d(TAG,"onScanResult: ${result}")
            if (result.device.name != null) {
                 Log.d(TAG, "GattScanner, onScanResult()")
                 Log.d(TAG, "    result: $result")
                devices[result.device.address] = result
            }

//            gridUpdater.onDeviceListUpdate(devices.map{ DeviceInfo(it.value) })
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: List<ScanResult?>?) {
            Log.d(TAG, "GattScanner, onBatchScanResults()")
            if (results == null) {
                return
            }

            for (result in results) {
                if (result == null) {
                    continue
                }
                Log.d(TAG,"onBatchScanResults: ${result}")
                if (result.device.name != null) {
                    devices[result.device.address] = result
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d(TAG, "GattScanner, onScanFailed(), errorCode = $errorCode")
        }
//        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            super.onScanResult(callbackType, result)
//            // leDeviceListAdapter.addDevice(result.device)
//            // leDeviceListAdapter.notifyDataSetChanged()
//        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        Log.d(TAG, "GattScanner, startScan()")
        if (!isActive) {

            devices.clear()
            showDiscoveredDevices()

            configureAndRunScanner()
            isActive = true

            stopTimer = CoroutineScope(Dispatchers.IO).launch {
                delay(BLE_SCAN_PERIOD_SINGLE)
                stopScan()
            }
            CoroutineScope(Dispatchers.IO).launch {
                notifier.showToast("BLE services discovery started. " +
                        "Please wait for ${BLE_SCAN_PERIOD_SINGLE/1000} seconds ")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanPeriodic() {
        Log.d(TAG, "GattScanner, startScan()")
        if (!isActive) {

            devices.clear()

            configureAndRunScanner()
            isActive = true

            periodicScan = CoroutineScope(Dispatchers.IO).launch {
                var cntr = 0
                for (i in 0..NUM_SCAN_PERIODS) {
                    delay(BLE_SCAN_PERIOD_MULTIPLE)
                    Log.d(TAG, "startScanPeriodic, show found BLE nodes")
                    if (cntr++ % 5 == 0) {
                        withContext(Dispatchers.Main) {
                            notifier.onDeviceListUpdate(devices.map{ DeviceInfo(it.value) })
                            devices.clear()
                        }
                    }
                }
            }
//            CoroutineScope(Dispatchers.IO).launch {
//                notifier.showToast("BLE services discovery started. " +
//                        "Please wait for ${BLE_SCAN_PERIOD/1000} seconds ")
//            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        Log.d(TAG, "GattScanner, stopScan()")
        stopTimer?.cancel()
        periodicScan?.cancel()

        if (isActive) {
            scanner.stopScan(scanCallback)
            isActive = false
        }
        showDiscoveredDevices()
    }

    @SuppressLint("MissingPermission")
    private fun configureAndRunScanner() {
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(GattServer.GATT_SERVICE_UUID))
            .build()
        val filters = mutableListOf<ScanFilter>(scanFilter)
//        val filters = mutableListOf<ScanFilter>()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();
        scanner.startScan(filters, settings, scanCallback);
    }

    private fun showDiscoveredDevices() {
        CoroutineScope(Dispatchers.Main).launch {
            notifier.onDeviceListUpdate(devices.map{ DeviceInfo(it.value) })
        }
    }

}