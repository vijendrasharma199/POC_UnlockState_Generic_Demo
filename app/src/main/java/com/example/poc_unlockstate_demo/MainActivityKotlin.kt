package com.example.poc_unlockstate_demo

import android.app.ProgressDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.devicedetect.UsbHelperListener
import com.example.devicedetect.UsbSerialCommunicationHelper
import com.example.poc_unlockstate_demo.databinding.ActivityMainKotlinBinding
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class MainActivityKotlin : AppCompatActivity() {

    var TAG = javaClass.simpleName

    lateinit var binding: ActivityMainKotlinBinding

    //Module Variables
    var helper: UsbSerialCommunicationHelper? = null
    var progressDialog: ProgressDialog? = null
    var arraylist = ArrayList<String>()

    //Required Variable
    var divideBy: Int = 100
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainKotlinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initialization()

        binding.convertBtn.setOnClickListener {
            val input = binding.inputEt.text.toString().trim()
            try {
                binding.outputTv.text = toHexString(getSHA(input + "SPDN"))
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
        }
        binding.sendBtn.setOnClickListener { sendCommand() }
        binding.clearBtn.setOnClickListener {
            binding.receivedTextTv.text = ""
            binding.cmdEt.setText("")
            binding.timerEt.setText("")
            binding.receivedTextTv.text = "Size of list : ${arraylist.size}\n"
            arraylist.clear()
        }

        binding.startBtn.setOnClickListener { helper!!.onStartTransmission() }
        binding.stopBtn.setOnClickListener { helper!!.onStopTransmission() }
        binding.releaseBtn.setOnClickListener {
            try {
                helper!!.removeInstance()
            } catch (e: Exception) {
                showToast(e.toString())
            }
        }

        //set default partition
        binding.partitionEt.setText("100")
    }

    private fun sendCommand() {
        arraylist.clear()
        val cmd = binding.cmdEt.text.toString().trim()
        val time = binding.timerEt.text.toString().trim()

        val partitionBy = binding.partitionEt.text.toString().trim()
        if (partitionBy.isNotEmpty()) {
            divideBy = partitionBy.toInt()
        } else {
            divideBy = 100
        }

        if (!TextUtils.isEmpty(cmd)) {
            binding.receivedTextTv.append("----------------------\nSend : $cmd\n")
            if (time.isNotEmpty()) {
                helper!!.onSendCustomCommand(cmd, time.toInt())
            } else {
                helper!!.onSendCommand(cmd)
            }
        } else {
            showToast("Please enter command...")
        }
    }

    override fun onResume() {
        super.onResume()
        useModule("onResume")
    }

    private fun useModule(message: String) {
        arraylist.clear()
        Log.d(TAG, "Activity Module Call : $message")
        helper!!.setCommunication(object : UsbHelperListener {
            override fun onDeviceConnect() {
                progressDialog!!.setMessage("Connecting...")
                Log.d(TAG, "Activity : Device Connected...")
                binding.receivedTextTv.append("Device Connected....\n")
                binding.deviceStatusTv.text = "Device Connected..."
                binding.deviceStatusTv.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.verify_color))
            }

            override fun onDeviceVerified(isVerified: Boolean) {
                progressDialog!!.setMessage("Verifying...")
                progressDialog!!.dismiss()
                Log.d(TAG, "Activity : Device Verified...")
                binding.receivedTextTv.append("Received : Device ready for communication\n")
                binding.deviceStatusTv.text = "Device Verified..."
                binding.deviceStatusTv.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.start_color))
            }

            override fun onTransmission(data: String) {
                if (progressDialog!!.isShowing) progressDialog!!.dismiss()
                Log.w(TAG, "Activity : $data")
                arraylist.add(data)
                if (arraylist.size % divideBy == 0) {
                    var size = arraylist.size
                    runOnUiThread { binding.receivedTextTv.append("Received : $data : Size of List : $size\n") }
                }
                //runOnUiThread { binding.receivedTextTv.setText("Received : $data : Size of List : ${arraylist.size}\n") }
                //runOnUiThread { binding.receivedTextTv.setText("Received : $data\n") }
                //runOnUiThread { binding.receivedTextTv.setText("Received : ${SpannableStringBuilder(data)}\n") }
            }

            override fun onDeviceDisconnect() {
                progressDialog!!.dismiss()
                Log.d(TAG, "Activity : Device Disconnected")
                binding.receivedTextTv.append("Device disconnected or transmission stopped.....\n")
                binding.deviceStatusTv.text = "Device Disconnected..."
                binding.deviceStatusTv.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.stop_color))

                runOnUiThread { binding.receivedTextTv.append("Size of List : ${arraylist.size}\n") }
            }

            override fun onConnectionError(errorMessage: String) {
                progressDialog!!.dismiss()
                Log.e(TAG, "onConnectionError: $errorMessage")
                binding.deviceStatusTv.text = "Device Error..."
                binding.deviceStatusTv.backgroundTintList =
                    ColorStateList.valueOf(resources.getColor(R.color.stop_color))
                runOnUiThread { binding.receivedTextTv.append("$errorMessage\n") }
            }
        })
        binding.startBtn.setOnClickListener { helper!!.onStartTransmission() }
        binding.stopBtn.setOnClickListener { helper!!.onStopTransmission() }
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

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun initialization() {
        binding.receivedTextTv.movementMethod = ScrollingMovementMethod()

        progressDialog = ProgressDialog(this)
        progressDialog!!.setTitle("Please wait")
        progressDialog!!.setCanceledOnTouchOutside(false)

        //helper = UsbSerialCommunicationHelper.getInstance(this, )
        helper = UsbSerialCommunicationHelper.getInstance(this)
    }
}