package com.example.droid_share.connection.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.util.Log

class NfcUtils {
    companion object {

        private const val TAG = "NfcUtils"

        fun bytesToHex(bytes: ByteArray): String {
            val result = StringBuffer()
            for (b in bytes) result.append(((b.toInt() and 0xff) + 0x100).toString(16).substring(1))
            return result.toString()
        }

        fun hexStringToByteArray(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((s[i].digitToIntOrNull(16) ?: -1 shl 4)
                + s[i + 1].digitToIntOrNull(16)!! ?: -1).toByte()
                i += 2
            }
            return data
        }
        fun dumpTagData(tag: Tag): String {
            Log.d(TAG, "start dumpTagData()")
            val sb = StringBuilder()
            val id = tag.id
            sb.append("ID (hex): ").append(toHex(id)).append('\n')
            sb.append("ID (reversed hex): ").append(toReversedHex(id)).append('\n')
            sb.append("ID (dec): ").append(toDec(id)).append('\n')
            sb.append("ID (reversed dec): ").append(toReversedDec(id)).append('\n')
            val prefix = "android.nfc.tech."
            sb.append("Technologies: ")
            for (tech in tag.techList) {
                sb.append(tech.substring(prefix.length))
                sb.append(", ")
            }
            sb.delete(sb.length - 2, sb.length)
            for (tech in tag.techList) {
                if (tech == MifareClassic::class.java.name) {
                    sb.append('\n')
                    var type = "Unknown"
                    try {
                        val mifareTag = MifareClassic.get(tag)

                        when (mifareTag.type) {
                            MifareClassic.TYPE_CLASSIC -> type = "Classic"
                            MifareClassic.TYPE_PLUS -> type = "Plus"
                            MifareClassic.TYPE_PRO -> type = "Pro"
                        }
                        sb.appendLine("Mifare Classic type: $type")
                        sb.appendLine("Mifare size: ${mifareTag.size} bytes")
                        sb.appendLine("Mifare sectors: ${mifareTag.sectorCount}")
                        sb.appendLine("Mifare blocks: ${mifareTag.blockCount}")
                    } catch (e: Exception) {
                        sb.appendLine("Mifare classic error: ${e.message}")
                    }
                }
                if (tech == MifareUltralight::class.java.name) {
                    sb.append('\n')
                    val mifareUlTag = MifareUltralight.get(tag)
                    var type = "Unknown"
                    when (mifareUlTag.type) {
                        MifareUltralight.TYPE_ULTRALIGHT -> type = "Ultralight"
                        MifareUltralight.TYPE_ULTRALIGHT_C -> type = "Ultralight C"
                    }
                    sb.append("Mifare Ultralight type: ")
                    sb.append(type)
                }
            }
            return sb.toString()
        }

        private fun toHex(bytes: ByteArray): String {
            val sb = StringBuilder()
            for (i in bytes.indices.reversed()) {
                val b = bytes[i].toInt() and 0xff
                if (b < 0x10) sb.append('0')
                sb.append(Integer.toHexString(b))
                if (i > 0) {
                    sb.append(" ")
                }
            }
            return sb.toString()
        }

        private fun toReversedHex(bytes: ByteArray): String {
            val sb = StringBuilder()
            for (i in bytes.indices) {
                if (i > 0) {
                    sb.append(" ")
                }
                val b = bytes[i].toInt() and 0xff
                if (b < 0x10) sb.append('0')
                sb.append(Integer.toHexString(b))
            }
            return sb.toString()
        }

        private fun toDec(bytes: ByteArray): Long {
            var result: Long = 0
            var factor: Long = 1
            for (i in bytes.indices) {
                val value = bytes[i].toLong() and 0xffL
                result += value * factor
                factor *= 256L
            }
            return result
        }

        private fun toReversedDec(bytes: ByteArray): Long {
            var result: Long = 0
            var factor: Long = 1
            for (i in bytes.indices.reversed()) {
                val value = bytes[i].toLong() and 0xffL
                result += value * factor
                factor *= 256L
            }
            return result
        }
    }
}