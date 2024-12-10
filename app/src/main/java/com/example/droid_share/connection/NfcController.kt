package com.example.droid_share.connection

import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.util.Log

class NfcController (
    private val adapter: NfcAdapter
) {

    companion object {
        private const val TAG = "NfcController"
        private const val _MIME_TYPE = "text/plain"
    }

    private lateinit var intentFilter: IntentFilter

    init {
        if (adapter.isEnabled) {
            intentFilter = IntentFilter (NfcAdapter.ACTION_NDEF_DISCOVERED)
            try {
                intentFilter.addDataType((_MIME_TYPE))
            } catch (e: Exception) {
                Log.d(TAG, "Exception on 'addDataType',$e")
            }

            Log.d(TAG, "isSecureNfcSupported: ${adapter.isSecureNfcSupported}")
            Log.d(TAG, "isSecureNfcEnabled: ${adapter.isSecureNfcEnabled}")

            // adapter.enableForegroundDispatch()
//            adapter.setNdefPushMessage()
        }

    }

//    fun sendMessage(data: String) {
//
//        val message =  NdefMessage(
//            NdefRecord(NdefRecord.TNF_MIME_MEDIA,
//                _MIME_TYPE.toByteArray(Charsets.US_ASCII),
//                ByteArray(1),
//                data.toByteArray(Charsets.US_ASCII)
//            ))
//
//    }
}