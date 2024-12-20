package com.example.droid_share.data

import android.util.Log
import com.example.droid_share.TxFilePackDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

open class BaseDataTransceiver {

    companion object {
        private const val TAG = "BaseDataTransceiver"
    }

    protected var socketJob: Job? = null
    protected var txJob: Job? = null
    protected var rxJob: Job? = null

    protected var dataTransceiver: DataTransceiver? = null

    protected var txFilePack: TxFilePackDescriptor? = null

    protected fun isJobActive(job: Job?) : Boolean {
        return (job != null) && job.isActive
    }

    fun sendData(filePack: TxFilePackDescriptor) {
        CoroutineScope(Dispatchers.IO).launch {
            dataTransceiver!!.initiateDataTransmission(filePack)
        }
    }

    protected fun stopActiveJobs() {
        if (isJobActive(socketJob)) {
            Log.d(TAG, "stopActiveJobs, stop socketJob")
            socketJob!!.cancel()
        }
        if (isJobActive(txJob)) {
            Log.d(TAG, "stopActiveJobs, stop txJob")
            txJob!!.cancel()
        }
        if (isJobActive(rxJob)) {
            Log.d(TAG, "stopActiveJobs, stop rxJob")
            rxJob!!.cancel()
        }

    }

//    fun startRxJob(inputStream: InputStream, outputStream: OutputStream) {
//        dataTransceiver!!.setStreams(inputStream, outputStream)
//        rxJob = CoroutineScope(Dispatchers.IO).launch {
//            dataTransceiver!!.doInBackground(txFilePack!!)
//        }
//    }
}