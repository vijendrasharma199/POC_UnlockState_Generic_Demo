package `in`.sunfox.healthcare.commons.android.sericom

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
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import `in`.sunfox.healthcare.commons.android.sericom.Util.ConstantHelper
import `in`.sunfox.healthcare.commons.android.sericom.interfaces.OnConnectionStateChangeListener

object SeriCom {

    private var TAG = javaClass.simpleName

    //Context
    private lateinit var context: Application

    //AppCompatActivity
    private lateinit var activity: AppCompatActivity

    private var usbPermission = ConstantHelper.UsbPermission.Unknown

    private const val INTENT_ACTION_GRANT_USB = "com.example.device_detect.GRANT_USB"

    //UsbDeviceConnection
    private var mConnection: UsbDeviceConnection? = null

    //UsbSerialIoOperation
    private var seriComIOOperation: SeriComIOOperation? = null

    //UsbSerialIoManager
    private var seriComIOManager: SeriComIOManager? = null

    //UsbHelperListener
    private lateinit var onConnectionStateChangeListener: OnConnectionStateChangeListener

    //current command
    internal var currentCommand = ""

    //Device Config Parameters
    internal var BAUD_RATE = 115200
    internal var REQUEST_CODE = 0x22
    internal var STOP_BIT = 0
    internal var PARITY_BIT = 0x00
    internal var DATA_BITS = 0x08
    internal var DELIMITER = "Y"

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
                else ConstantHelper.UsbPermission.Denied
                //else ConstantHelper.UsbPermission.Unknown
                connect("T")
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.d(TAG, "Device Connected...")
                onConnectionStateChangeListener.onDeviceConnect()
                connect("F")
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
        Log.w(TAG, "initRegister: ")
    }

    /**
     * Connect
     */
    private fun connect(str: String) {
        var device: UsbDevice? = null
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        for (v in usbManager.deviceList.values) {
            Log.w(TAG, "connect: Devices : ${v.vendorId}")
            device = v
            /*if (v.vendorId == ConstantHelper.spandanVendorId || v.vendorId == ConstantHelper.spandanVendorId1 || v.vendorId == ConstantHelper.spandanVendorId2) {
                device = v
                Log.e(TAG, "connect: connected_vendor_id : " + device.vendorId)
            }*/
        }

        if (device == null) {
            onConnectionStateChangeListener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : connection failed: device not found")
            return
        }

        //device connect
        onConnectionStateChangeListener.onDeviceConnect()

        if (str == "T") {
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

            //open the device
            openDevice(usbManager, device)
        } else {
            //get parent activity lifecycle and add observer
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    super.onResume(owner)
                    if (usbManager.hasPermission(device)) {
                        onConnectionStateChangeListener.onDeviceConnect()

                        //open the device
                        openDevice(usbManager, device)

                        // remove observer
                        activity.lifecycle.removeObserver(this)

                    } else {
                        onConnectionStateChangeListener.onConnectionError(
                            "${ConstantHelper.ErrorCode.CONNECTION} : connection failed: permission denied"
                        )
                    }
                }
            })
        }
    }

    /**
     * Open Device
     * @param usbManager : UsbManager for open device
     * @param device: Current Device
     */
    private fun openDevice(usbManager: UsbManager, device: UsbDevice) {
        val usbConnection = usbManager.openDevice(device)
        if (usbConnection == null) {
            if (!usbManager.hasPermission(device)) onConnectionStateChangeListener.onConnectionError(
                "${ConstantHelper.ErrorCode.CONNECTION} : connection failed: permission denied"
            )
            else onConnectionStateChangeListener.onConnectionError(
                "${ConstantHelper.ErrorCode.CONNECTION} : connection failed: open failed"
            )
            return
        } else mConnection = usbConnection

        //start the serial manager
        mConnection.let { connection ->
            if (connection != null) {
                //init seriComIOOperation
                seriComIOOperation =
                    SeriComIOOperation(connection, device, onConnectionStateChangeListener)
                //init seriComIOManager
                seriComIOOperation.let { usbOperation ->
                    if (usbOperation != null) {
                        seriComIOManager =
                            SeriComIOManager(usbOperation, onConnectionStateChangeListener)
                        //launch coroutine
                        seriComIOManager.let { usbSerialManager -> usbSerialManager?.start() }
                    }
                }
            } else {
                Log.e(TAG, "Connection is null")
                onConnectionStateChangeListener.onConnectionError(
                    "${ConstantHelper.ErrorCode.CONNECTION} : Connection is null"
                )
            }
        }
    }

    /**
     * Disconnect
     */
    private fun disconnect() {
        Log.e(TAG, "Device has been disconnected...")

        //stop UsbSerialIOManager
        seriComIOManager.let { it?.stop() }

        //clear UsbSerialIOOperation
        seriComIOOperation.let { it?.releaseControl() }

        //close the connection
        mConnection.let { connection ->
            connection?.close() ?: {
                Log.e(TAG, "disconnect: Connection is null")
                onConnectionStateChangeListener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : Connection is null")
            }
        }

        //reset usb permission
        usbPermission = ConstantHelper.UsbPermission.Unknown

        //notify user to device disconnect
        onConnectionStateChangeListener.onDeviceDisconnect()

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
            seriComIOOperation.let { usbOperation -> usbOperation?.write(str, 2000) }
        } catch (e: Exception) {
            Log.e(TAG, "send: Write method : " + e.message)
            onConnectionStateChangeListener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : ${e.message}")
        }
    }

    /**
     * Received Data
     */
    internal fun receivedData(str: String, command: String = "") {
        if (command.isNotEmpty()) send(command)
        else onConnectionStateChangeListener.onReceivedData(str)
    }

    /**
     * deviceVerificationState
     */
    internal fun setDeviceVerificationState(isVerified: Boolean, status: String) {
        if (isVerified) onConnectionStateChangeListener.onDeviceVerified()
        else onConnectionStateChangeListener.onConnectionError(
            "${ConstantHelper.ErrorCode.AUTHENTICATION} : $status"
        )
    }

    /**
     * ACCESSIBLE METHODS
     */
    @JvmStatic
    fun initialize(context: Context) {
        //initialize
        Log.w(TAG, "initialize: $context")
        SeriCom.context = context.applicationContext as Application

        //init registers
        initRegister()
    }

    //Device Config Methods
    @JvmStatic
    fun setBaudRate(baudRate: Int = 115200) {
        BAUD_RATE = baudRate
    }

    @JvmStatic
    fun setDataBit(dataBits: Int = 0x08) {
        DATA_BITS = dataBits
    }

    @JvmStatic
    fun setStopBit(stopBit: Int = 0) {
        STOP_BIT = stopBit
    }

    @JvmStatic
    fun setParity(parityBit: Int = 0x00) {
        PARITY_BIT = parityBit
    }

    @JvmStatic
    fun setRequestCode(requestCode: Int = 0x22) {
        REQUEST_CODE = requestCode
    }

    @JvmStatic
    fun setDelimiter(delimiter: String) {
        DELIMITER = delimiter
    }

    @JvmStatic
    fun setConnectionChangeListener(
        onConnectionStateChangeListener: OnConnectionStateChangeListener?,
        activity: AppCompatActivity?
    ) {
        if (onConnectionStateChangeListener != null) {
            SeriCom.onConnectionStateChangeListener = onConnectionStateChangeListener
        }

        if (activity != null) {
            SeriCom.activity = activity
        }

        //check for permission and connect
        if (usbPermission == ConstantHelper.UsbPermission.Unknown || usbPermission == ConstantHelper.UsbPermission.Granted) connect(
            "T"
        )
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