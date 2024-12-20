package com.example.droid_share

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.droid_share.fragments.BaseFragment
import com.example.droid_share.fragments.BluetoothFragment
import com.example.droid_share.fragments.FileShareFragment
import com.example.droid_share.fragments.FileSharingRole
import com.example.droid_share.fragments.NfcFragment
import com.example.droid_share.grid.DeviceInfo
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActiveFragment {
    OLD,
    WIFI,
    BLUETOOTH,
    NFC,
    FILE_SHARE
}

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BT = 1
        // var txFilePackDscr = TxFilePackDescriptor()
    }

    private var startIntentResolved = false
    private var progressDialog: ProgressDialog? = null
    private var alertDialog: AlertDialog? = null

    private var activeFragment = ActiveFragment.FILE_SHARE
    private lateinit var oldFragment: OldFragment
    private lateinit var wifiFragment: WifiFragment
    private lateinit var bluetoothFragment: BluetoothFragment
    private lateinit var nfcFragment: NfcFragment
    private lateinit var fsFragment: FileShareFragment

    private val channel = Channel<Job>(capacity = Channel.UNLIMITED).apply {
        CoroutineScope(Dispatchers.Main).launch {
            consumeEach { it.join() }
        }
    }


    @SuppressLint("MissingPermission", "InflateParams", "UnspecifiedImmutableFlag")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_v2)

        oldFragment = OldFragment()
        oldFragment.config(notifier)

        wifiFragment = WifiFragment()
        wifiFragment.config(notifier)

        bluetoothFragment = BluetoothFragment()
        bluetoothFragment.config(notifier)

        nfcFragment = NfcFragment()
        nfcFragment.config(notifier)

        fsFragment = FileShareFragment(getFileSharingRole(intent))
        fsFragment.config(notifier)

        findViewById<BottomNavigationView>(R.id.bottom_navigation)
            .setOnItemSelectedListener(bottomMenuListener)
        findViewById<BottomNavigationView>(R.id.bottom_navigation)
            .visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, getActiveFragment())
            .commit()

        requestPerms()
    }

    override fun onStart() {
        super.onStart()

        if (!startIntentResolved) {
            onNewIntent(intent)
            startIntentResolved = true
        }
    }

    private val bottomMenuListener = NavigationBarView.OnItemSelectedListener { item: MenuItem ->
        val itemId = item.itemId
        when (itemId) {
            R.id.item_wifi_direct_botm -> {
                activeFragment = ActiveFragment.WIFI
            }
            R.id.item_bluetooth_botm -> {
                activeFragment = ActiveFragment.BLUETOOTH
            }
            R.id.item_nfc_botm -> {
                activeFragment = ActiveFragment.NFC
            }
        }
        updateFragment()
        true
    }

    private fun updateFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, getActiveFragment()).commit()
    }

    private val notifier = object: NotificationInterface{
        override suspend fun showProgressDialog(title: String, message: String, listener: DialogInterface.OnClickListener) {
            withContext(Dispatchers.Main) {
                progressDialog = ProgressDialog(this@MainActivity)
                with(progressDialog!!) {
                    setTitle(title)
                    setMessage(message)
                    setCancelable(true)
                    setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", listener)
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
            channel.trySend(
                CoroutineScope(Dispatchers.Main).launch {
                    val builder = AlertDialog.Builder(this@MainActivity)
                        .setMessage(message)
                        .setNegativeButton("Dismiss", negativeListener)
                        .setPositiveButton("Accept", positiveListener)
                    alertDialog = builder.create()
                    alertDialog?.show()
                }
            )
        }

        override fun dismissAlertDialog() {
            channel.trySend(
                CoroutineScope(Dispatchers.Main).launch {
                    alertDialog?.dismiss()
                }
            )
        }

        override fun cancelConnection() {
            TODO("Not yet implemented")
        }

        override suspend fun disconnect() {
            // TODO("Not yet implemented")
        }

        override fun onDeviceListUpdate(deviceList: List<DeviceInfo>) {
            TODO("Not yet implemented")
        }

        override fun onWifiP2pConnection(info: WifiP2pInfo) {
            TODO("Not yet implemented")
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "MainActivity, start onActivityResult()")
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) {
            Log.d(TAG, "MainActivity, onActivityResult(). data is null")
            return
        }
        data.action = Intent.ACTION_SEND

        getActiveFragment().resolveActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        Log.d(TAG, "MainActivity, start on onNewIntent()")
        Log.d(TAG, "MainActivity, intent.action = ${intent.action}")

        getActiveFragment().resolveNewIntent(intent)
    }

    private fun getActiveFragment(): BaseFragment {
        return when (activeFragment) {
            ActiveFragment.OLD -> {
                oldFragment
            }
            ActiveFragment.WIFI -> {
                wifiFragment
            }
            ActiveFragment.BLUETOOTH -> {
                bluetoothFragment
            }
            ActiveFragment.NFC -> {
                nfcFragment
            }
            ActiveFragment.FILE_SHARE -> {
                fsFragment
            }
        }
    }

    private fun getFileSharingRole(intent: Intent) : FileSharingRole {
        return if (intent.action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            FileSharingRole.FILE_TRANSMITTER
        } else {
            FileSharingRole.FILE_RECEIVER
        }
    }

}