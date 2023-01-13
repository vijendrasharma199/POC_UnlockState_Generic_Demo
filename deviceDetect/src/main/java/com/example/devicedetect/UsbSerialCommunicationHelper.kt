package com.example.devicedetect

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
import com.example.devicedetect.driver.UsbSerialPort
import kotlinx.coroutines.*
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class UsbSerialCommunicationHelper private constructor(private var context: Context) {
    enum class UsbPermission {
        Unknown, Requested, Granted
    }

    private var shouldLogDifference = false
    private var difference = 0
    private var TAG = "USB_COMMUNICATION_HELPER"
    private var usbPermission = UsbPermission.Unknown
    private var mainLooper: Handler? = null
    private var connected = false
    private var portNum = ConstantHelper.PORT_NUMBER
    private var usbIoManager: SerialInputOutputManager? = null
    private var usbSerialPort: UsbSerialPort? = null

    //Required Variables
    private var mControlInterface: UsbInterface? = null
    private var mDataInterface: UsbInterface? = null
    private var mControlEndpoint: UsbEndpoint? = null
    private var mReadEndpoint: UsbEndpoint? = null
    private var mWriteEndpoint: UsbEndpoint? = null
    private var mUsbRequest: UsbRequest? = null
    private var mControlIndex = 0

    //UsbHelperListener
    private var usbHelperListener: UsbHelperListener? = null

    //Custom implementation
    private var mConnection: UsbDeviceConnection? = null
    private val mReadBufferLock = Any()
    private val mWriteBufferLock = Any()
    private var mReadBuffer: ByteBuffer? = null
    private val mWriteBuffer = ByteBuffer.allocate(4096)
    private lateinit var mWriteBufferByte: ByteArray
    private var coroutineJob: CompletableJob? = null

    //Interface Receive data
    private var command = ""
    private var stringBuilder = StringBuilder()
    private var deviceHashValue = ""
    private var generatedHashValue = ""

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
                ) UsbPermission.Granted else UsbPermission.Unknown
                connect()
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.d(TAG, "Device Connected...")
                usbHelperListener!!.onDeviceConnect()
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                Log.d(TAG, "Device Disconnected...")
                disconnect()
            }
        }
    }

    private fun connect() {
        counter = 0
        var device: UsbDevice? = null
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        for (v in usbManager.deviceList.values) {
            Log.e("DevicesCount List", "connect: " + v.vendorId)
            if (v.vendorId == ConstantHelper.spandanVendorId
                || v.vendorId == ConstantHelper.spandanVendorId1
                || v.vendorId == ConstantHelper.spandanVendorId2
            ) {
                device = v
                Log.e("DevicesCount Catch", "connect: " + device.vendorId)
                break;
            }
        }

        if (device == null) {
            Log.e(TAG, "connection failed: device not found")
            usbHelperListener!!.onConnectionError("connection failed: device not found")
            return
        }

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
            if (!usbManager.hasPermission(device)) {
                Log.e(TAG, "connection failed: permission denied")
                usbHelperListener!!.onConnectionError("connection failed: permission denied")
            } else {
                Log.e(TAG, "connection failed: open failed")
                usbHelperListener!!.onConnectionError("connection failed: open failed")
            }
            return
        } else {
            mConnection = usbConnection
        }
        Log.e(TAG, "connect: " + usbManager.hasPermission(device) + " : " + device.vendorId)

        //Find Interfaces and Endpoint of Device
        findInterfaceAndEndpointOfDevice(device)
    }

    private fun findInterfaceAndEndpointOfDevice(device: UsbDevice) {
        try {
            //OPEN SINGLE INTERFACE
            if (device.vendorId == ConstantHelper.spandanVendorId1) {
                mControlInterface = device.getInterface(0)
                mDataInterface = device.getInterface(0)
                if (!mConnection!!.claimInterface(mControlInterface, true)) {
                    Log.w(
                        TAG,
                        "findInterfaceOfDevice: Could not claim shared control/data interface"
                    )
                    showToast("Could not claim shared control/data interface")
                }
                for (i in 0 until mControlInterface!!.endpointCount) {
                    val ep = mControlInterface!!.getEndpoint(i)
                    Log.w(TAG, "findInterfaceOfDevice: $ep")
                    if (ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                        mControlEndpoint = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        mReadEndpoint = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        mWriteEndpoint = ep
                    }
                }
                if (mControlEndpoint == null) {
                    Log.e(TAG, "findInterfaceOfDevice: No control endpoint")
                    showToast("No Control Endpoint")
                } else {
                    Log.w(TAG, "findInterfaceOfDevice: $mControlEndpoint")
                }

            }
            //OPEN MULTIPLE INTERFACE POINT
            else {
                //Open interface for find required interface and endpoint
                Log.d(TAG, "claiming interfaces, count=" + device.interfaceCount)
                var controlInterfaceCount = 0
                var dataInterfaceCount = 0
                mControlInterface = null
                mDataInterface = null
                for (i in 0 until device.interfaceCount) {
                    val usbInterface = device.getInterface(i)
                    if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                        if (controlInterfaceCount == 0) {
                            mControlIndex = i
                            mControlInterface = usbInterface
                        }
                        controlInterfaceCount++
                    }
                    if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                        if (dataInterfaceCount == 0) {
                            mDataInterface = usbInterface
                        }
                        dataInterfaceCount++
                    }
                }
                if (mControlInterface == null) {
                    Log.w(TAG, "findInterfaceOfDevice: No control interface")
                    showToast("No control interface")
                }
                Log.d(TAG, "Control iface=$mControlInterface")
                if (!mConnection!!.claimInterface(mControlInterface, true)) {
                    Log.w(TAG, "findInterfaceOfDevice: Could not claim control interface")
                    showToast("Could not claim control interface")
                }
                mControlEndpoint = mControlInterface!!.getEndpoint(0)
                if (mControlEndpoint!!.direction != UsbConstants.USB_DIR_IN || mControlEndpoint!!.type != UsbConstants.USB_ENDPOINT_XFER_INT) {
                    Log.w(TAG, "findInterfaceOfDevice: Invalid control endpoint")
                    showToast("Invalid control endpoint")
                }
                if (mDataInterface == null) {
                    Log.w(TAG, "findInterfaceOfDevice: No data interface")
                    showToast("No data interface")
                }
                Log.d(TAG, "data iface=$mDataInterface")
                if (!mConnection!!.claimInterface(mDataInterface, true)) {
                    Log.w(TAG, "findInterfaceOfDevice: Could not claim data interface")
                    showToast("Could not claim data interface")
                }
                for (i in 0 until mDataInterface!!.endpointCount) {
                    val ep = mDataInterface!!.getEndpoint(i)
                    if (ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) mReadEndpoint =
                        ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) mWriteEndpoint =
                        ep
                }
                if (mReadEndpoint == null || mWriteEndpoint == null) {
                    Log.e(TAG, "Could not get read & write endpoints")
                    showToast("Could not get read and write endpoint")
                } else {
                    //Log.w(
                    //TAG,
                    //"findInterfaceOfDevice: \nReadEndPoint$mReadEndpoint \nWriteEndPoint $mWriteEndpoint"
                    //)
                }
            }

            //allocate size of read endpoint
            mReadBuffer = ByteBuffer.allocate(mReadEndpoint!!.maxPacketSize)
            mUsbRequest = UsbRequest()
            mUsbRequest!!.initialize(mConnection, mReadEndpoint)

            //it will work when we have single interface
            val baudRate = ConstantHelper.BAUD_RATE
            val dataBits = 8
            val stopBitsByte: Byte = 1
            val parityBitesByte: Byte = 0
            val setLineCoding = 0x20
            val USB_RT_ACM = UsbConstants.USB_TYPE_CLASS or 0x01
            val buf = byteArrayOf(
                (baudRate and 0xff).toByte(),
                (baudRate shr 8 and 0xff).toByte(),
                (baudRate shr 16 and 0xff).toByte(),
                (baudRate shr 24 and 0xff).toByte(),
                stopBitsByte,
                parityBitesByte,
                dataBits.toByte()
            )

            //sendAcmControlMessage(setLineCoding, 0, msg);
            val len = mConnection!!.controlTransfer(
                USB_RT_ACM,
                setLineCoding,
                0,
                mControlIndex,
                buf,
                buf.size,
                5000
            )
            if (len < 0) {
                Log.w(TAG, "findInterfaceOfDevice: controlTransfer failed")
                showToast("Control transfer failed")
            }

            launchCoroutine()
        } catch (e: Exception) {
            showToast(e.toString())
        }
        /*//OPEN SINGLE INTERFACE
        if (device.vendorId == ConstantHelper.spandanVendorId1) {
            mControlInterface = device.getInterface(0)
            mDataInterface = device.getInterface(0)
            if (!mConnection!!.claimInterface(mControlInterface, true)) {
                Log.w(TAG, "findInterfaceOfDevice: Could not claim shared control/data interface")
                showToast("Could not claim shared control/data interface")
            }
            for (i in 0 until mControlInterface!!.endpointCount) {
                val ep = mControlInterface!!.getEndpoint(i)
                Log.w(TAG, "findInterfaceOfDevice: $ep")
                if (ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                    mControlEndpoint = ep
                } else if (ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    mReadEndpoint = ep
                } else if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    mWriteEndpoint = ep
                }
            }
            if (mControlEndpoint == null) {
                Log.e(TAG, "findInterfaceOfDevice: No control endpoint")
                showToast("No Control Endpoint")
            } else {
                Log.w(TAG, "findInterfaceOfDevice: $mControlEndpoint")
            }

        }
        //OPEN MULTIPLE INTERFACE POINT
        else {
            //Open interface for find required interface and endpoint
            Log.d(TAG, "claiming interfaces, count=" + device.interfaceCount)
            var controlInterfaceCount = 0
            var dataInterfaceCount = 0
            mControlInterface = null
            mDataInterface = null
            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                    if (controlInterfaceCount == 0) {
                        mControlIndex = i
                        mControlInterface = usbInterface
                    }
                    controlInterfaceCount++
                }
                if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                    if (dataInterfaceCount == 0) {
                        mDataInterface = usbInterface
                    }
                    dataInterfaceCount++
                }
            }
            if (mControlInterface == null) {
                Log.w(TAG, "findInterfaceOfDevice: No control interface")
                showToast("No control interface")
            }
            Log.d(TAG, "Control iface=$mControlInterface")
            if (!mConnection!!.claimInterface(mControlInterface, true)) {
                Log.w(TAG, "findInterfaceOfDevice: Could not claim control interface")
                showToast("Could not claim control interface")
            }
            mControlEndpoint = mControlInterface!!.getEndpoint(0)
            if (mControlEndpoint!!.direction != UsbConstants.USB_DIR_IN || mControlEndpoint!!.type != UsbConstants.USB_ENDPOINT_XFER_INT) {
                Log.w(TAG, "findInterfaceOfDevice: Invalid control endpoint")
                showToast("Invalid control endpoint")
            }
            if (mDataInterface == null) {
                Log.w(TAG, "findInterfaceOfDevice: No data interface")
                showToast("No data interface")
            }
            Log.d(TAG, "data iface=$mDataInterface")
            if (!mConnection!!.claimInterface(mDataInterface, true)) {
                Log.w(TAG, "findInterfaceOfDevice: Could not claim data interface")
                showToast("Could not claim data interface")
            }
            for (i in 0 until mDataInterface!!.endpointCount) {
                val ep = mDataInterface!!.getEndpoint(i)
                if (ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) mReadEndpoint =
                    ep
                if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) mWriteEndpoint =
                    ep
            }
            if (mReadEndpoint == null || mWriteEndpoint == null) {
                Log.e(TAG, "Could not get read & write endpoints")
                showToast("Could not get read and write endpoint")
            } else {
                //Log.w(
                //TAG,
                //"findInterfaceOfDevice: \nReadEndPoint$mReadEndpoint \nWriteEndPoint $mWriteEndpoint"
                //)
            }
        }

        //allocate size of read endpoint
        mReadBuffer = ByteBuffer.allocate(mReadEndpoint!!.maxPacketSize)
        mUsbRequest = UsbRequest()
        mUsbRequest!!.initialize(mConnection, mReadEndpoint)

        //it will work when we have single interface
        val baudRate = ConstantHelper.BAUD_RATE
        val dataBits = 8
        val stopBitsByte: Byte = 1
        val parityBitesByte: Byte = 0
        val setLineCoding = 0x20
        val USB_RT_ACM = UsbConstants.USB_TYPE_CLASS or 0x01
        val buf = byteArrayOf(
            (baudRate and 0xff).toByte(),
            (baudRate shr 8 and 0xff).toByte(),
            (baudRate shr 16 and 0xff).toByte(),
            (baudRate shr 24 and 0xff).toByte(),
            stopBitsByte,
            parityBitesByte,
            dataBits.toByte()
        )

        //sendAcmControlMessage(setLineCoding, 0, msg);
        val len = mConnection!!.controlTransfer(
            USB_RT_ACM,
            setLineCoding,
            0,
            mControlIndex,
            buf,
            buf.size,
            5000
        )
        if (len < 0) {
            Log.w(TAG, "findInterfaceOfDevice: controlTransfer failed")
            showToast("Control transfer failed")
        }

        launchCoroutine()*/
        //verifyDevice();
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun launchCoroutine() {
        coroutineJob = Job()
        CoroutineScope(Dispatchers.IO + coroutineJob!!).launch {
            try {
                Log.w(TAG, "launchCoroutine: " + Thread.currentThread().name)
                while (true) {
                    if (mConnection == null) {
                        Log.e(TAG, "run: Connection is null Or connection lost")
                    } else {
                        //Handle incoming data
                        var buffer: ByteArray?
                        //synchronized(mReadBufferLock) { buffer = mReadBuffer!!.array() }
                        buffer = mReadBuffer!!.array()
                        var len = read(buffer, 0)
                        if (len > 0) {
                            val data = ByteArray(len)
                            System.arraycopy(buffer, 0, data, 0, len)
                            Log.v(TAG, "Read Data : " + String(data))
                            receiveTransmittedData(data)
                        }

                        //Handle outgoing data
                        buffer = null
                        synchronized(mWriteBufferLock) {
                            len = mWriteBuffer.position()
                            if (len > 0) {
                                buffer = ByteArray(len)
                                mWriteBuffer.rewind()
                                mWriteBuffer[buffer, 0, len]
                                mWriteBuffer.clear()
                            }
                        }
                        if (buffer != null) {
                            write(buffer, 2000, "From thread")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Run ending due to exception: " + e.message)
            }
        }
    }

    @Throws(IOException::class)
    private fun write(src: ByteArray?, timeout: Int, comesFrom: String) {
        Log.w(
            TAG, "Start Def. Write : " + System.currentTimeMillis() + " Comes From : " + comesFrom
        )
        if (mConnection == null) {
            throw IOException("Connection closed")
        } else {
            mConnection!!.bulkTransfer(mWriteEndpoint, src, src!!.size, timeout)
        }
        Log.w(TAG, "End Def. Write" + System.currentTimeMillis())
    }

    @Throws(IOException::class)
    private fun read(dest: ByteArray?, timeout: Int): Int {
        return read(dest, timeout, true)
    }

    @Throws(IOException::class)
    private fun read(dest: ByteArray?, timeout: Int, testConnection: Boolean): Int {
        if (mConnection == null) {
            throw IOException("Connection closed")
        }
        require(dest!!.size > 0) { "Read buffer to small" }
        val nread: Int
        val buf = ByteBuffer.wrap(dest)
        if (!mUsbRequest!!.queue(buf, dest.size)) {
            throw IOException("Queueing USB request failed")
        }
        val response =
            mConnection!!.requestWait() ?: throw IOException("Waiting for USB request failed")
        nread = buf.position()
        return Math.max(nread, 0)
    }

    private fun verifyDevice() {
        send(ConstantHelper.REQUEST_TO_UNLOCK.also { command = it })
    }

    private fun send(str: String) {
        if (mConnection == null) {
            Log.d(TAG, "Device disconnected...")
            return
        }
        try {
            val data = """$str""".toByteArray()
            write(data, 2000, "Send $str")
        } catch (e: Exception) {
            usbHelperListener!!.onConnectionError(e.toString())
            Log.e(TAG, "send: Write method : " + e.message)
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
        stringBuilder.setLength(0)
        connected = false
        /*if (usbIoManager != null) {
            usbIoManager!!.shareDataListener = null
            usbIoManager!!.stop()
        }
        usbIoManager = null
        try {
            if (usbSerialPort!!.isOpen) usbSerialPort!!.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }*/
        usbSerialPort = null
        if (coroutineJob!!.isActive)
            coroutineJob!!.cancel()

        usbPermission = UsbPermission.Unknown
        usbHelperListener!!.onDeviceDisconnect()
        context.unregisterReceiver(usbReceiver)
    }

    /**
     * @param data receive from SerialInputOutputManager
     */
    private var counter: Int = 0
    private fun receiveTransmittedData(data: ByteArray) {
        Log.i(
            TAG,
            "receiveTransmittedData: " + System.currentTimeMillis() + " : " + String(data) + " : " + command
        )
        val spn = SpannableStringBuilder()
        //if (data.size > 0) spn.append(String(data))
        if (data.isNotEmpty()) spn.append(String(data))

        //REQUEST TO UNLOCK
        if (command == ConstantHelper.REQUEST_TO_UNLOCK) {
            stringBuilder.append(spn)
            Log.w(
                TAG,
                "Request To Unlock Command : $command Response : $stringBuilder Length : ${stringBuilder.length}"
            )
            if (stringBuilder.length == 99) {
                val deviceId = stringBuilder.substring(7, 23)
                val microControllerId = stringBuilder.substring(23, 35)
                deviceHashValue = stringBuilder.substring(35, stringBuilder.length)
                try {
                    generatedHashValue =
                        toHexString(getSHA(deviceId + microControllerId + ConstantHelper.NOISE))
                    if (generatedHashValue == deviceHashValue) {
                        //mainLooper!!.post { usbHelperListener!!.onTransmission("Device Unlocked...") }
                        stringBuilder.setLength(0)
                        //REQUEST FOR CONNECT
                        Log.w(
                            TAG,
                            "receiveTransmittedData: Generated Hash Value : $generatedHashValue"
                        )

                        //REQUEST FOR CONNECT
                        command = ConstantHelper.REQUEST_TO_CONNECT + generatedHashValue
                        send(command)

                    } else {
                        mainLooper!!.post { usbHelperListener!!.onConnectionError("Unauthentic Device...") }
                    }
                } catch (e: NoSuchAlgorithmException) {
                    e.printStackTrace()
                    Log.e(TAG, "Error : " + e.message)
                }
            }
        } else if (command == ConstantHelper.REQUEST_TO_CONNECT + generatedHashValue) {
            stringBuilder.append(spn)
            Log.e(
                TAG,
                "Request To Connect Command : $command Response : $stringBuilder Length : ${stringBuilder.length} "
            )
            if (stringBuilder.length == 71) {
                val receivedHashValue = stringBuilder.substring(7, 71)
                if (generatedHashValue == receivedHashValue) {
                    mainLooper!!.post { usbHelperListener!!.onDeviceVerified(true) }
                } else {
                    mainLooper!!.post { usbHelperListener!!.onConnectionError("Invalid Hash Value Device...") }
                }
                stringBuilder.setLength(0)
            }
        } else if (command == ConstantHelper.START_KEY) {
            //Log.i(TAG, "Request To START" + "\nCommand : " + command + "\nResponse : " + stringBuilder + "\nLength : " + stringBuilder.length)

            val cmd = command

            /*
            val time: Int = ++counter;
            Log.w(
                TAG,
                "receiveTransmittedData: Data $spn \t Counter : $time \t " + "Extracted Part : ${
                    spn.substring(
                        0, spn.indexOf(" ")
                    )
                }"
            )

            usbHelperListener!!.onTransmission(
                "Data : $spn : \tCounter : $time : " + "Extracted Part : ${
                    spn.substring(
                        0, spn.indexOf(" ")
                    )
                }"
            );*/

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

            /*//WITHOUT DELIMITER
            val time = ++counter
            Log.w(TAG, "receiveTransmittedData: Data $spn \tCounter : $time")
            usbHelperListener!!.onTransmission("$spn \tCounter : $time")*/

        } else if (command == ConstantHelper.STOP_KEY) {
            //Log.e(TAG, "Request To STOP" + "\nCommand : " + command + "\nResponse : " + stringBuilder + "\nLength : " + stringBuilder.length());
            val time = ++counter
            //Log.e(TAG, "receiveTransmittedData: " + command + " : " + System.currentTimeMillis() + "\tCounter : " + time)
            //usbHelperListener.onTransmission("Data : Stop -- " + spn + " : " + "\tData Length : " + spn.length() + "\tExact Length : " + time);
        } else {
            /*val time = ++counter;
            usbHelperListener!!.onTransmission("Data : -- " + spn + " : " + "\tData Length : " + spn.length() + "\tExact Length : " + time);*/

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
        }
    }

    private fun returnDataToUser(result: Array<String>, start: Int, end: Int, command: String) {
        for (i in start until end) {
            val time = ++counter
            Log.w(TAG, "receiveTransmittedData: Data ${result[i]} \tCounter : $time")
            usbHelperListener!!.onTransmission("${result[i]} \tCounter : $time")
        }
    }

    /**
     * Outside Accessible Method
     */
    fun setCommunication(usbHelperListener: UsbHelperListener?) {
        initRegister()
        this.usbHelperListener = usbHelperListener
        if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted) mainLooper!!.post { connect() }
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
        sendCustomCommand(cmd, timer);
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

    companion object {
        private const val INTENT_ACTION_GRANT_USB = "com.example.device_detect.GRANT_USB"
        private const val WRITE_WAIT_MILLIS = 2000
        private var usbSerialCommunicationHelper: UsbSerialCommunicationHelper? = null

        @JvmStatic
        fun getInstance(context: Context): UsbSerialCommunicationHelper? {
            if (usbSerialCommunicationHelper == null) {
                usbSerialCommunicationHelper = UsbSerialCommunicationHelper(context)
            }
            return usbSerialCommunicationHelper
        }

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
    }
}