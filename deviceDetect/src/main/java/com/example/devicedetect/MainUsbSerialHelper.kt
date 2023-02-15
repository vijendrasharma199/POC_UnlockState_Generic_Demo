package com.example.devicedetect

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
import android.util.Log
import com.example.devicedetect.Util.ConstantHelper

object MainUsbSerialHelper {

    private var TAG = "USB_COMMUNICATION_HELPER"

    //Context
    private lateinit var context: Application

    private var usbPermission = ConstantHelper.UsbPermission.Unknown

    private val INTENT_ACTION_GRANT_USB = "com.example.device_detect.GRANT_USB"

    //UsbDeviceConnection
    private var mConnection: UsbDeviceConnection? = null

    //UsbOperation Class
    private lateinit var usbSerialIOOperation: UsbSerialIOOperation

    //UsbSerialManager
    private lateinit var usbSerialIOManager: UsbSerialIOManager

    //UsbHelperListener
    private lateinit var usbHelperListener: UsbHelperListener

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
                ) ConstantHelper.UsbPermission.Granted
                //else ConstantHelper.UsbPermission.Denied
                else ConstantHelper.UsbPermission.Unknown
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
        //mainLooper = Handler(Looper.getMainLooper())
        Log.w(TAG, "initRegister: ")
    }

    /**
     * Connect
     */
    private fun connect() {
        var device: UsbDevice? = null
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        for (v in usbManager.deviceList.values) {
            if (v.vendorId == ConstantHelper.spandanVendorId || v.vendorId == ConstantHelper.spandanVendorId1 || v.vendorId == ConstantHelper.spandanVendorId2) {
                device = v
                Log.e(TAG, "connect: connected_vendor_id : " + device.vendorId)
            }
        }

        if (device == null) {
            usbHelperListener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : connection failed: device not found")
            return
        }

        //device connect
        usbHelperListener.onDeviceConnect()

        //check permission for usb connection
        if (usbPermission == ConstantHelper.UsbPermission.Unknown && !usbManager.hasPermission(
                device
            )
        ) {
            usbPermission = ConstantHelper.UsbPermission.Requested
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
            if (!usbManager.hasPermission(device)) usbHelperListener.onConnectionError(
                "${ConstantHelper.ErrorCode.CONNECTION} : connection failed: permission denied"
            )
            else usbHelperListener.onConnectionError(
                "${ConstantHelper.ErrorCode.CONNECTION} : connection failed: open failed"
            )
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
            } else {
                Log.e(TAG, "Connection is null")
                usbHelperListener.onConnectionError(
                    "${ConstantHelper.ErrorCode.CONNECTION} : Connection is null"
                )
            }
        }
    }

    /**
     * Disconnect
     */
    internal fun disconnect() {
        Log.e(TAG, "Device has been disconnected...")

        //stop UsbSerialIOManager
        usbSerialIOManager.stop()

        //release Interface of usbSerialIOOperation
        usbSerialIOOperation.releaseControl()

        //close the connection
        mConnection.let { connection ->
            connection?.close() ?: {
                Log.e(TAG, "disconnect: Connection is null")
                usbHelperListener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : Connection is null")
            }
        }

        //reset usb permission
        usbPermission = ConstantHelper.UsbPermission.Unknown

        //notify user to device disconnect
        usbHelperListener.onDeviceDisconnect()

        //unregister receiver
        //context.unregisterReceiver(usbReceiver)
    }

    /**
     * Send Data
     */
    private fun send(str: String) {
        if (mConnection == null) {
            Log.d(TAG, "Device disconnected...")
            return
        }
        try {
            currentCommand = str
            //write data
            usbSerialIOOperation.write(str, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "send: Write method : " + e.message)
            usbHelperListener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : ${e.message}")
        }
    }

    /**
     * Received Data
     */
    internal fun receivedData(str: String, command: String = "") {
        if (command.isNotEmpty()) send(command)
        else usbHelperListener.onReceivedData(str)
    }

    /**
     * deviceVerificationState
     */
    internal fun setDeviceVerificationState(isVerified: Boolean, status: String) {
        if (isVerified) usbHelperListener.onDeviceVerified()
        else usbHelperListener.onConnectionError(
            "${ConstantHelper.ErrorCode.AUTHENTICATION} : $status"
        )
    }

    /**
     * ACCESSIBLE METHODS
     */
    @JvmStatic
    //fun initialize(context: Context, usbHelperListener: UsbHelperListener?) {
    fun initialize(context: Context) {
        //initialize context
        Log.w(TAG, "initialize: $context")
        MainUsbSerialHelper.context = context.applicationContext as Application

        //initialize registers
        initRegister()

        /*//check for permission and connect
        if (usbPermission == ConstantHelper.UsbPermission.Unknown || usbPermission == ConstantHelper.UsbPermission.Granted) {
            if (usbHelperListener != null) {
                MainUsbSerialHelper.usbHelperListener = usbHelperListener
            }
            connect()
        }*/
    }

    @JvmStatic
    fun setDeviceCallback(usbHelperListener: UsbHelperListener?) {
        //initialize usbHelperListener
        if (usbHelperListener != null) {
            MainUsbSerialHelper.usbHelperListener = usbHelperListener
        }

        //check for permission and connect
        if (usbPermission == ConstantHelper.UsbPermission.Unknown || usbPermission == ConstantHelper.UsbPermission.Granted) {
            connect()
            /*mainLooper.let { looper ->
                if (looper != null) {
                    looper.post { connect() }
                } else {
                    Log.e(TAG, "initialize: Looper is null")
                    MainUsbSerialHelper.usbHelperListener.onConnectionError(
                        "${ConstantHelper.ErrorCode.CONNECTION} : Connection is null"
                    )
                }
            }*/
        }
    }

    @JvmStatic
    fun sendCommand(cmd: String) {
        send(cmd)
    }

    @JvmStatic
    fun clearInstance() {
        //unregister receiver
        context.unregisterReceiver(usbReceiver)
        disconnect()
    }
}