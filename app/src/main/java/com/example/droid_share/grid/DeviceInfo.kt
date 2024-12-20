package com.example.droid_share.grid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pDevice

enum class InfoType {
    TEST,
    WIFI_DIRECT_PEER,
    WIFI_DIRECT_SERVICE,
    BLUETOOTH,
    NSD,
    BLE
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTED
}


class WifiDirectServiceInfo (
    val instanceName: String,
    val registrationType: String,
    val device: WifiP2pDevice,
) {}

class DeviceInfo {
    var type: InfoType = InfoType.TEST
    lateinit var deviceName: String
    lateinit var deviceInfo: String
    lateinit var deviceAddress: String
    lateinit var state: ConnectionState

    var wifiP2pDevice: WifiP2pDevice? = null
    var bluetoothDevice: BluetoothDevice? = null
    var nsdServiceInfo: NsdServiceInfo? = null
    var bleScanResult: ScanResult? = null

    init {
        deviceName      = "not defined name"
        deviceInfo      = "not defined info"
        deviceAddress   = "not defined info"
        bluetoothDevice = null // "00", BluetoothDevice.ADDRESS_TYPE_UNKNOWN)
        bluetoothDevice = null
        nsdServiceInfo  = null
        bleScanResult   = null
        state           = ConnectionState.DISCONNECTED
    }

    constructor(name: String, info: String) {
        deviceName      = name
        deviceInfo      = info
        deviceAddress   = info
        wifiP2pDevice   = null
        bluetoothDevice = null
        nsdServiceInfo  = null
        bleScanResult   = null
        state           = ConnectionState.DISCONNECTED
    }

    constructor(device: WifiP2pDevice) {
        type            = InfoType.WIFI_DIRECT_PEER
        deviceName      = device.deviceName
        deviceInfo      = getWifiP2pDeviceStatus(device.status)
        deviceAddress   = device.deviceAddress
        wifiP2pDevice   = device
        bluetoothDevice = null
        nsdServiceInfo  = null
        bleScanResult   = null
        state           = ConnectionState.DISCONNECTED
    }

    constructor(service: WifiDirectServiceInfo): this(service.device) {
        type        = InfoType.WIFI_DIRECT_SERVICE
        deviceName  = service.instanceName
        deviceInfo  = service.registrationType
    }

    @SuppressLint("MissingPermission")
    constructor(device: BluetoothDevice) {
        type            = InfoType.BLUETOOTH
        deviceName      = if (device.name != null) device.name else "null"
        deviceInfo      = device.address
        deviceAddress   = device.address
        wifiP2pDevice   = null
        bluetoothDevice = device
        nsdServiceInfo  = null
        bleScanResult   = null
        state           = ConnectionState.DISCONNECTED
    }

    constructor(info: NsdServiceInfo) {
        type            = InfoType.NSD
        deviceName      = if (info.serviceName != null) info.serviceName else "null"
        deviceInfo      = info.serviceType
        deviceAddress   = info.host.toString()
        wifiP2pDevice   = null
        bluetoothDevice = null
        nsdServiceInfo  = info
        bleScanResult   = null
        state           = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    constructor(result: ScanResult) {
        type            = InfoType.BLE
        deviceName      = if (result.device.name != null) result.device.name else "null"
        deviceInfo      = result.device.address
        deviceAddress   = result.device.address
        wifiP2pDevice   = null
        bluetoothDevice = null
        nsdServiceInfo  = null
        bleScanResult   = result
        state           = ConnectionState.DISCONNECTED
    }
}

private fun getWifiP2pDeviceStatus(deviceStatus: Int): String {
    return when (deviceStatus) {
        WifiP2pDevice.AVAILABLE     -> "Available"
        WifiP2pDevice.INVITED       -> "Invited"
        WifiP2pDevice.CONNECTED     -> "Connected"
        WifiP2pDevice.FAILED        -> "Failed"
        WifiP2pDevice.UNAVAILABLE   -> "Unavailable"
        else -> "Unknown"
    }
}
