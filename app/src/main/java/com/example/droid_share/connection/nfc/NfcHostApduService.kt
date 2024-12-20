package com.example.droid_share.connection.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * This class emulates a NFC Forum Tag Type 4 containing a NDEF message
 * The class uses the AID D2760000850101
 */
class NfcHostApduService : HostApduService() {
    private lateinit var mNdefRecordFile: ByteArray

    private var mAppSelected = false // true when SELECT_APPLICATION detected

    private var mCcSelected = false // true when SELECT_CAPABILITY_CONTAINER detected

    private var mNdefSelected = false // true when SELECT_NDEF_FILE detected

    override fun onCreate() {
        super.onCreate()

        mAppSelected = false
        mCcSelected = false
        mNdefSelected = false

        // default NDEF-message
        val DEFAULT_MESSAGE =
            "This is the default message from NfcHceNdelEmulator. If you want to change the message use the tab 'Send' to enter an individual message."
        val ndefDefaultMessage = getNdefMessage(DEFAULT_MESSAGE)
        // the maximum length is 246 so do not extend this value
        val nlen = ndefDefaultMessage!!.byteArrayLength
        mNdefRecordFile = ByteArray(nlen + 2)
        mNdefRecordFile[0] = ((nlen and 0xff00) / 256).toByte()
        mNdefRecordFile[1] = (nlen and 0xff).toByte()
        System.arraycopy(
            ndefDefaultMessage.toByteArray(),
            0,
            mNdefRecordFile,
            2,
            ndefDefaultMessage.byteArrayLength
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent != null) {
            // intent contains a text message
            if (intent.hasExtra("ndefMessage")) {
                val ndefMessage = getNdefMessage(intent.getStringExtra("ndefMessage"))
                if (ndefMessage != null) {
                    val nlen = ndefMessage.byteArrayLength
                    mNdefRecordFile = ByteArray(nlen + 2)
                    mNdefRecordFile[0] = ((nlen and 0xff00) / 256).toByte()
                    mNdefRecordFile[1] = (nlen and 0xff).toByte()
                    System.arraycopy(
                        ndefMessage.toByteArray(),
                        0,
                        mNdefRecordFile,
                        2,
                        ndefMessage.byteArrayLength
                    )
                }
            }
            // intent contains an URL
            if (intent.hasExtra("ndefUrl")) {
                val ndefMessage = getNdefUrlMessage(intent.getStringExtra("ndefUrl"))
                if (ndefMessage != null) {
                    val nlen = ndefMessage.byteArrayLength
                    mNdefRecordFile = ByteArray(nlen + 2)
                    mNdefRecordFile[0] = ((nlen and 0xff00) / 256).toByte()
                    mNdefRecordFile[1] = (nlen and 0xff).toByte()
                    System.arraycopy(
                        ndefMessage.toByteArray(),
                        0,
                        mNdefRecordFile,
                        2,
                        ndefMessage.byteArrayLength
                    )
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun getNdefMessage(ndefData: String?): NdefMessage? {
        if (ndefData!!.length == 0) {
            return null
        }
        val ndefRecord = NdefRecord.createTextRecord("en", ndefData)
        return NdefMessage(ndefRecord)
    }

    private fun getNdefUrlMessage(ndefData: String?): NdefMessage? {
        if (ndefData!!.length == 0) {
            return null
        }
        val ndefRecord = NdefRecord.createUri(ndefData)
        return NdefMessage(ndefRecord)
    }

    /**
     * emulates an NFC Forum Tag Type 4
     */
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle): ByteArray {
        Log.d((TAG), "commandApdu: " + NfcUtils.bytesToHex(commandApdu))

        //if (Arrays.equals(SELECT_APP, commandApdu)) {
        // check if commandApdu qualifies for SELECT_APPLICATION
        if (SELECT_APPLICATION.contentEquals(commandApdu)) {
            mAppSelected = true
            mCcSelected = false
            mNdefSelected = false
            Log.d((TAG), "responseApdu: " + NfcUtils.bytesToHex(SUCCESS_SW))
            return SUCCESS_SW
            // check if commandApdu qualifies for SELECT_CAPABILITY_CONTAINER
        } else if (mAppSelected && SELECT_CAPABILITY_CONTAINER.contentEquals(commandApdu)) {
            mCcSelected = true
            mNdefSelected = false
            Log.d((TAG), "responseApdu: " + NfcUtils.bytesToHex(SUCCESS_SW))
            return SUCCESS_SW
            // check if commandApdu qualifies for SELECT_NDEF_FILE
        } else if (mAppSelected && SELECT_NDEF_FILE.contentEquals(commandApdu)) {
            // NDEF
            mCcSelected = false
            mNdefSelected = true
            Log.d((TAG), "responseApdu: " + NfcUtils.bytesToHex(SUCCESS_SW))
            return SUCCESS_SW
            // check if commandApdu qualifies for // READ_BINARY
        } else if (commandApdu[0] == 0x00.toByte() && commandApdu[1] == 0xb0.toByte()) {
            // READ_BINARY
            // get the offset an le (length) data
            //System.out.println("** " + Utils.bytesToHex(commandApdu) + " in else if (commandApdu[0] == (byte)0x00 && commandApdu[1] == (byte)0xb0) {");
            val offset =
                (0x00ff and commandApdu[2].toInt()) * 256 + (0x00ff and commandApdu[3].toInt())
            val le = 0x00ff and commandApdu[4].toInt()

            val responseApdu = ByteArray(le + SUCCESS_SW.size)

            if (mCcSelected && offset == 0 && le == CAPABILITY_CONTAINER_FILE.size) {
                System.arraycopy(CAPABILITY_CONTAINER_FILE, offset, responseApdu, 0, le)
                System.arraycopy(SUCCESS_SW, 0, responseApdu, le, SUCCESS_SW.size)
                Log.d((TAG), "responseApdu: " + NfcUtils.bytesToHex(responseApdu))
                return responseApdu
            } else if (mNdefSelected) {
                if (offset + le <= mNdefRecordFile.size) {
                    System.arraycopy(mNdefRecordFile, offset, responseApdu, 0, le)
                    System.arraycopy(SUCCESS_SW, 0, responseApdu, le, SUCCESS_SW.size)
                    Log.d((TAG), "responseApdu: " + NfcUtils.bytesToHex(responseApdu))
                    return responseApdu
                }
            }
        }

        // The tag should return different errors for different reasons
        // this emulation just returns the general error message
        Log.d((TAG), "responseApdu: " + NfcUtils.bytesToHex(FAILURE_SW))
        return FAILURE_SW
    }

    /*
complete sequence:
commandApdu: 00a4040007d276000085010100
responseApdu: 9000
commandApdu: 00a4000c02e103
responseApdu: 9000
commandApdu: 00b000000f
responseApdu: 000f20003b00340406e10400ff00ff9000
commandApdu: 00a4000c02e104
responseApdu: 9000
commandApdu: 00b0000002
responseApdu: 002e9000
commandApdu: 00b000022e
responseApdu: d1012a55046769746875622e636f6d2f416e64726f696443727970746f3f7461623d7265706f7369746f726965739000
 */
    /**
     * onDeactivated is called when reading ends
     * reset the status boolean values
     */
    override fun onDeactivated(reason: Int) {
        mAppSelected = false
        mCcSelected = false
        mNdefSelected = false
    }

    companion object {
        // source: https://github.com/TechBooster/C85-Android-4.4-Sample/blob/master/chapter08/NdefCard/src/com/example/ndefcard/NdefHostApduService.java
        private const val TAG = "MyHostApduService"

        private val SELECT_APPLICATION = byteArrayOf(
            0x00.toByte(),  // CLA	- Class - Class of instruction
            0xA4.toByte(),  // INS	- Instruction - Instruction code
            0x04.toByte(),  // P1	- Parameter 1 - Instruction parameter 1
            0x00.toByte(),  // P2	- Parameter 2 - Instruction parameter 2
            0x07.toByte(),  // Lc field	- Number of bytes present in the data field of the command
            0xD2.toByte(),
            0x76.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x85.toByte(),
            0x01.toByte(),
            0x01.toByte(),  // NDEF Tag Application name D2 76 00 00 85 01 01
            0x00.toByte() // Le field	- Maximum number of bytes expected in the data field of the response to the command
        )

        private val SELECT_CAPABILITY_CONTAINER = byteArrayOf(
            0x00.toByte(),  // CLA	- Class - Class of instruction
            0xa4.toByte(),  // INS	- Instruction - Instruction code
            0x00.toByte(),  // P1	- Parameter 1 - Instruction parameter 1
            0x0c.toByte(),  // P2	- Parameter 2 - Instruction parameter 2
            0x02.toByte(),  // Lc field	- Number of bytes present in the data field of the command
            0xe1.toByte(), 0x03.toByte() // file identifier of the CC file
        )

        private val SELECT_NDEF_FILE = byteArrayOf(
            0x00.toByte(),  // CLA	- Class - Class of instruction
            0xa4.toByte(),  // Instruction byte (INS) for Select command
            0x00.toByte(),  // Parameter byte (P1), select by identifier
            0x0c.toByte(),  // Parameter byte (P1), select by identifier
            0x02.toByte(),  // Lc field	- Number of bytes present in the data field of the command
            0xE1.toByte(),
            0x04.toByte() // file identifier of the NDEF file retrieved from the CC file
        )

        private val CAPABILITY_CONTAINER_FILE = byteArrayOf(
            0x00, 0x0f,  // CCLEN
            0x20,  // Mapping Version
            0x00, 0x3b,  // Maximum R-APDU data size
            0x00, 0x34,  // Maximum C-APDU data size
            0x04, 0x06,  // Tag & Length
            0xe1.toByte(), 0x04,  // NDEF File Identifier
            0x00.toByte(), 0xff.toByte(),  // Maximum NDEF size, do NOT extend this value
            0x00,  // NDEF file read access granted
            0xff.toByte(),  // NDEF File write access denied
        )

        // Status Word success
        private val SUCCESS_SW = byteArrayOf(
            0x90.toByte(),
            0x00.toByte(),
        )

        // Status Word failure
        private val FAILURE_SW = byteArrayOf(
            0x6a.toByte(),
            0x82.toByte(),
        )
    }
}