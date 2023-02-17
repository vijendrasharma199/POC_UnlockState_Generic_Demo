package com.example.devicedetect.Util

import android.util.Log

class SpandanResponseDecoder(private val key: String) {

    private val keyIndex = arrayListOf('0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f')

    fun decodeResponseDid(responseInput: String): String {
        val filteredResponse = ArrayList<String>()
        var counter = 0
        val data = StringBuilder()
        val encodedResponse = ArrayList<Char>()
        responseInput.forEach {
            encodedResponse.add(keyIndex[key.indexOf(it)])
        }
        encodedResponse.forEachIndexed { index, c ->
            if (counter < 2) {
                data.append(c)
                counter++
                if (index == responseInput.lastIndex)
                    filteredResponse.add((data.toString().decodeHex()).toString())
            } else {
                counter = 0
                filteredResponse.add((data.toString().decodeHex()).toString())
                data.clear()
                data.append(c)
                counter++
            }
        }
        return filteredResponse.toString()
    }

    fun decodeResponseMid(responseInput: String): String {
        val filteredResponse = ArrayList<String>()
        var counter = 0
        val data = StringBuilder()
        val encodedResponse = ArrayList<Char>()
        responseInput.forEach {
            encodedResponse.add(keyIndex[key.indexOf(it)])
        }
        encodedResponse.forEachIndexed { index, c ->
            if (counter < 2) {
                data.append(c)
                counter++
                if (index == responseInput.lastIndex)
                    filteredResponse.add((data.toString()))
            } else {
                counter = 0
                filteredResponse.add((data.toString()))
                data.clear()
                data.append(c)
                counter++
            }
        }
        return filteredResponse.toString()
    }

    private fun String.decodeHex(): String {
        require(length % 2 == 0) {"Must have an even length"}
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
            .toString(Charsets.ISO_8859_1)  // Or whichever encoding your input uses
    }

}