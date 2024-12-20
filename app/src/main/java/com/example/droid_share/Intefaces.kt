package com.example.droid_share

import android.content.DialogInterface
import android.net.wifi.p2p.WifiP2pInfo
import com.example.droid_share.grid.DeviceInfo

interface StatusUpdater {
    fun onDeviceInfoUpdate(newDeviceInfo: DeviceInfo?)
}

interface NotificationInterface {
    suspend fun showProgressDialog(title: String, message: String, listener: DialogInterface.OnClickListener)
    suspend fun updateProgressDialog(message: String)
    suspend fun dismissProgressDialog()
    fun showToast(message: String)
    suspend fun showAlertDialog(message: String,
                                negativeListener: DialogInterface.OnClickListener,
                                positiveListener: DialogInterface.OnClickListener)
    fun dismissAlertDialog()
    fun cancelConnection()
    suspend fun disconnect()
    fun onDeviceListUpdate(deviceList: List<DeviceInfo>)
    fun onWifiP2pConnection(info: WifiP2pInfo)
}