package com.example.droid_share

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.droid_share.data.FileAsyncTask
import com.example.droid_share.grid.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceDetailFragment : Fragment(), ConnectionInfoListener {

    private var mContentView: View? = null
    private var device: WifiP2pDevice? = null
    private var info: WifiP2pInfo? = null
    private var progressDialog: ProgressDialog? = null

    // tmp
    private lateinit var fileAsyncTask : FileAsyncTask

    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mContentView = inflater.inflate(R.layout.device_detail, null)
        mContentView!!.findViewById<View>(R.id.btn_connect_dd).setOnClickListener {
            if (progressDialog != null && progressDialog!!.isShowing) {
                progressDialog!!.dismiss()
            }
            progressDialog = ProgressDialog.show(activity, "Press back to cancel",
                "Connecting to :" + device!!.deviceAddress, true, true
            )
            // (activity as DeviceActionListener).connect(device!!.deviceAddress)
        }

//        mContentView!!.findViewById<View>(R.id.btn_disconnect).setOnClickListener {
//            (activity as DeviceActionListener).cancelConnect()
//            fileAsyncTask.shutdown()
//        }

        mContentView!!.findViewById<View>(R.id.btn_send_file_dd).setOnClickListener { _ ->
            // Allow user to pick an image from Gallery or other
            // registered apps
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE)
        }

        fileAsyncTask = FileAsyncTask(requireContext(), object: NotificationInterface{
                override suspend fun showProgressDialog(title: String, message: String, listener: DialogInterface.OnClickListener) {
                    withContext(Dispatchers.Main) {
                        progressDialog = ProgressDialog(activity)
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
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }

            override suspend fun showAlertDialog(message: String,
                                                 negativeListener: DialogInterface.OnClickListener,
                                                 positiveListener: DialogInterface.OnClickListener) {
                withContext(Dispatchers.Main) {
                    val builder = AlertDialog.Builder(context)
                        .setMessage(message)
                        .setNegativeButton("Dismiss", negativeListener)
                        .setPositiveButton("Accept", positiveListener)
                    val dialog = builder.create()
                    dialog.show()
                }
            }

            override suspend fun disconnect() {
                withContext(Dispatchers.Main) {
                    // (activity as DeviceActionListener).cancelConnect()
                    fileAsyncTask.shutdown()
                }
            }

            override fun onDeviceListUpdate(deviceList: List<DeviceInfo>) {
                TODO("Not yet implemented")
            }
        })

        return mContentView
    }

    @SuppressLint("Recycle")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data == null) {
            return
        }
        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        val uri = data.data
        val statusText = mContentView!!.findViewById<View>(R.id.status_text) as TextView

        statusText.text = "Sending: $uri"
        Log.d(TAG, "Intent----------- $uri")

        txFilePackDscr.clear()
        txFilePackDscr.add(getTxFileDescriptor(requireContext(),uri!!))
        CoroutineScope(Dispatchers.IO).launch {
            fileAsyncTask.initiateDataTransmission(txFilePackDscr)
            txFilePackDscr.clear()
        }
    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    fun showDetails(device: WifiP2pDevice) {
        this.device = device
        this.requireView().visibility = View.VISIBLE
        when (this.device!!.status) {
            WifiP2pDevice.CONNECTED,  WifiP2pDevice.INVITED -> {

                mContentView!!.findViewById<View>(R.id.btn_connect_dd).visibility = View.VISIBLE
//                mContentView!!.findViewById<View>(R.id.btn_disconnect).visibility = View.VISIBLE
                mContentView!!.findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
            }
            WifiP2pDevice.AVAILABLE, WifiP2pDevice.FAILED -> {
                mContentView!!.findViewById<View>(R.id.btn_connect_dd).visibility = View.VISIBLE
//                mContentView!!.findViewById<View>(R.id.btn_disconnect).visibility = View.GONE
                mContentView!!.findViewById<View>(R.id.btn_send_file_dd).visibility = View.GONE
            }
            WifiP2pDevice.UNAVAILABLE -> {}
        }
        var view = mContentView!!.findViewById<View>(R.id.device_address) as TextView
        view.text = device.deviceAddress
        view = mContentView!!.findViewById<View>(R.id.device_info) as TextView
        view.text = device.toString()

    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("SetTextI18n")
    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        hideProgressDialog()
        if (info == null || !info.groupFormed) {
            return
        }
        Log.d(TAG, "onConnectionInfoAvailable, info: $info")

        this.info = info
        this.requireView().visibility = View.VISIBLE

        // The owner IP is now known.
        var view = mContentView!!.findViewById<View>(R.id.group_owner) as TextView
        view.text = if (info.isGroupOwner) {
            resources.getString(R.string.group_owner_text)
        } else {
             resources.getString(R.string.not_group_owner_text)
        }

        // InetAddress from WifiP2pInfo struct.
        view = mContentView!!.findViewById<View>(R.id.device_info) as TextView
        view.text = "Group Owner IP - " + info.groupOwnerAddress.hostAddress

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            mContentView!!.findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
            (mContentView!!.findViewById<View>(R.id.status_text) as TextView).text = resources
                .getString(R.string.server_text) + resources.getString(R.string.select_file_to_transmit)
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            mContentView!!.findViewById<View>(R.id.btn_send_file_dd).visibility = View.VISIBLE
            (mContentView!!.findViewById<View>(R.id.status_text) as TextView).text = resources
                .getString(R.string.client_text) + resources.getString(R.string.select_file_to_transmit)
        }

        // hide the connect button
        mContentView!!.findViewById<View>(R.id.btn_connect_dd).visibility = View.GONE
//        mContentView!!.findViewById<View>(R.id.btn_disconnect).visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            fileAsyncTask.doInBackground(info, txFilePackDscr)
        }
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    fun resetViews() {
        mContentView!!.findViewById<View>(R.id.btn_connect_dd).visibility = View.VISIBLE
//        mContentView!!.findViewById<View>(R.id.btn_disconnect).visibility = View.GONE

        var view = mContentView!!.findViewById<View>(R.id.device_address) as TextView
        view.setText(R.string.empty)
        view = mContentView!!.findViewById<View>(R.id.device_info) as TextView
        view.setText(R.string.empty)
        view = mContentView!!.findViewById<View>(R.id.group_owner) as TextView
        view.setText(R.string.empty)
        view = mContentView!!.findViewById<View>(R.id.status_text) as TextView
        view.setText(R.string.empty)
        mContentView!!.findViewById<View>(R.id.btn_send_file_dd).visibility = View.GONE
        this.requireView().visibility = View.GONE

        fileAsyncTask.shutdown()
    }

    companion object {
        private val TAG = "DeviceDetailFragment"

        const val CHOOSE_FILE_RESULT_CODE = 20
        const val PORT_NUMBER = 8888
        const val CHUNK_SIZE = 1024

        var txFilePackDscr = TxFilePackDescriptor()

        @SuppressLint("Recycle", "Range")
        fun getTxFileDescriptor(context : Context, uri: Uri): TxFileDescriptor {
            val cursor = context.contentResolver?.query(uri, null, null, null, null)
            cursor?.moveToFirst()
            val fileName = cursor?.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            val fileSize = cursor?.getString(cursor.getColumnIndex(OpenableColumns.SIZE))?.toInt()
            cursor?.close()
            val inputStream = context.contentResolver?.openInputStream(uri)

            return TxFileDescriptor(fileName!!, fileSize!!, inputStream!!)
        }

        interface FileTransferStatus {
            suspend fun onProgressUpdate(text: String)
        }

//        fun copyFile(inputStream: InputStream, out: OutputStream): Boolean {
//            val buf = ByteArray(1024)
//            try {
//                var len = inputStream.read(buf)
//                while (len != -1) {
//                    out.write(buf, 0, len)
//                    len = inputStream.read(buf)
//
//                }
//                out.close()
//                inputStream.close()
//            } catch (e: IOException) {
//                Log.d(TAG, e.toString())
//                return false
//            }
//
//            return true
//        }
    }

    private fun hideProgressDialog() {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
    }


}