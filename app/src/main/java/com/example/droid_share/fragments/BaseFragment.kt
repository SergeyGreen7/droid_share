package com.example.droid_share.fragments

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.fragment.app.Fragment
import com.example.droid_share.FileManager
import com.example.droid_share.FileManager.Companion.getTxFileDescriptor
import com.example.droid_share.MainActivity
import com.example.droid_share.NotificationInterface
import com.example.droid_share.TxFilePackDescriptor
import com.example.droid_share.grid.DeviceInfo

abstract class BaseFragment : Fragment() {

    companion object {
        private const val TAG = "BaseFragment"
        private const val CHOOSE_FILE_RESULT_CODE = 20
    }

    protected var txFilePackDscr = TxFilePackDescriptor()
    private var uriList = mutableListOf<Uri>()

    protected var connectedDevices = HashMap<String, DeviceInfo>()

    protected lateinit var notifier: NotificationInterface

    protected var selectedDeviceInfo: DeviceInfo? = null

    abstract fun config(notifier: NotificationInterface)

    abstract fun resolveActivityResult(requestCode: Int, resultCode: Int, intent: Intent)

    abstract fun resolveNewIntent(intent: Intent)

    protected fun startActionGetContent() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE)
    }

    protected fun getUriFromIntent(intent: Intent) {
        Log.d(TAG, "start resolveIntent()")
        uriList.clear()
        txFilePackDscr.clear()

        if (intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)){
            return
        }
        Log.d(TAG, "ACTION_SEND")

        if (intent.clipData != null) {
            for (i in 0..<intent.clipData!!.itemCount) {
                val uri = intent.clipData!!.getItemAt(i).uri
                Log.d(TAG, "multiple URIs: = $uri")
                uriList.add(uri)
            }
        } else {
            if (intent.data != null) {
                Log.d(TAG, "single URI: = ${intent.data}")
                uriList.add(intent.data!!)
            }
        }
    }

    protected fun getFilePackDscr() {
        txFilePackDscr.clear()
        for (uri in uriList) {
            txFilePackDscr.add(FileManager.getTxFileDescriptor(requireContext(), uri))
        }
    }
}