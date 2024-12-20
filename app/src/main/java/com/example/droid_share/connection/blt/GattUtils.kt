package com.example.droid_share.connection

import android.bluetooth.BluetoothGatt
import android.bluetooth.le.AdvertiseCallback

class GattUtils {
    companion object {
        fun getStatus(status: Int) : String {
            var statusStr = "Unknown GATT state"
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    statusStr = "GATT operation completed successfully"
                }
                BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                    statusStr = "GATT read operation is not permitted"
                }
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                    statusStr = "GATT write operation is not permitted"
                }
                BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> {
                    statusStr = "Insufficient authentication for a given operation"
                }
                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> {
                    statusStr = "The given request is not supported"
                }
                BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> {
                    statusStr = "Insufficient encryption for a given operation"
                }
                BluetoothGatt.GATT_INVALID_OFFSET -> {
                    statusStr = "A read or write operation was requested with an invalid offset"
                }
                BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION -> {
                    statusStr = "Insufficient authorization for a given operation"
                }
                BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                    statusStr = "A write operation exceeds the maximum length of the attribute"
                }
                BluetoothGatt.GATT_CONNECTION_CONGESTED -> {
                    statusStr = "A remote device connection is congested"
                }
                BluetoothGatt.GATT_CONNECTION_TIMEOUT -> {
                    statusStr = "GATT connection timed out, likely due to the remote device " +
                            "being out of range or not advertising as connectable"
                }
                BluetoothGatt.GATT_FAILURE -> {
                    statusStr = "GATT operation failed, errors other than the above"
                }
            }
            return buildString {
                append("'")
                append(statusStr)
                append("'")
            }
        }

        fun getAdvertiseFailure(errorCode: Int): String {
            var errorStr = "Unknown advertise failure code"
            when (errorCode) {
                AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> {
                    errorStr = "Failed to start advertising as the advertise data " +
                            "to be broadcasted is larger than 31 bytes."
                }
                AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> {
                    errorStr = "Failed to start advertising because no" +
                            " advertising instance is available"
                }
                AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> {
                    errorStr = "Failed to start advertising as the advertising is already started"
                }
                AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> {
                    errorStr = "Operation failed due to an internal error"
                }
                AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> {
                    errorStr = "This feature is not supported on this platform"
                }
            }
            return errorStr
        }
    }
}