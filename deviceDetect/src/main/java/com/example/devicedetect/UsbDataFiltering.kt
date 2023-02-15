package com.example.devicedetect

import android.text.SpannableStringBuilder
import android.util.Log
import com.example.devicedetect.Util.ConstantHelper
import java.security.NoSuchAlgorithmException

internal object UsbDataFiltering {
    var TAG = "USB_DATA_FILTERING"

    private var counter: Int = 0
    private var stringBuilder = StringBuilder()
    private lateinit var deviceHashValue: String
    private lateinit var generatedHashValue: String

    /**
     * getRawDataAndApplyFilter
     * @param data = device response data( in ByteArray)
     */
    fun getRawDataAnApplyFilter(data: ByteArray) {
        Log.w(TAG, "getRawData: $data")

        val spn = SpannableStringBuilder()
        if (data.isNotEmpty()) spn.append(String(data))

        val currentCommand = MainUsbSerialHelper.currentCommand

        //REQUEST TO UNLOCK
        if (currentCommand == ConstantHelper.REQUEST_TO_UNLOCK) {
            stringBuilder.append(spn)
            if (stringBuilder.length == 99) {
                val deviceId = stringBuilder.substring(7, 23)//Get Device Id
                val microControllerId = stringBuilder.substring(23, 35)//Get MID
                deviceHashValue =
                    stringBuilder.substring(35, stringBuilder.length)//Get Device HashValue
                try {
                    //Generate Hash Value
                    generatedHashValue =
                        ConstantHelper.toHexString(ConstantHelper.getSHA(deviceId + microControllerId + ConstantHelper.NOISE))
                    if (generatedHashValue == deviceHashValue) {
                        //mainLooper!!.post { usbHelperListener!!.onTransmission("Device Unlocked...") }
                        stringBuilder.setLength(0)
                        //REQUEST FOR CONNECT
                        Log.w(
                            TAG,
                            "receiveTransmittedData: Generated Hash Value : $generatedHashValue"
                        )

                        //REQUEST FOR CONNECT
                        //MainUsbSerialHelper.sendVerificationCommand(ConstantHelper.REQUEST_TO_CONNECT + generatedHashValue)
                        MainUsbSerialHelper.receivedData(
                            "", ConstantHelper.REQUEST_TO_CONNECT + generatedHashValue
                        )

                    } else {
                        //mainLooper!!.post { usbHelperListener.onConnectionError("Unauthentic Device...") }
                        MainUsbSerialHelper.receivedData("Unauthentic Device")
                    }
                } catch (e: NoSuchAlgorithmException) {
                    e.printStackTrace()
                    Log.e(TAG, "Error : " + e.message)
                }
            }
        } else if (currentCommand == ConstantHelper.REQUEST_TO_CONNECT + generatedHashValue) {
            stringBuilder.append(spn)
            if (stringBuilder.length == 71) {
                val receivedHashValue = stringBuilder.substring(7, 71)
                if (generatedHashValue == receivedHashValue) {
                    //set deviceVerificationState = true
                    MainUsbSerialHelper.setDeviceVerificationState(true, "Verified")
                } else {
                    //set deviceVerificationState = false
                    MainUsbSerialHelper.setDeviceVerificationState(false, "Invalid Hash Value Device")
                }
                stringBuilder.setLength(0)
            }
        } else if (currentCommand == ConstantHelper.START_KEY) {
            val cmd = currentCommand

            /*//LOGIC for find duplicate entries
            val ctime = time
            val compareData = spn.substring(0, spn.indexOf(" ")).toInt()
            val currentDiff = abs(ctime - compareData);

            if (currentDiff > difference) {
                shouldLogDifference = true
                difference = currentDiff;
            }
            if (shouldLogDifference) {
                Log.e(
                    "Differences", "Data : $spn : : $ctime -- > $compareData"
                )
                shouldLogDifference = false
            }*/

            //APPLY FILTER WITH DELIMITER
            stringBuilder.append(spn)
            if (stringBuilder.isNotEmpty() || stringBuilder.toString()
                    .contains(ConstantHelper.DELIMITER)
            ) {
                val result = stringBuilder.toString().split(ConstantHelper.DELIMITER).toTypedArray()
                val lastElementOfResult = result[result.size - 1]
                val lastElementOfBuilder = stringBuilder.substring(
                    stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1, stringBuilder.length
                )
                if (lastElementOfResult == lastElementOfBuilder) {
                    returnFilteredData(result, 0, result.size - 1, cmd)
                    stringBuilder.delete(
                        0, stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1
                    )
                } else {
                    returnFilteredData(result, 0, result.size, cmd)
                    stringBuilder.delete(0, stringBuilder.length)
                }
            }

            //WITHOUT DELIMITER
            //val time = ++counter
            //Log.w(TAG, "receiveTransmittedData: Data $spn \tCounter : $time")
            //usbHelperListener!!.onTransmission("$spn \tCounter : $time")

        } else if (currentCommand == ConstantHelper.STOP_KEY) {
            val time = ++counter
            //Log.e(TAG, "receiveTransmittedData: " + command + " : " + System.currentTimeMillis() + "\tCounter : " + time)
            //usbHelperListener.onTransmission("Data : Stop -- " + spn + " : " + "\tData Length : " + spn.length() + "\tExact Length : " + time);
        } else {
            val time = ++counter;

            //APPLY FILTER WITH DELIMITER
            stringBuilder.append(spn)
            if (stringBuilder.isNotEmpty() || stringBuilder.toString()
                    .contains(ConstantHelper.DELIMITER)
            ) {
                val result = stringBuilder.toString().split(ConstantHelper.DELIMITER).toTypedArray()
                val lastElementOfResult = result[result.size - 1]
                val lastElementOfBuilder = stringBuilder.substring(
                    stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1, stringBuilder.length
                )
                if (lastElementOfResult == lastElementOfBuilder) {
                    returnFilteredData(result, 0, result.size - 1, "")
                    stringBuilder.delete(
                        0, stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1
                    )
                } else {
                    returnFilteredData(result, 0, result.size, "")
                    stringBuilder.delete(0, stringBuilder.length)
                }
            }
        }

        /*//val cmd = MainUsbSerialHelper.currentCommand
        //APPLY FILTER WITH DELIMITER
        stringBuilder.append(spn)
        if (stringBuilder.isNotEmpty() || stringBuilder.toString()
                .contains(ConstantHelper.DELIMITER)
        ) {
            val result =
                stringBuilder.toString().split(ConstantHelper.DELIMITER).toTypedArray()
            val lastElementOfResult = result[result.size - 1]
            val lastElementOfBuilder = stringBuilder.substring(
                stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1, stringBuilder.length
            )
            if (lastElementOfResult == lastElementOfBuilder) {
                returnFilteredData(result, 0, result.size - 1, cmd)
                stringBuilder.delete(
                    0, stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1
                )
            } else {
                returnFilteredData(result, 0, result.size, cmd)
                stringBuilder.delete(0, stringBuilder.length)
            }
        }*/

    }

    /**
     * Return Filter Data
     * @param result : data in array format
     * @param start : start index of array
     * @param end : end index of array
     * @param command : current executing command
     */
    private fun returnFilteredData(result: Array<String>, start: Int, end: Int, command: String) {
        for (i in start until end) {
            val time = ++counter
            Log.w(TAG, "returnDataToUser: Data ${result[i]} \tCounter : $time")
            MainUsbSerialHelper.receivedData(result[i])
        }
    }
}