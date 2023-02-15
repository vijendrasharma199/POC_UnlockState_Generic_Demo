package com.example.devicedetect.Util

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object ConstantHelper {
    internal const val spandanVendorId = 1155
    internal const val spandanVendorId1 = 4292
    internal const val spandanVendorId2 = 9025
    internal const val BAUD_RATE = 115200
    internal const val REQUEST_TO_UNLOCK = "RTU"
    internal const val REQUEST_TO_CONNECT = "RTC"
    internal const val START_KEY = "1"
    internal const val STOP_KEY = "0"
    internal const val DELIMITER = "Y"
    internal const val NOISE = "SPDN"

    internal val INTENT_ACTION_GRANT_USB = "com.example.device_detect.GRANT_USB"

    //ENUM Class
    internal enum class UsbPermission {
        Unknown, Requested, Granted, Denied
    }

    internal enum class ErrorCode {
        AUTHENTICATION, CONNECTION, COMMAND, ENDPOINT, USB_REQUEST, DATA
    }

    //Functions
    @Throws(NoSuchAlgorithmException::class)
    internal fun getSHA(input: String): ByteArray? {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(StandardCharsets.UTF_8))
    }

    internal fun toHexString(hash: ByteArray?): String {
        val number = BigInteger(1, hash)
        val hexString = StringBuilder(number.toString(16))
        while (hexString.length < 64) {
            hexString.insert(0, '0')
        }
        return hexString.toString()
    }
}