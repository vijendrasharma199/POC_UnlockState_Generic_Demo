package `in`.sunfox.healthcare.commons.android.sericom

import android.hardware.usb.*
import android.util.Log
import `in`.sunfox.healthcare.commons.android.sericom.Util.ConstantHelper
import `in`.sunfox.healthcare.commons.android.sericom.interfaces.OnConnectionStateChangeListener
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max

class SeriComIOOperation constructor(
    connection: UsbDeviceConnection,
    device: UsbDevice,
    onConnectionStateChangeListener: OnConnectionStateChangeListener
) {

    private var TAG = javaClass.simpleName

    //UsbDeviceConnection
    private var mConnection: UsbDeviceConnection? = null

    //UsbHelperListener
    private var listener: OnConnectionStateChangeListener

    //UsbDevice
    private var device: UsbDevice

    //UsbInterface
    private var mControlInterface: UsbInterface? = null

    //UsbEndpoint
    private var mReadEndpoint: UsbEndpoint? = null
    private var mWriteEndpoint: UsbEndpoint? = null

    //UsbRequest
    private var mUsbRequest: UsbRequest? = null

    //ReadBuffer
    private var mReadBuffer: ByteBuffer? = null

    //UsbControlIndex for USB_COMM
    private var mControlIndex = 0

    //Command
    private var command = ""

    init {
        this.mConnection = connection
        this.listener = onConnectionStateChangeListener
        this.device = device

        //getInterfacesAndEndpoints
        getRequiredInterfacesAndEndpointsOfDevice()
    }

    /**
     * Write
     * @param cmd: Command entered by user
     * @param timeout: timeout
     */
    @Throws(IOException::class)
    fun write(cmd: String, timeout: Int) {
        if (mConnection == null) {
            throw IOException("Connection closed")
        } else {
            //init command
            command = cmd

            val src = cmd.toByteArray()
            mConnection.let { connection ->
                connection?.bulkTransfer(mWriteEndpoint, src, src.size, timeout) ?: {
                    Log.e(TAG, "write: Connection is null")
                    listener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : Connection is null")
                }
            }
        }
    }

    /**
     * Read
     * @param dest: ByteArray for store data
     */
    @Throws(IOException::class)
    fun read(dest: ByteArray?): Int {
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
                listener.onConnectionError("${ConstantHelper.ErrorCode.USB_REQUEST} : UsbRequest is null")
            }
        }
        mConnection.let { connection ->
            if (connection != null) {
                connection.requestWait() ?: throw IOException("Waiting for USB request failed")
            } else {
                Log.e(TAG, "read: Connection is null")
                listener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : Connection is null")
            }
        }
        nread = buf.position()
        return max(nread, 0)
    }

    /**
     * Get Required Interfaces and Endpoint
     */
    private fun getRequiredInterfacesAndEndpointsOfDevice() {
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
                    listener.onConnectionError("${ConstantHelper.ErrorCode.ENDPOINT} : Could not claim data interface")
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
                        Log.e(TAG, "Control Interface is null")
                        listener.onConnectionError("${ConstantHelper.ErrorCode.ENDPOINT} : Control Interface is null")
                    }
                }

                /*val buf = byteArrayOf(
                    (ConstantHelper.BAUD_RATE and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 8 and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 16 and 0xff).toByte(),
                    (ConstantHelper.BAUD_RATE shr 24 and 0xff).toByte(),
                    ConstantHelper.STOP_BIT.toByte(),//Stop Bits
                    ConstantHelper.PARITY_BIT.toByte(),//Parity Bits
                    ConstantHelper.DATA_BIT.toByte()//Data Bits
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
                */

                Log.w(
                    TAG,
                    "getRequiredInterfacesAndEndpointsOfDevice: " + "\nBAUD_RATE: ${SeriCom.BAUD_RATE}" + "\nDATA_BIT: ${SeriCom.DATA_BITS}" + "\nSTOP_BIT: ${SeriCom.STOP_BIT}" + "\nPARITY_BIT: ${SeriCom.PARITY_BIT}" + "\nREQUEST_CODE: ${SeriCom.REQUEST_CODE}"
                )
                val buf = byteArrayOf(
                    (SeriCom.BAUD_RATE and 0xff).toByte(),
                    (SeriCom.BAUD_RATE shr 8 and 0xff).toByte(),
                    (SeriCom.BAUD_RATE shr 16 and 0xff).toByte(),
                    (SeriCom.BAUD_RATE shr 24 and 0xff).toByte(),
                    SeriCom.STOP_BIT.toByte(),//Stop Bits
                    SeriCom.PARITY_BIT.toByte(),//Parity Bits
                    SeriCom.DATA_BITS.toByte()//Data Bits
                )

                val result: Int = connection.controlTransfer(
                    UsbConstants.USB_TYPE_CLASS or 0x01, SeriCom.REQUEST_CODE,//0x22 or 0x20
                    0, mControlIndex, buf, buf.size, 0
                )
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
                                listener.onConnectionError("${ConstantHelper.ErrorCode.USB_REQUEST} : UsbRequest is null")
                                Log.e(
                                    TAG,
                                    "getRequiredInterfacesAndEndpointsOfDevice: UsbRequest is null"
                                )
                            }
                        }
                    } else {
                        Log.e(TAG, "ReadEndPoint is null")
                        listener.onConnectionError("${ConstantHelper.ErrorCode.ENDPOINT} : ReadEndPoint is null")
                    }
                }
            } else {
                Log.e(TAG, "Connection is null")
                listener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : Connection is null")
            }
        }
    }

    /**
     * Return readBuffer
     */
    fun getReadBuffer(): ByteBuffer? {
        return mReadBuffer
    }

    /**
     * release Interface
     */
    fun releaseControl() {
        mConnection.let { connection ->
            if (connection != null) connection.releaseInterface(mControlInterface)
            else {
                Log.e(TAG, "releaseControl: Connection is null")
                listener.onConnectionError("${ConstantHelper.ErrorCode.CONNECTION} : Connection is null")
            }
        }
    }
}