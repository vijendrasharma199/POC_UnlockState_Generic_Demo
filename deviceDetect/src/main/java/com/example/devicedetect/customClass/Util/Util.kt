package com.example.devicedetect.customClass.Util

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class Util {
    companion object {
        /**
         * @input: Take the input string to convert SHA-256
         */
        @Throws(NoSuchAlgorithmException::class)
        fun getSHA(input: String): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(input.toByteArray(StandardCharsets.UTF_8))
        }

        /**
         * @param hash as byte array
         * @return hex string of given input
         */
        fun toHexString(hash: ByteArray?): String {
            val number = BigInteger(1, hash)
            val hexString = StringBuilder(number.toString(16))
            while (hexString.length < 64) {
                hexString.insert(0, '0')
            }
            return hexString.toString()
        }

    }
}