package com.example.devicedetect

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.math.max

class UsbSerialCommunicationHelper private constructor(private var context: Context) {

    enum class UsbPermission {
        Unknown, Requested, Granted
    }

    private var TAG = "USB_COMMUNICATION_HELPER"
    private var usbPermission = UsbPermission.Unknown
    private var mainLooper: Handler? = null

    //Required Variables
    private var mControlInterface: UsbInterface? = null
    private var mReadEndpoint: UsbEndpoint? = null
    private var mWriteEndpoint: UsbEndpoint? = null
    private var mUsbRequest: UsbRequest? = null
    private var mControlIndex = 0

    //UsbHelperListener
    private lateinit var usbHelperListener: UsbHelperListener

    //Custom implementation
    private var mConnection: UsbDeviceConnection? = null
    private var mReadBuffer: ByteBuffer? = null
    private var coroutineJob: CompletableJob? = null

    //Interface Receive data
    private var command = ""
    private var stringBuilder = StringBuilder()
    private var deviceHashValue = ""
    private var generatedHashValue = ""

    /**
     * @param input
     * @return SHA 256 of given input
     * @throws NoSuchAlgorithmException
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

    private fun initRegister() {
        val filter = IntentFilter()
        filter.addAction(INTENT_ACTION_GRANT_USB)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        context.registerReceiver(usbReceiver, filter)
        mainLooper = Handler(Looper.getMainLooper())
    }

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

    private fun connect() {
        //Log.w(TAG, "connect: ")
        var device: UsbDevice? = null
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        for (v in usbManager.deviceList.values) {
            //Log.e(TAG, "connect: connected_vendor_id : " + v.vendorId)
            if (v.vendorId == ConstantHelper.spandanVendorId || v.vendorId == ConstantHelper.spandanVendorId1 || v.vendorId == ConstantHelper.spandanVendorId2) {
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

        val usbConnection = usbManager.openDevice(device)
        if (usbConnection == null) {
            if (!usbManager.hasPermission(device)) usbHelperListener.onConnectionError("connection failed: permission denied")
            else usbHelperListener.onConnectionError("connection failed: open failed")
            return
        } else mConnection = usbConnection


        //getInterfacesAndEndpointsOfDevice
        getRequiredInterfacesAndEndpointsOfDevice(device)
    }

    private fun getRequiredInterfacesAndEndpointsOfDevice(device: UsbDevice) {
        mControlInterface = null
        mReadEndpoint = null
        mWriteEndpoint = null

        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_COMM) mControlIndex = i
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) mControlInterface =
                usbInterface
        }

        if (mControlInterface == null) {
            Log.i(TAG, "Single Interface")
            mControlInterface = device.getInterface(0)
        } else Log.i(TAG, "Multiple Interface")

        Log.d(TAG, "data iface = $mControlInterface")

        mConnection.let { connection ->
            if (connection != null) {
                if (!connection.claimInterface(mControlInterface, true)) {
                    Log.w(TAG, "findInterfaceOfDevice: Could not claim data interface")
                    //showToast("Could not claim data interface")
                    //TODO Error Notify
                    usbHelperListener.onConnectionError("Could not claim data interface")
                    return
                }

                mControlInterface.let { controlInterface ->
                    if (controlInterface != null) {
                        for (i in 0 until controlInterface.endpointCount) {
                            val ep = controlInterface.getEndpoint(i)
                            if (ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) mReadEndpoint =
                                ep
                            if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) mWriteEndpoint =
                                ep
                        }
                    } else {
                        //TODO Error Notify
                        Log.e(TAG, "Control Interface is null")
                        usbHelperListener.onConnectionError("Control Interface is null")
                    }
                }

                /*//Control Transfer
                val buf = byteArrayOf(
                    (ConstantHelper.BAUD_RATE and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 8 and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 16 and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 24 and 0xff).toByte(),
                    1,
                    0,
                    8
                )
                val result = connection.controlTransfer(
                    UsbConstants.USB_TYPE_CLASS or 0x01, 0x20, 0, mControlIndex, buf, buf.size, 5000
                )*/

                /*//Control Transfer With tested
                val buf = byteArrayOf(
                    (ConstantHelper.BAUD_RATE and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 8 and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 16 and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 24 and 0xff).toByte(),
                    0,//Stop Bits
                    0x00,//Parity Bits
                    0x08//Data Bits
                )
                //ESP & SPDN & Arduino Working but SPDN* not
                //val result = connection.controlTransfer(UsbConstants.USB_TYPE_CLASS or 0x01, 0x20, 0, mControlIndex, buf, buf.size, 0)
                //val result = connection.controlTransfer(CDC_REQTYPE_HOST2DEVICE, 0x20, 0, mControlIndex, buf, buf.size, 0)

                //SPDN & SPDN* working but ESP  & Arduino(hang) not
                //val result = connection.controlTransfer(UsbConstants.USB_TYPE_CLASS or 0x01, 0x22, 0, mControlIndex, buf, buf.size, 0)
                //val result = connection.controlTransfer(CDC_REQTYPE_HOST2DEVICE, 0x22, 0, mControlIndex, buf, buf.size, 0)*/

                val buf = byteArrayOf(
                    (ConstantHelper.BAUD_RATE and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 8 and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 16 and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 24 and 0xff).toByte(),
                    0,//Stop Bits
                    0x00,//Parity Bits
                    0x08//Data Bits
                )

                val result: Int
                if (device.vendorId == ConstantHelper.spandanVendorId) {
                    result = connection.controlTransfer(
                        UsbConstants.USB_TYPE_CLASS or 0x01,
                        0x22,
                        0,
                        mControlIndex,
                        buf,
                        buf.size,
                        0
                    )
                } else {
                    result = connection.controlTransfer(
                        UsbConstants.USB_TYPE_CLASS or 0x01,
                        0x20,
                        0,
                        mControlIndex,
                        buf,
                        buf.size,
                        0
                    )
                }
                Log.w(
                    TAG, "Control Transfer Result : $result"
                )

                //allocate size of read endpoint
                mReadEndpoint.let { readPoint ->
                    if (readPoint != null) {
                        mReadBuffer = ByteBuffer.allocate(readPoint.maxPacketSize)

                        mUsbRequest = UsbRequest()
                        mUsbRequest.let { usbRequest ->
                            if (usbRequest != null) {
                                usbRequest.initialize(connection, mReadEndpoint)
                            } else {
                                usbHelperListener.onConnectionError("UsbRequest is null")
                                Log.e(
                                    TAG,
                                    "getRequiredInterfacesAndEndpointsOfDevice: UsbRequest is null"
                                )
                            }
                        }
                    } else {
                        Log.e(TAG, "ReadEndPoint is null")
                        usbHelperListener.onConnectionError("ReadEndPoint is null")
                    }
                }
            } else {
                Log.e(TAG, "Connection is null")
                usbHelperListener.onConnectionError("Connection is null")
            }
        }

        //Launch Coroutine
        launchCoroutine()
    }

    private fun verifyDevice() {
        command = ConstantHelper.REQUEST_TO_UNLOCK
        send(ConstantHelper.REQUEST_TO_UNLOCK)
    }

    private fun launchCoroutine() {
        counter = 0
        coroutineJob = Job()
        coroutineJob.let { cJob ->
            if (cJob != null) {
                CoroutineScope(Dispatchers.IO + cJob).launch {
                    try {
                        while (true) {
                            if (mConnection == null) {
                                Log.e(TAG, "Connection is null")
                            } else {
                                //Handle incoming data
                                mReadBuffer.let { readBuffer ->
                                    if (readBuffer != null) {
                                        val buffer: ByteArray? = readBuffer.array()
                                        val len = read(buffer, 0)
                                        if (len > 0) {
                                            val data = ByteArray(len)
                                            System.arraycopy(buffer, 0, data, 0, len)
                                            Log.v(TAG, "Read Data : " + String(data))
                                            receiveTransmittedData(data)
                                        } else {
                                            Log.e(TAG, "launchCoroutine: Len is less than 0")
                                            //usbHelperListener.onConnectionError("Len is less than 0")
                                        }
                                    } else {
                                        Log.e(TAG, "Read Buffer is null")
                                        usbHelperListener.onConnectionError("Failed to read data, read buffer is null")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Run ending due to exception: " + e.message)
                        //usbHelperListener.onConnectionError("Run ending due to exception: " + e.message)
                    }
                }
            } else {
                Log.e(TAG, "launchCoroutine: Coroutine Job is null")
                usbHelperListener.onConnectionError("Coroutine job is null")
            }
        }
    }

    @Throws(IOException::class)
    private fun write(src: ByteArray?, timeout: Int, comesFrom: String) {
        if (mConnection == null) {
            throw IOException("Connection closed")
        } else {
            mConnection.let { connection ->
                connection?.bulkTransfer(mWriteEndpoint, src, src!!.size, timeout) ?: {
                    Log.e(TAG, "write: Connection is null")
                    usbHelperListener.onConnectionError("Connection is null")
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun read(dest: ByteArray?, timeout: Int): Int {
        if (mConnection == null) throw IOException("Connection closed")

        require(dest!!.isNotEmpty()) { "Read buffer to small" }
        val nread: Int
        val buf = ByteBuffer.wrap(dest)
        mUsbRequest.let { usbRequest ->
            if (usbRequest != null) {
                if (!usbRequest.queue(buf, dest.size)) {
                    throw IOException("Queueing USB request failed")
                } else {
                }
            } else {
                Log.e(TAG, "read: UsbRequest is Null")
                usbHelperListener.onConnectionError("UsbRequest is null")
            }
        }
        mConnection.let { connection ->
            if (connection != null) {
                connection.requestWait() ?: throw IOException("Waiting for USB request failed")
            } else {
                Log.e(TAG, "read: Connection is null")
                usbHelperListener.onConnectionError("Connection is null")
            }
        }
        nread = buf.position()
        return max(nread, 0)
    }

    private fun send(str: String) {
        if (mConnection == null) {
            Log.d(TAG, "Device disconnected...")
            return
        }
        try {
            val data = str.toByteArray()
            write(data, 2000, "Send $str")
        } catch (e: Exception) {
            Log.e(TAG, "send: Write method : " + e.message)
            usbHelperListener.onConnectionError(e.message)
        }
    }

    private fun sendCustomCommand(str: String, time: Int) {
        if (mConnection == null) {
            Log.d(TAG, "Device disconnected...")
            showToast("Device Disconnected...")
            return
        } else {
            counter = 0
            val thread = Thread {
                command = ConstantHelper.START_KEY
                send(command)
                try {
                    Thread.sleep((time * 1000).toLong())
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                command = ConstantHelper.STOP_KEY
                send(command)
            }
            thread.start()
        }
    }

    private fun disconnect() {
        Log.e(TAG, "Device has been disconnected...")

        //set stringBuilder length to 0
        stringBuilder.setLength(0)

        //close coroutine
        coroutineJob.let { cJob ->
            if (cJob != null && cJob.isActive) {
                cJob.cancel()
            } else {
                Log.e(TAG, "disconnect: Coroutine Job is null")
            }
        }
        //if (coroutineJob != null && coroutineJob!!.isActive) coroutineJob!!.cancel()

        //release interface
        mConnection.let { connection ->
            if (connection != null) {
                connection.releaseInterface(mControlInterface)
                mControlInterface = null
                mReadEndpoint = null
                mWriteEndpoint = null
            } else {
                Log.e(TAG, "disconnect: Connection is null")
            }
        }
        /*mConnection!!.releaseInterface(mControlInterface)
        mControlInterface = null
        mReadEndpoint = null
        mWriteEndpoint = null*/

        //reset all permission
        usbPermission = UsbPermission.Unknown

        //notify user to device disconnect
        usbHelperListener.onDeviceDisconnect()

        //unregister receiver
        context.unregisterReceiver(usbReceiver)
    }

    private var counter: Int = 0

    private fun receiveTransmittedData(data: ByteArray) {
        Log.i(
            TAG,
            "receiveTransmittedData: " + System.currentTimeMillis() + " : " + String(data) + " : " + command
        )

        val spn = SpannableStringBuilder()
        if (data.isNotEmpty()) spn.append(String(data))

        /*//REQUEST TO UNLOCK
        if (command == ConstantHelper.REQUEST_TO_UNLOCK) {
            stringBuilder.append(spn)
            if (stringBuilder.length == 99) {
                val deviceId = stringBuilder.substring(7, 23)//Get Device Id
                val microControllerId = stringBuilder.substring(23, 35)//Get MID
                deviceHashValue = stringBuilder.substring(35, stringBuilder.length)//Get Device HashValue
                try {
                    //Generate Hash Value
                    generatedHashValue =
                        toHexString(getSHA(deviceId + microControllerId + ConstantHelper.NOISE))
                    if (generatedHashValue == deviceHashValue) {
                        //mainLooper!!.post { usbHelperListener!!.onTransmission("Device Unlocked...") }
                        stringBuilder.setLength(0)
                        //REQUEST FOR CONNECT
                        Log.w(TAG, "receiveTransmittedData: Generated Hash Value : $generatedHashValue")

                        //REQUEST FOR CONNECT
                        command = ConstantHelper.REQUEST_TO_CONNECT + generatedHashValue
                        send(command)

                    } else {
                        mainLooper!!.post { usbHelperListener.onConnectionError("Unauthentic Device...") }
                    }
                } catch (e: NoSuchAlgorithmException) {
                    e.printStackTrace()
                    Log.e(TAG, "Error : " + e.message)
                }
            }
        }
        else if (command == ConstantHelper.REQUEST_TO_CONNECT + generatedHashValue) {
            stringBuilder.append(spn)
            if (stringBuilder.length == 71) {
                val receivedHashValue = stringBuilder.substring(7, 71)
                if (generatedHashValue == receivedHashValue) {
                    mainLooper!!.post { usbHelperListener.onDeviceVerified(true) }
                } else {
                    mainLooper!!.post { usbHelperListener.onConnectionError("Invalid Hash Value Device...") }
                }
                stringBuilder.setLength(0)
            }
        }
        else if (command == ConstantHelper.START_KEY) {
            val cmd = command

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
                    returnDataToUser(result, 0, result.size - 1, cmd)
                    stringBuilder.delete(0, stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1)
                } else {
                    returnDataToUser(result, 0, result.size, cmd)
                    stringBuilder.delete(0, stringBuilder.length)
                }
            }

            //WITHOUT DELIMITER
            //val time = ++counter
            //Log.w(TAG, "receiveTransmittedData: Data $spn \tCounter : $time")
            //usbHelperListener!!.onTransmission("$spn \tCounter : $time")

        } else if (command == ConstantHelper.STOP_KEY) {
            val time = ++counter
            //Log.e(TAG, "receiveTransmittedData: " + command + " : " + System.currentTimeMillis() + "\tCounter : " + time)
            //usbHelperListener.onTransmission("Data : Stop -- " + spn + " : " + "\tData Length : " + spn.length() + "\tExact Length : " + time);
        }
        else {
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
                    returnDataToUser(result, 0, result.size - 1, "")
                    stringBuilder.delete(0, stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1)
                } else {
                    returnDataToUser(result, 0, result.size, "")
                    stringBuilder.delete(0, stringBuilder.length)
                }
            }

            //Log.w(TAG, "receiveTransmittedData: Data $spn \t Counter : $time")
            //usbHelperListener!!.onTransmission("Data $spn \t Counter : $time");
        }*/

        val cmd = command
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
                returnDataToUser(result, 0, result.size - 1, cmd)
                stringBuilder.delete(0, stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1)
            } else {
                returnDataToUser(result, 0, result.size, cmd)
                stringBuilder.delete(0, stringBuilder.length)
            }
        }
    }

    private fun returnDataToUser(result: Array<String>, start: Int, end: Int, command: String) {
        for (i in start until end) {
            val time = ++counter
            Log.w(TAG, "returnDataToUser: Data ${result[i]} \tCounter : $time")
            //usbHelperListener.onTransmission("${result[i]} \tCounter : $time")
            usbHelperListener.onTransmission(result[i])
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * ACCESSIBLE METHODS
     */
    companion object {
        private const val INTENT_ACTION_GRANT_USB = "com.example.device_detect.GRANT_USB"
        private var usbSerialCommunicationHelper: UsbSerialCommunicationHelper? = null

        @JvmStatic
        fun getInstance(context: Context): UsbSerialCommunicationHelper? {
            if (usbSerialCommunicationHelper == null) {
                usbSerialCommunicationHelper = UsbSerialCommunicationHelper(context)
            }
            return usbSerialCommunicationHelper
        }
    }

    fun setCommunication(usbHelperListener: UsbHelperListener?) {
        initRegister()
        if (usbHelperListener != null) {
            this.usbHelperListener = usbHelperListener
        }

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
        command = when (cmd) {
            ConstantHelper.REQUEST_TO_UNLOCK -> ConstantHelper.REQUEST_TO_UNLOCK
            ConstantHelper.REQUEST_TO_CONNECT -> ConstantHelper.REQUEST_TO_CONNECT
            ConstantHelper.START_KEY -> ConstantHelper.START_KEY
            ConstantHelper.STOP_KEY -> ConstantHelper.STOP_KEY
            else -> cmd
        }
        send(command)
    }

    fun onSendCustomCommand(cmd: String, timer: Int) {
        sendCustomCommand(cmd, timer)
    }

    fun onStartTransmission() {
        counter = 0
        val thread = Thread {
            command = ConstantHelper.START_KEY
            send(command)
            try {
                Thread.sleep(10000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            command = ConstantHelper.STOP_KEY
            send(command)
        }
        thread.start()
    }

    fun onStopTransmission() {
        command = ConstantHelper.STOP_KEY
        send(command)
    }

    fun removeInstance() {
        disconnect()
    }

    fun startTransmission(command: String) {
        counter = 0
        send(command)
    }

    fun stopTransmission(command: String) {
        send(command)
    }
}