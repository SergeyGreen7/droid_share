package com.example.droid_share.connection

import android.annotation.SuppressLint
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

class GattScanner (
    private val scanner: BluetoothLeScanner,
    private var notifier : NotificationInterface
) {

    companion object {
        private const val TAG = "GattScanner"
        private val BLE_SCAN_PERIOD = 5000L
    }

    private var isActive = false
    private var stopTimer: Job? = null
    private var devices = HashMap<String, ScanResult>()

    private val scanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result == null) {
                return
            }

            if (result.device.name != null) {
                // Log.d(TAG, "GattScanner, onScanResult()")
                // Log.d(TAG, "    result: $result")
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

            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(GattServer.GATT_SERVICE_UUID))
                .build()
            val filters = mutableListOf<ScanFilter>(scanFilter)
            val settings = ScanSettings.Builder()
                .build();
            scanner.startScan(filters, settings, scanCallback);
            isActive = true

            stopTimer = CoroutineScope(Dispatchers.IO).launch {
                delay(BLE_SCAN_PERIOD)
                stopScan()
            }
            CoroutineScope(Dispatchers.IO).launch {
                notifier.showToast("BLE services discovery started. " +
                        "Please wait for ${BLE_SCAN_PERIOD/1000} seconds ")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        Log.d(TAG, "GattScanner, stopScan()")
        if (stopTimer?.isActive == true) {
            stopTimer!!.cancel()
        }

        if (isActive) {
            scanner.stopScan(scanCallback)
            isActive = false
        }
        showDiscoveredDevices()
    }

    private fun showDiscoveredDevices() {
        CoroutineScope(Dispatchers.Main).launch {
            notifier.onDeviceListUpdate(devices.map{ DeviceInfo(it.value) })
        }
    }

}