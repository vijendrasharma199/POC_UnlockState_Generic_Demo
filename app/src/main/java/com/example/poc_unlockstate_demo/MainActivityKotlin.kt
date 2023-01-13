package com.example.poc_unlockstate_demo

import android.app.ProgressDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.devicedetect.UsbHelperListener
import com.example.devicedetect.UsbSerialCommunicationHelper
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class MainActivityKotlin : AppCompatActivity() {

    var TAG = javaClass.simpleName

    var deviceStatusTv: TextView? = null
    var receivedTextTv: TextView? = null
    var outputTv: TextView? = null
    var cmdEt: EditText? = null
    var inputEt: EditText? = null
    var convertBtn: Button? = null
    var sendBtn: Button? = null
    var clearBtn: Button? = null
    var startBtn: Button? = null
    var stopBtn: Button? = null
    var releaseBtn: Button? = null

    var helper: UsbSerialCommunicationHelper? = null
    var progressDialog: ProgressDialog? = null
    var arraylist = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_kotlin)

        initialization()

        convertBtn!!.setOnClickListener {
            val input = inputEt!!.text.toString().trim { it <= ' ' }
            try {
                outputTv!!.text = MainActivity.toHexString(MainActivity.getSHA(input + "SPDN"))
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
        }
        sendBtn!!.setOnClickListener { sendCommand() }
        clearBtn!!.setOnClickListener {
            receivedTextTv!!.text = ""
            cmdEt!!.setText("")
            receivedTextTv!!.text = "Size of list : ${arraylist.size}\n"
            arraylist.clear()
        }

        startBtn!!.setOnClickListener { helper!!.onStartTransmission() }
        stopBtn!!.setOnClickListener { helper!!.onStopTransmission() }
        releaseBtn!!.setOnClickListener {
            try {
                helper!!.removeInstance()
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendCommand() {
        val cmd = cmdEt!!.text.toString().trim { it <= ' ' }
        if (!TextUtils.isEmpty(cmd)) {
            receivedTextTv!!.append("----------------------\nSend : $cmd\n")
            helper!!.onSendCommand(cmd)
        } else {
            Toast.makeText(this, "Please enter command...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        useModule("onResume")
    }

    private fun useModule(message: String) {
        progressDialog!!.setMessage("Connecting...")
        //progressDialog.show();
        arraylist.clear()
        Log.d(TAG, "Activity Module Call : $message")
        helper!!.setCommunication(object : UsbHelperListener {
            override fun onDeviceConnect() {
                progressDialog!!.setMessage("Connecting...")
                Log.d(TAG, "Activity : Device Connected...")
                receivedTextTv!!.append("Device Connected....\n")
                deviceStatusTv!!.text = "Device Connected..."
                deviceStatusTv!!.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.verify_color))
            }

            override fun onDeviceVerified(isVerified: Boolean) {
                progressDialog!!.setMessage("Verifying...")
                progressDialog!!.dismiss()
                Log.d(TAG, "Activity : Device Verified...")
                receivedTextTv!!.append("Received : Device ready for communication\n")
                deviceStatusTv!!.text = "Device Verified..."
                deviceStatusTv!!.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.start_color))
            }

            override fun onTransmission(data: String) {
                if (progressDialog!!.isShowing) progressDialog!!.dismiss()
                Log.w(TAG, "Activity : $data")
                arraylist.add(data)
                if (arraylist.size % 100 == 0) {
                    runOnUiThread { receivedTextTv!!.append("Received : $data : Size of List : ${arraylist.size}\n") }
                }
                //runOnUiThread { receivedTextTv!!.append("Received : $data\n") }
            }

            override fun onDeviceDisconnect() {
                progressDialog!!.dismiss()
                Log.d(TAG, "Activity : Device Disconnected")
                receivedTextTv!!.append("Device disconnected or transmission stopped.....\n")
                deviceStatusTv!!.text = "Device Disconnected..."
                deviceStatusTv!!.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.stop_color))

                runOnUiThread { receivedTextTv!!.append("Size of List : ${arraylist.size}\n") }
            }

            override fun onConnectionError(errorMessage: String) {
                progressDialog!!.dismiss()
                Log.e(TAG, "onConnectionError: $errorMessage")
                deviceStatusTv!!.text = "Device Error..."
                deviceStatusTv!!.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.stop_color))
                receivedTextTv!!.append("$errorMessage\n")
            }
        })
        startBtn!!.setOnClickListener { helper!!.onStartTransmission() }
        stopBtn!!.setOnClickListener { helper!!.onStopTransmission() }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
            useModule("onNewIntent")
        }
    }

    @Throws(NoSuchAlgorithmException::class)
    fun getSHA(input: String): ByteArray? {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(StandardCharsets.UTF_8))
    }


    fun toHexString(hash: ByteArray?): String? {
        val number = BigInteger(1, hash)
        val hexString = StringBuilder(number.toString(16))
        while (hexString.length < 64) {
            hexString.insert(0, '0')
        }
        return hexString.toString()
    }

    private fun initialization() {
        deviceStatusTv = findViewById(R.id.deviceStatusTv)
        receivedTextTv = findViewById(R.id.receivedTextTv)
        receivedTextTv!!.setMovementMethod(ScrollingMovementMethod())

        cmdEt = findViewById(R.id.cmdEt)
        inputEt = findViewById(R.id.inputEt)
        outputTv = findViewById(R.id.outputTv)
        convertBtn = findViewById(R.id.convertBtn)
        sendBtn = findViewById(R.id.sendBtn)
        clearBtn = findViewById(R.id.clearBtn)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        releaseBtn = findViewById(R.id.releaseBtn)

        progressDialog = ProgressDialog(this)
        progressDialog!!.setTitle("Please wait")
        progressDialog!!.setCanceledOnTouchOutside(false)

        helper = UsbSerialCommunicationHelper.getInstance(this)

    }
}