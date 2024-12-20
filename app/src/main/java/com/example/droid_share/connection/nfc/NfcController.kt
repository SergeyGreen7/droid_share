package com.example.droid_share.connection

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.droid_share.NotificationInterface
import com.example.droid_share.connection.nfc.NfcHostApduService
import com.example.droid_share.connection.nfc.NfcUtils


val PendingIntent_Mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    PendingIntent.FLAG_MUTABLE
} else {
    0
}

class NfcController (
    private val context: Context,
    val adapter: NfcAdapter,
    private val notifier: NotificationInterface
) {

    companion object {
        private const val TAG = "NfcController"
        private const val _MIME_TYPE = "text/plain"
    }

    var isActive = false
    lateinit var intentFilters: Array<IntentFilter>

    private var validActions = listOf<String>()

    init {

        isActive = true

        val intentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            intentFilter.addDataType("*/*")
            intentFilters = arrayOf(intentFilter)
        } catch (e: Exception) {
            Log.d(TAG, "Exception on 'addDataType',$e")
        }

//        Log.d(TAG, "isSecureNfcSupported: ${adapter.isSecureNfcSupported}")
//        Log.d(TAG, "isSecureNfcEnabled: ${adapter.isSecureNfcEnabled}")

        val echLists = arrayOf(
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcF::class.java.name),
            arrayOf(NfcV::class.java.name),
        )
        Log.d(TAG, "NfcA = ${NfcA::class.java.name}")
        Log.d(TAG, "NfcB = ${NfcB::class.java.name}")
        Log.d(TAG, "NfcF = ${NfcF::class.java.name}")
        Log.d(TAG, "NfcV = ${NfcV::class.java.name}")

        validActions = listOf(
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED
        )
    }

    fun parseNfcIntent(intent: Intent) {
        if (intent.action in validActions) {
            Log.d(TAG, "intent has valid NFC action = ${intent.action}")

            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            val messages = mutableListOf<NdefMessage>()

            val empty = ByteArray(0)
            val id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)
            Log.d(TAG, "id = $id")
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)!!
            Log.d(TAG, "tag = $tag")
            val payload = NfcUtils.dumpTagData(tag)
            Log.d(TAG, "payload = $payload")
            // val record = NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, payload)
            // val msg = NdefMessage(arrayOf(record))
            // messages.add(msg)

            var rxMessage = ""
            rawMsgs?.forEach { it ->
                Log.d(TAG, "$it")
                // messages.add(it as )
                val message = it as NdefMessage
                rxMessage = message.records[0].payload.toString(Charsets.UTF_8)
                Log.d(TAG, "received message:" + message.records[0].payload.toString(Charsets.UTF_8))
            }

            if (rxMessage.isNotEmpty()) {
                notifier.showToast("Received message by NFC: $rxMessage")
            }
        }
//
//        fun sendTestMessage() : Intent {
//            val message: String = "Test data transmitted over NFC"
//            val nfcIntent: Intent = Intent(context, NfcHostApduService::class.java)
//            nfcIntent.putExtra("ndefMessage", message)
//            notifier.showToast("Message is send as NDEF message: '$message'")
//            return nfcIntent
//        }

    }
}