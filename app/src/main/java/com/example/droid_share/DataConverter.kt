package com.example.droid_share

class DataConverter {

    companion object{

        fun string2Utf8(string : String): ByteArray {
            return string.toByteArray(Charsets.UTF_8)
        }

        // convert integer into little-endian byte array
        fun value2bytes(value : Int, size : Int) : ByteArray {
            var i = 0
            var v = value
            val bytes = ByteArray(size)
            while (v != 0) {
                bytes[i++] = (v % 256).toByte();
                v /= 256;
            }
            return bytes
        }

        // convert little-endian byte array into  integer
        fun bytes2value(bytes: ByteArray) : Int {
            var i = 0
            val numBytes = bytes.sumOf { it.toUByte().toInt() shl (8 * i++) }
            return numBytes
        }

    }

}