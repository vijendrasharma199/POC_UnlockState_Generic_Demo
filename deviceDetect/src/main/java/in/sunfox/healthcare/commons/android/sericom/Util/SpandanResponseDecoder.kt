package `in`.sunfox.healthcare.commons.android.sericom.Util

import android.util.Log

class SpandanResponseDecoder(private val key: String) {

    private var TAG = "SpandanResponseDecoder"

    private val keyIndex =
        arrayListOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    fun decode(responseInput: String): String {
        val filteredResponse: StringBuilder = StringBuilder()
        var counter = 0
        val data = StringBuilder()
        val encodedResponse = ArrayList<Char>()
        Log.d(TAG, "decode: \n")
        responseInput.forEach {
            // Log.d(TAG, "decode: Input Array Item: $it --> Encoded Index Item: ${keyIndex[key.indexOf(it)]}")
            encodedResponse.add(keyIndex[key.indexOf(it)])
        }
        encodedResponse.forEachIndexed { index, c ->
            //Log.d(TAG, "decode: Encoded Response Item ${encodedResponse[index]} Index: $index --> $c")
            if (counter < 2) {
                data.append(c)
                counter++
                if (index == responseInput.lastIndex) filteredResponse.append(data.toString())
                //Log.d(TAG, "decode: data : $data --> FilteredResponse $filteredResponse")
            } else {
                counter = 0
                filteredResponse.append(data.toString())
                data.clear()
                data.append(c)
                counter++
                //Log.w(TAG, "decode: data : $data --> FilteredResponse $filteredResponse")
            }
        }
        return filteredResponse.toString()
    }

    private fun String.decodeHex(): String {
        require(length % 2 == 0) { "Must have an even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            .toString(Charsets.ISO_8859_1)  // Or whichever encoding your input uses
    }

    fun convertToAscii(input: String): Char {
        val value = input.toInt()
        return if (value in 0..127) {
            value.toChar()
        } else {
            throw NumberFormatException("Number not in range")
        }
    }

}