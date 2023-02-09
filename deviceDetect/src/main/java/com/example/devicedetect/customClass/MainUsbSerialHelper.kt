package com.example.devicedetect.customClass

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.devicedetect.UsbHelperListener
import com.example.devicedetect.customClass.Util.ConstantHelperCustom

object MainUsbSerialHelper {

    private var TAG = "USB_COMMUNICATION_HELPER"

    //Context
    private lateinit var context: Application

    enum class UsbPermission {
        Unknown, Requested, Granted
    }

    private var usbPermission = UsbPermission.Unknown

    private val INTENT_ACTION_GRANT_USB = "com.example.device_detect.GRANT_USB"

    //UsbDeviceConnection
    private var mConnection: UsbDeviceConnection? = null

    //UsbHelperListener
    private lateinit var usbHelperListener: UsbHelperListener

    //UsbSerialManager
    private lateinit var usbSerialIOManager: UsbSerialIOManager

    //UsbOperation Class
    private lateinit var usbSerialIOOperation: UsbSerialIOOperation

    //Handler
    private var mainLooper: Handler? = null

    //current command
    internal var currentCommand = ""

    /**
     * BroadCast Receiver
     */
    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "onReceive: $action")
            if (INTENT_ACTION_GRANT_USB == action) {
                usbPermission = if (intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false
                    )
                ) UsbPermission.Granted
                else UsbPermission.Unknown
                connect()
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.d(TAG, "Device Connected...")
                usbHelperListener.onDeviceConnect()
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                Log.d(TAG, "Device Disconnected...")
                disconnect()
            }
        }
    }

    /**
     * Initialize Register
     */
    private fun initRegister() {
        val filter = IntentFilter()
        filter.addAction(INTENT_ACTION_GRANT_USB)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        context.registerReceiver(usbReceiver, filter)
        mainLooper = Handler(Looper.getMainLooper())
    }

    /**
     * Connect
     */
    private fun connect() {
        var device: UsbDevice? = null
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        for (v in usbManager.deviceList.values) {
            if (v.vendorId == ConstantHelperCustom.spandanVendorId || v.vendorId == ConstantHelperCustom.spandanVendorId1 || v.vendorId == ConstantHelperCustom.spandanVendorId2) {
                device = v
                Log.e(TAG, "connect: connected_vendor_id : " + device.vendorId)
            }
        }

        if (device == null) {
            usbHelperListener.onConnectionError("connection failed: device not found")
            return
        }

        //device connect
        usbHelperListener.onDeviceConnect()

        //check permission for usb connection
        if (usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(device)) {
            usbPermission = UsbPermission.Requested
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val usbPermissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(
                    INTENT_ACTION_GRANT_USB
                ), flags
            )
            usbManager.requestPermission(device, usbPermissionIntent)
            return
        }

        //open device and start serial manager
        openDevice(usbManager, device)
    }

    /**
     * Open Device
     * @param usbManager : UsbManager for open device
     * @param device: Current Device
     */
    private fun openDevice(usbManager: UsbManager, device: UsbDevice) {
        val usbConnection = usbManager.openDevice(device)
        if (usbConnection == null) {
            if (!usbManager.hasPermission(device)) usbHelperListener.onConnectionError("connection failed: permission denied")
            else usbHelperListener.onConnectionError("connection failed: open failed")
            return
        } else mConnection = usbConnection

        //start the serial manager
        mConnection.let { connection ->
            if (connection != null) {
                //initialize usbIOOperation
                usbSerialIOOperation = UsbSerialIOOperation(connection, device, usbHelperListener)
                //initialize usbSerialIOManager
                usbSerialIOManager = UsbSerialIOManager(usbSerialIOOperation, usbHelperListener)
                //launch coroutine
                usbSerialIOManager.start()
                //verify Device
                //verifyDevice()
            } else {
                Log.e(TAG, "Connection is null")
                usbHelperListener.onConnectionError("Connection is null")
            }
        }
    }

    /**
     * verifyDevice
     */
    private fun verifyDevice() {
        send(ConstantHelperCustom.REQUEST_TO_UNLOCK)
    }

    /**
     * Disconnect
     */
    private fun disconnect() {
        Log.e(TAG, "Device has been disconnected...")

        //stop UsbSerialIOManager
        usbSerialIOManager.stop()

        //release Interface of usbSerialIOOperation
        usbSerialIOOperation.releaseControl()

        //close the connection
        mConnection.let { connection ->
            connection?.close() ?: Log.e(TAG, "disconnect: Connection is null")
        }

        //reset usb permission
        usbPermission = UsbPermission.Unknown

        //notify user to device disconnect
        usbHelperListener.onDeviceDisconnect()

        //unregister receiver
        context.unregisterReceiver(usbReceiver)
    }

    /**
     * Send
     */
    private fun send(str: String) {
        if (mConnection == null) {
            Log.d(TAG, "Device disconnected...")
            return
        }
        try {
            currentCommand = str
            //write data
            usbSerialIOOperation.write(str, 2000, "Send $str")
        } catch (e: Exception) {
            Log.e(TAG, "send: Write method : " + e.message)
            usbHelperListener.onConnectionError(e.message)
        }
    }

    /**
     * Send Custom Command
     */
    private fun sendCustomCommand(str: String, time: Int) {
        if (mConnection == null) {
            Log.d(TAG, "Device disconnected...")
            usbHelperListener.onDeviceDisconnect()
            return
        } else {
            val thread = Thread {
                //command = ConstantHelperCustom.START_KEY
                send(ConstantHelperCustom.START_KEY)
                try {
                    Thread.sleep((time * 1000).toLong())
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                send(ConstantHelperCustom.STOP_KEY)
            }
            thread.start()
        }
    }

    /**
     * Received Data
     */
    internal fun receivedData(str: String) {
        usbHelperListener.onTransmission(str)
    }

    /**
     * sendVerificationCommand
     * @param command = custom verification command
     */
    internal fun sendVerificationCommand(command: String) {
        send(command)
    }

    /**
     * deviceVerificationState
     * @param isVerified = return true if verified else not verified
     * @param status = message associated with verification Status
     */
    internal fun deviceVerificationState(isVerified: Boolean, status: String) {
        if (isVerified) usbHelperListener.onDeviceVerified(isVerified)
        else usbHelperListener.onConnectionError(status)
    }

    /**
     * ACCESSIBLE METHODS
     */
    fun setCommunication(context: Context, usbHelperListener: UsbHelperListener?) {
        //initialize context
        this.context = context.applicationContext as Application

        //initialize registers
        initRegister()

        //initialize usbHelperListener
        if (usbHelperListener != null) {
            this.usbHelperListener = usbHelperListener
        }

        //check for permission and connect
        if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted) {
            mainLooper.let { looper ->
                if (looper != null) {
                    looper.post { connect() }
                } else {
                    Log.e(TAG, "setCommunication: Looper is null")
                    this.usbHelperListener.onConnectionError("Connection is null")
                }
            }
        }
    }

    fun onSendCommand(cmd: String) {
        send(
            when (cmd) {
                ConstantHelperCustom.REQUEST_TO_UNLOCK -> ConstantHelperCustom.REQUEST_TO_UNLOCK
                ConstantHelperCustom.REQUEST_TO_CONNECT -> ConstantHelperCustom.REQUEST_TO_CONNECT
                ConstantHelperCustom.START_KEY -> ConstantHelperCustom.START_KEY
                ConstantHelperCustom.STOP_KEY -> ConstantHelperCustom.STOP_KEY
                else -> cmd
            }
        )
    }

    fun onSendCustomCommand(cmd: String, timer: Int) {
        sendCustomCommand(cmd, timer)
    }

    fun onStartTransmission() {
        val thread = Thread {
            send(ConstantHelperCustom.START_KEY)
            try {
                Thread.sleep(10000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            send(ConstantHelperCustom.STOP_KEY)
        }
        thread.start()
    }

    fun onStopTransmission() {
        send(ConstantHelperCustom.STOP_KEY)
    }

    fun removeInstance() {
        disconnect()
    }
}