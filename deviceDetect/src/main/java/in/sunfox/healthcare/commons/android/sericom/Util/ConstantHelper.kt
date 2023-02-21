package `in`.sunfox.healthcare.commons.android.sericom.Util

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object ConstantHelper {
    internal enum class UsbPermission {
        Unknown, Requested, Granted, Denied
    }

    internal enum class ErrorCode {
        AUTHENTICATION, CONNECTION, ENDPOINT, USB_REQUEST, DATA
    }

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