package com.example.droid_share.fragments

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pInfo
import android.nfc.NfcAdapter
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
import android.widget.Toast
import androidx.core.view.MenuProvider
import com.example.droid_share.MainActivity
import com.example.droid_share.NotificationInterface
import com.example.droid_share.OldFragment
import com.example.droid_share.OldFragment.Companion
import com.example.droid_share.R
import com.example.droid_share.connection.NfcController
import com.example.droid_share.connection.nfc.NfcHostApduService
import com.example.droid_share.grid.DeviceInfo
import com.example.droid_share.grid.InfoType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class NfcFragment : BaseFragment() {

    companion object {
        private const val TAG = "NfcFragment"
    }

    private var nfcController: NfcController? = null
    var nfcAdapter: NfcAdapter? = null

    override fun config(notifier: NotificationInterface) {
        this.notifier = object: NotificationInterface {
            override suspend fun showProgressDialog(title: String, message: String,
                                                    listener: DialogInterface.OnClickListener) {
                throw Exception("Unhandled callback happened")
            }

            override suspend fun updateProgressDialog(message: String) {
                throw Exception("Unhandled callback happened")
            }

            override suspend fun dismissProgressDialog() {
                throw Exception("Unhandled callback happened")
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
                throw Exception("Unhandled callback happened")
            }

            override fun onDeviceListUpdate(deviceList: List<DeviceInfo>) {
                throw Exception("Unhandled callback happened")
            }

            override fun onWifiP2pConnection(info: WifiP2pInfo) {
                throw Exception("Unhandled callback happened")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (requireActivity().packageManager?.hasSystemFeature(PackageManager.FEATURE_NFC) == true) {
            nfcController = NfcController(requireContext(), NfcAdapter.getDefaultAdapter(requireContext()), notifier)
            nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())
        } else {
            notifier.showToast("The device doesn't have the NFC hardware")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_nfc, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().findViewById<View>(R.id.fragment_nfc_text_input_layout).setOnClickListener {
            val data = requireActivity().findViewById<com.google.android.material.textfield
                .TextInputEditText>(R.id.fragment_nfc_text_input_edit_text).text.toString()
            if (data.isEmpty()) {
                notifier.showToast("Enter data to send")
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                val message = "Test data transmitted over NFC"
                val nfcIntent = Intent(requireActivity(), NfcHostApduService::class.java)
                nfcIntent.putExtra("ndefMessage", message)
                notifier.showToast("Message is send as NDEF message: '$message'")
                val res = requireActivity().startService(nfcIntent)
                Log.d(TAG, "res on 'startService' = $res")
            }

        }
    }

    override fun resolveActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
        Log.d(TAG, "BluetoothFragment, start resolveActivityResult()")
        Log.d(TAG, "intent.action = ${intent.action}")
        // resolveIntent(intent)
    }

    override fun resolveNewIntent(intent: Intent) {
        Log.d(TAG, "BluetoothFragment, start on onNewIntent()")
        Log.d(TAG, "intent.action = ${intent.action}")
        // resolveIntent(intent)
    }

    fun resolveIntent(intent: Intent) {
        Log.d(TAG, "BluetoothFragment, start on resolveIntent()")
        Log.d(TAG, "intent.action = ${intent.action}")

        getUriFromIntent(intent)
        getFilePackDscr()

        if (txFilePackDscr.isEmpty()) {
            return
        }
    }
}