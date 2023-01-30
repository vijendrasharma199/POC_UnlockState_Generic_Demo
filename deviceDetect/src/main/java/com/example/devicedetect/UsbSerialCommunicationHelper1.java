package com.example.devicedetect;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.util.Log;

import com.example.devicedetect.driver.UsbSerialPort;
import com.example.devicedetect.util.MonotonicClock;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UsbSerialCommunicationHelper1 implements Runnable {

    private enum UsbPermission {Unknown, Requested, Granted}

    String TAG = "USB_COMMUNICATION_HELPER";
    private static final String INTENT_ACTION_GRANT_USB = "com.example.device_detect.GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    Context context;

    UsbPermission usbPermission = UsbPermission.Unknown;

    Handler mainLooper;

    boolean connected = false;

    int portNum = ConstantHelper.PORT_NUMBER;

    SerialInputOutputManager usbIoManager;

    UsbSerialPort usbSerialPort;

    //Required Variables
    UsbInterface mControlInterface, mDataInterface;
    UsbEndpoint mControlEndpoint, mReadEndpoint, mWriteEndpoint;
    UsbRequest mUsbRequest;


    //UsbHelperListener
    UsbHelperListener usbHelperListener;

    //Custom implementation
    UsbDeviceConnection mConnection;
    private final Object mReadBufferLock = new Object();
    private final Object mWriteBufferLock = new Object();

    private ByteBuffer mReadBuffer; // default size = getReadEndpoint().getMaxPacketSize()
    private final ByteBuffer mWriteBuffer = ByteBuffer.allocate(4096);
    byte[] mWriteBufferByte;

    @Override
    public void run() {
        try {
            while (true) {
                //check if connection is not null
                if (mConnection == null) {
                    Log.e(TAG, "run: Connection is null Or connection lost");
                } else {
                    //Handle incoming data
                    //read data
                    byte[] buffer;
                    synchronized (mReadBufferLock) {
                        buffer = mReadBuffer.array();
                    }
                    int len = read(buffer, 0);

                    if (len > 0) {
                        final byte[] data = new byte[len];
                        System.arraycopy(buffer, 0, data, 0, len);
                        Log.v(TAG, "Read Data : " + new String(data));
                        receiveTransmittedData(data);

                        /*final SerialInputOutputManager.ShareDataListener mSListener = usbIoManager.getShareDataListener();
                        if (mSListener != null) {
                            final byte[] data = new byte[len];
                            System.arraycopy(buffer, 0, data, 0, len);
                            int loop = counter;
                            Log.v("TimeLog", "Read Data : " + System.currentTimeMillis() + " \t Data : " + new String(data) + "\t counter : " + loop);
                            mSListener.onTransmissionData(data);
                        }*/
                    }

                    //Handle outgoing data
                    buffer = null;
                    synchronized (mWriteBufferLock) {
                        len = mWriteBuffer.position();
                        if (len > 0) {
                            buffer = new byte[len];
                            mWriteBuffer.rewind();
                            mWriteBuffer.get(buffer, 0, len);
                            mWriteBuffer.clear();
                        }
                    }
                    if (buffer != null) {
                        write(buffer, 0, "From thread");
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Run ending due to exception: " + e.getMessage(), e);
            shareDataListener.onRunError(e);
        }
    }


    public void write(final byte[] src, final int timeout, String comesFrom) throws IOException {
        Log.w(TAG, "Start Def. Write : " + System.currentTimeMillis() + " Comes From : " + comesFrom);
        int offset = 0;
        final long endTime = (timeout == 0) ? 0 : (MonotonicClock.millis() + timeout);

        if (mConnection == null) {
            throw new IOException("Connection closed");
        } else {
            mConnection.bulkTransfer(mWriteEndpoint, src, src.length, timeout);

//            int baudRate = ConstantHelper.BAUD_RATE;
//            int dataBits = 8;
//            byte stopBitsByte = 0;
//            byte parityBitesByte = 0;
//            int setLineCoding = 0x20;
//            int USB_RT_ACM = UsbConstants.USB_TYPE_CLASS | 0x01;
//            byte[] buf = {(byte) (baudRate & 0xff), (byte) ((baudRate >> 8) & 0xff), (byte) ((baudRate >> 16) & 0xff), (byte) ((baudRate >> 24) & 0xff), stopBitsByte, parityBitesByte, (byte) dataBits};
//            mConnection.controlTransfer(UsbConstants.USB_TYPE_CLASS | 0*01, 0*)
        }
        Log.w(TAG, "End Def. Write" + System.currentTimeMillis());
        /*while (offset < src.length) {
            int requestTimeout;
            final int requestLength;
            final int actualLength;

            synchronized (mWriteBufferLock) {
                final byte[] writeBuffer;

                if (mWriteBufferByte == null) {
                    mWriteBufferByte = new byte[mWriteEndpoint.getMaxPacketSize()];
                }
                requestLength = Math.min(src.length - offset, mWriteBufferByte.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    // bulkTransfer does not support offsets, make a copy.
                    System.arraycopy(src, offset, mWriteBufferByte, 0, requestLength);
                    writeBuffer = mWriteBufferByte;
                }
                if (timeout == 0 || offset == 0) {
                    requestTimeout = timeout;
                } else {
                    requestTimeout = (int) (endTime - MonotonicClock.millis());
                    if (requestTimeout == 0) requestTimeout = -1;
                }
                if (requestTimeout < 0) {
                    actualLength = -2;
                } else {
                    actualLength = mConnection.bulkTransfer(mWriteEndpoint, writeBuffer, requestLength, requestTimeout);
                }
            }
            if (true) {
                //Log.d(TAG, "Wrote " + actualLength + "/" + requestLength + " offset " + offset + "/" + src.length + " timeout " + requestTimeout);
                Log.w(TAG, "Inside Debug : " + System.currentTimeMillis() + " : " + actualLength + "/" + requestLength + " offset " + offset + "/" + src.length + " timeout " + requestTimeout);
            }
            if (actualLength <= 0) {
                if (timeout != 0 && MonotonicClock.millis() >= endTime) {
                    SerialTimeoutException ex = new SerialTimeoutException("Error writing " + requestLength + " bytes at offset " + offset + " of total " + src.length + ", rc=" + actualLength);
                    ex.bytesTransferred = offset;
                    throw ex;
                } else {
                    throw new IOException("Error writing " + requestLength + " bytes at offset " + offset + " of total " + src.length);
                }
            }
            offset += actualLength;
            Log.w(TAG, "End Def. Write" + System.currentTimeMillis() + "Actual Length : " + actualLength);
        }*/
    }

    public int read(final byte[] dest, final int timeout) throws IOException {
        return read(dest, timeout, true);
    }

    protected int read(final byte[] dest, final int timeout, boolean testConnection) throws IOException {
        if (mConnection == null) {
            throw new IOException("Connection closed");
        }
        if (dest.length <= 0) {
            throw new IllegalArgumentException("Read buffer to small");
        }

        final int nread;
        final ByteBuffer buf = ByteBuffer.wrap(dest);
        if (!mUsbRequest.queue(buf, dest.length)) {
            throw new IOException("Queueing USB request failed");
        }
        final UsbRequest response = mConnection.requestWait();
        if (response == null) {
            throw new IOException("Waiting for USB request failed");
        }
        nread = buf.position();
        return Math.max(nread, 0);
    }

    //Interface Receive data
    String command = "";
    StringBuilder stringBuilder = new StringBuilder();
    String deviceHashValue = "", generatedHashValue = "";
    SerialInputOutputManager.ShareDataListener shareDataListener = new SerialInputOutputManager.ShareDataListener() {
        @Override
        public void onTransmissionData(byte[] data) {
            Log.w("TimeLog", "onTransmissionData: " + System.currentTimeMillis());
            //receiveTransmittedData(data);
        }

        @Override
        public void onRunError(Exception e) {
            //mainLooper.post(() -> usbHelperListener.onConnectionError(e.getMessage()));
        }
    };

    private static UsbSerialCommunicationHelper1 usbSerialCommunicationHelper = null;

    public static UsbSerialCommunicationHelper1 getInstance(Context context) {
        if (usbSerialCommunicationHelper == null) {
            usbSerialCommunicationHelper = new UsbSerialCommunicationHelper1(context);
        }
        return usbSerialCommunicationHelper;
    }

    private UsbSerialCommunicationHelper1(Context context) {
        this.context = context;
    }

    private void initRegister() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_ACTION_GRANT_USB);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(usbReceiver, filter);
        mainLooper = new Handler(Looper.getMainLooper());
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive: " + action);
            if (INTENT_ACTION_GRANT_USB.equals(action)) {
                usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) ? UsbPermission.Granted : UsbPermission.Unknown;
                connect();
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "Device Connected...");
                usbHelperListener.onDeviceConnect();
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "Device Disconnected...");
                disconnect();
            }
        }
    };

    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values())
            if (v.getVendorId() == ConstantHelper.spandanVendorId || v.getVendorId() == ConstantHelper.spandanVendorId1 || v.getVendorId() == ConstantHelper.spandanVendorId2)
                device = v;

        if (device == null) {
            Log.e(TAG, "connection failed: device not found");
            usbHelperListener.onConnectionError("connection failed: device not found");
            return;
        }

        /*//2. find device driver
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            Log.e(TAG, "connection failed: no driver for device");
            usbHelperListener.onConnectionError("connection failed: no driver for device");
            return;
        }
        if (driver.getPorts().size() < portNum) {
            Log.e(TAG, "connection failed: not enough ports at device");
            usbHelperListener.onConnectionError("connection failed: not enough ports at device");
            return;
        }*/

        //3. find device ports and open the device
        //usbSerialPort = driver.getPorts().get(portNum);

        //4. check permission for usb connection
        //if (usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
        if (usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(device)) {
            usbPermission = UsbPermission.Requested;
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(INTENT_ACTION_GRANT_USB), flags);
            //usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            usbManager.requestPermission(device, usbPermissionIntent);
            return;
        }

        UsbDeviceConnection usbConnection = usbManager.openDevice(device);
        if (usbConnection == null) {
            if (!usbManager.hasPermission(device)) {
                Log.e(TAG, "connection failed: permission denied");
                usbHelperListener.onConnectionError("connection failed: permission denied");
            } else {
                Log.e(TAG, "connection failed: open failed");
                usbHelperListener.onConnectionError("connection failed: open failed");
            }
            return;
        }

        Log.e(TAG, "connect: " + usbManager.hasPermission(device));

        /*try {
            Log.w("Port", "connect: Port Opening : " + System.currentTimeMillis());
            usbSerialPort.open(usbConnection);
            Log.w("Port", "connect: Port Opening : " + System.currentTimeMillis());
            usbSerialPort.setParameters(ConstantHelper.BAUD_RATE, 8, 1, UsbSerialPort.PARITY_NONE);
            usbIoManager = new SerialInputOutputManager(usbSerialPort, shareDataListener);
            usbIoManager.start();
            connected = true;
            usbHelperListener.onDeviceConnect();

            //verify device
            verifyDevice();
        } catch (Exception e) {
            Log.e(TAG, "connection failed: " + e.getMessage());
            disconnect();
        }*/

        //find the interface of given device
        findInterfaceOfDevice(device, usbConnection);
    }

    private void findInterfaceOfDevice(UsbDevice device, UsbDeviceConnection usbConnection) {
        int mControlIndex = 0;
        if (usbConnection == null) {
            Log.w(TAG, "findInterfaceOfDevice: Connection is null");
        } else {
            mConnection = usbConnection;
            if (device.getVendorId() == ConstantHelper.spandanVendorId1) {
                //open single interface
                mControlInterface = device.getInterface(0);
                mDataInterface = device.getInterface(0);
                if (!usbConnection.claimInterface(mControlInterface, true)) {
                    Log.w(TAG, "findInterfaceOfDevice: Could not claim shared control/data interface");
                }

                for (int i = 0; i < mControlInterface.getEndpointCount(); ++i) {
                    UsbEndpoint ep = mControlInterface.getEndpoint(i);
                    if ((ep.getDirection() == UsbConstants.USB_DIR_IN) && (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT)) {
                        mControlEndpoint = ep;
                    } else if ((ep.getDirection() == UsbConstants.USB_DIR_IN) && (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                        mReadEndpoint = ep;
                    } else if ((ep.getDirection() == UsbConstants.USB_DIR_OUT) && (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                        mWriteEndpoint = ep;
                    }
                }
                if (mControlEndpoint == null) {
                    Log.e(TAG, "findInterfaceOfDevice: No control endpoint");
                } else {
                    Log.w(TAG, "findInterfaceOfDevice: " + mControlEndpoint);
                }
            } else {
                //Open interface for find required interface and endpoint
                Log.d(TAG, "claiming interfaces, count=" + device.getInterfaceCount());
                int controlInterfaceCount = 0;
                int dataInterfaceCount = 0;
                mControlInterface = null;
                mDataInterface = null;
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    UsbInterface usbInterface = device.getInterface(i);
                    if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_COMM) {
                        if (controlInterfaceCount == 0) {
                            mControlIndex = i;
                            mControlInterface = usbInterface;
                        }
                        controlInterfaceCount++;
                    }
                    if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) {
                        if (dataInterfaceCount == 0) {
                            mDataInterface = usbInterface;
                        }
                        dataInterfaceCount++;
                    }
                }

                if (mControlInterface == null) {
                    Log.w(TAG, "findInterfaceOfDevice: No control interface");
                }
                Log.d(TAG, "Control iface=" + mControlInterface);

                if (!mConnection.claimInterface(mControlInterface, true)) {
                    Log.w(TAG, "findInterfaceOfDevice: Could not claim control interface");
                }

                mControlEndpoint = mControlInterface.getEndpoint(0);
                if (mControlEndpoint.getDirection() != UsbConstants.USB_DIR_IN || mControlEndpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
                    Log.w(TAG, "findInterfaceOfDevice: Invalid control endpoint");
                }

                if (mDataInterface == null) {
                    Log.w(TAG, "findInterfaceOfDevice: No data interface");
                }
                Log.d(TAG, "data iface=" + mDataInterface);

                if (!mConnection.claimInterface(mDataInterface, true)) {
                    Log.w(TAG, "findInterfaceOfDevice: Could not claim data interface");
                }

                for (int i = 0; i < mDataInterface.getEndpointCount(); i++) {
                    UsbEndpoint ep = mDataInterface.getEndpoint(i);
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)
                        mReadEndpoint = ep;
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)
                        mWriteEndpoint = ep;
                }

                if (mReadEndpoint == null || mWriteEndpoint == null) {
                    Log.e(TAG, "Could not get read & write endpoints");
                } else {
                    Log.w(TAG, "findInterfaceOfDevice: \nReadEndPoint" + mReadEndpoint + " \nWriteEndPoint " + mWriteEndpoint);
                }
            }
            /*
            //open single interface
            mControlInterface = device.getInterface(0);
            mDataInterface = device.getInterface(0);
            if (!usbConnection.claimInterface(mControlInterface, true)) {
                Log.w(TAG, "findInterfaceOfDevice: Could not claim shared control/data interface");
            }

            for (int i = 0; i < mControlInterface.getEndpointCount(); ++i) {
                UsbEndpoint ep = mControlInterface.getEndpoint(i);
                if ((ep.getDirection() == UsbConstants.USB_DIR_IN) && (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT)) {
                    mControlEndpoint = ep;
                } else if ((ep.getDirection() == UsbConstants.USB_DIR_IN) && (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                    mReadEndpoint = ep;
                } else if ((ep.getDirection() == UsbConstants.USB_DIR_OUT) && (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                    mWriteEndpoint = ep;
                }
            }
            if (mControlEndpoint == null) {
                Log.e(TAG, "findInterfaceOfDevice: No control endpoint");
            } else {
                Log.w(TAG, "findInterfaceOfDevice: " + mControlEndpoint);
            }*/

            /*mConnection = usbConnection;
            //Open interface for find required interface and endpoint
            Log.d(TAG, "claiming interfaces, count=" + device.getInterfaceCount());
            int controlInterfaceCount = 0;
            int dataInterfaceCount = 0;
            mControlInterface = null;
            mDataInterface = null;
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface usbInterface = device.getInterface(i);
                if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_COMM) {
                    if (controlInterfaceCount == 0) {
                        mControlIndex = i;
                        mControlInterface = usbInterface;
                    }
                    controlInterfaceCount++;
                }
                if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) {
                    if (dataInterfaceCount == 0) {
                        mDataInterface = usbInterface;
                    }
                    dataInterfaceCount++;
                }
            }

            if (mControlInterface == null) {
                Log.w(TAG, "findInterfaceOfDevice: No control interface");
            }
            Log.d(TAG, "Control iface=" + mControlInterface);

            if (!mConnection.claimInterface(mControlInterface, true)) {
                Log.w(TAG, "findInterfaceOfDevice: Could not claim control interface");
            }

            mControlEndpoint = mControlInterface.getEndpoint(0);
            if (mControlEndpoint.getDirection() != UsbConstants.USB_DIR_IN || mControlEndpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
                Log.w(TAG, "findInterfaceOfDevice: Invalid control endpoint");
            }

            if (mDataInterface == null) {
                Log.w(TAG, "findInterfaceOfDevice: No data interface");
            }
            Log.d(TAG, "data iface=" + mDataInterface);

            if (!mConnection.claimInterface(mDataInterface, true)) {
                Log.w(TAG, "findInterfaceOfDevice: Could not claim data interface");
            }

            for (int i = 0; i < mDataInterface.getEndpointCount(); i++) {
                UsbEndpoint ep = mDataInterface.getEndpoint(i);
                if (ep.getDirection() == UsbConstants.USB_DIR_IN && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)
                    mReadEndpoint = ep;
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)
                    mWriteEndpoint = ep;
            }

            if (mReadEndpoint == null || mWriteEndpoint == null) {
                Log.e(TAG, "Could not get read & write endpoints");
            } else {
                Log.w(TAG, "findInterfaceOfDevice: \nReadEndPoint" + mReadEndpoint + " \nWriteEndPoint " + mWriteEndpoint);
            }*/

            //allocate size of read endpoint
            mReadBuffer = ByteBuffer.allocate(mReadEndpoint.getMaxPacketSize());
            mUsbRequest = new UsbRequest();
            mUsbRequest.initialize(mConnection, mReadEndpoint);

            //start the thread
            //new Thread(this, this.getClass().getSimpleName()).start();

            int baudRate = ConstantHelper.BAUD_RATE;
            int dataBits = 8;
            byte stopBitsByte = 0;
            byte parityBitesByte = 0;
            int setLineCoding = 0x20;
            int USB_RT_ACM = UsbConstants.USB_TYPE_CLASS | 0x01;
            byte[] buf = {(byte) (baudRate & 0xff), (byte) ((baudRate >> 8) & 0xff), (byte) ((baudRate >> 16) & 0xff), (byte) ((baudRate >> 24) & 0xff), stopBitsByte, parityBitesByte, (byte) dataBits};

            //sendAcmControlMessage(setLineCoding, 0, msg);
            int len = mConnection.controlTransfer(USB_RT_ACM, setLineCoding, 0, mControlIndex, buf, buf != null ? buf.length : 0, 5000);
            if (len < 0) {
                Log.w(TAG, "findInterfaceOfDevice: controlTransfer failed");
            }
            Log.w(TAG, "findInterfaceOfDevice: " + len);

            //start the thread
            new Thread(this, this.getClass().getSimpleName()).start();

            //verifyDevice();
        }
    }


    private void verifyDevice() {
        send(command = ConstantHelper.REQUEST_TO_UNLOCK);
    }

    private void send(String str) {
        if (mConnection == null) {
            Log.d(TAG, "Device disconnected...");
            return;
        }
        try {
            byte[] data = (str + '\n').getBytes();
            write(data, 2000, "Send");
        } catch (Exception e) {
            usbHelperListener.onConnectionError(e.toString());
            Log.e(TAG, "send: Write method : " + e.getMessage());
        }

        /*if (!connected) {
            Log.d(TAG, "Device disconnected...");
            return;
        }
        try {
            if (command.equals(ConstantHelper.REQUEST_TO_UNLOCK) || command.equals(ConstantHelper.REQUEST_TO_CONNECT + deviceHashValue)) {
                stringBuilder.setLength(0);
            }
            byte[] data = (str + '\n').getBytes();
            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
        } catch (Exception e) {
            usbHelperListener.onConnectionError(e.toString());
        }*/
    }

    private void disconnect() {
        Log.e(TAG, "Device has been disconnected...");
        stringBuilder.setLength(0);
        connected = false;
        if (usbIoManager != null) {
            usbIoManager.setShareDataListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            if (usbSerialPort.isOpen()) usbSerialPort.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        usbSerialPort = null;
        usbPermission = UsbPermission.Unknown;
        usbHelperListener.onDeviceDisconnect();
        context.unregisterReceiver(usbReceiver);
    }

    /**
     * @param data receive from SerialInputOutputManager
     */
    long counter = 0;

    private void receiveTransmittedData(byte[] data) {
        //Log.w("TimeLog", "receiveTransmittedData: " + System.currentTimeMillis());
        SpannableStringBuilder spn = new SpannableStringBuilder();
        if (data.length > 0) spn.append(new String(data));

        //REQUEST TO UNLOCK
        if (command.equals(ConstantHelper.REQUEST_TO_UNLOCK)) {
            stringBuilder.append(spn);
            Log.w(TAG, "Request To Unlock" + "\nCommand : " + command + "\nResponse : " + stringBuilder + "\nLength : " + stringBuilder.length());

            if (stringBuilder.length() == 99) {
                String deviceId = stringBuilder.substring(7, 23);
                String microControllerId = stringBuilder.substring(23, 35);
                deviceHashValue = stringBuilder.substring(35, stringBuilder.length());

                try {
                    generatedHashValue = toHexString(getSHA(deviceId + microControllerId + ConstantHelper.NOISE));
                    if (generatedHashValue.equals(deviceHashValue)) {
                        mainLooper.post(() -> usbHelperListener.onTransmission("Device Unlocked..."));
                        stringBuilder.setLength(0);
                        //REQUEST FOR CONNECT
                        send(command = ConstantHelper.REQUEST_TO_CONNECT + generatedHashValue);
                    } else {
                        mainLooper.post(() -> usbHelperListener.onConnectionError("Unauthentic Device..."));
                    }
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Error : " + e.getMessage());
                }
            }
        }
        //REQUEST TO CONNECT
        else if (command.equals(ConstantHelper.REQUEST_TO_CONNECT + generatedHashValue)) {
            stringBuilder.append(spn);
            Log.e(TAG, "Request To Connect" + "\nCommand : " + command + "\nResponse : " + stringBuilder + "\nLength : " + stringBuilder.length());

            if (stringBuilder.length() == 71) {
                String receivedHashValue = stringBuilder.substring(7, 71);
                if (generatedHashValue.equals(receivedHashValue)) {
                    mainLooper.post(() -> usbHelperListener.onDeviceVerified(true));
                } else {
                    mainLooper.post(() -> usbHelperListener.onConnectionError("Invalid Hash Value Device..."));
                }
                stringBuilder.setLength(0);
            }
        }
        //REQUEST FOR START
        else if (command.equals(ConstantHelper.START_KEY)) {
            //Log.i(TAG, "Request To START" + "\nCommand : " + command + "\nResponse : " + stringBuilder + "\nLength : " + stringBuilder.length());
            String cmd = command;
            long time = 0;
            //long time = ++counter;
            //Log.e(TAG, "receiveTransmittedData : " + cmd + " : " + System.currentTimeMillis() + "\tCounter : " + time);
            //usbHelperListener.onTransmission("Data : " + spn + "\t" + command + " : " + System.currentTimeMillis() + "\tCounter : " + time);
            //usbHelperListener.onTransmission("Data : " + spn + " : " + "\tCounter : " + time);

            stringBuilder.append(spn);
            if (stringBuilder.length() > 0 || stringBuilder.toString().contains(ConstantHelper.DELIMITER)) {
                String[] result = stringBuilder.toString().split(ConstantHelper.DELIMITER);
                String lastElementOfResult = result[result.length - 1];
                String lastElementOfBuilder = stringBuilder.substring((stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1), stringBuilder.length());
                if (lastElementOfResult.equals(lastElementOfBuilder)) {
                    returnDataToUser(result, 0, result.length - 1, cmd, time);
                    stringBuilder.delete(0, stringBuilder.lastIndexOf(ConstantHelper.DELIMITER) + 1);
                } else {
                    returnDataToUser(result, 0, result.length, cmd, time);
                    stringBuilder.delete(0, stringBuilder.length());
                }
            }
        }
        //REQUEST FOR STOP
        else if (command.equals(ConstantHelper.STOP_KEY)) {
            //Log.e(TAG, "Request To STOP" + "\nCommand : " + command + "\nResponse : " + stringBuilder + "\nLength : " + stringBuilder.length());
            long time = ++counter;
            Log.e(TAG, "receiveTransmittedData: " + command + " : " + System.currentTimeMillis() + "\tCounter : " + time);
            //usbHelperListener.onTransmission("Data : Stop -- " + spn + " : " + "\tData Length : " + spn.length() + "\tExact Length : " + time);
        }
        //OTHER COMMAND
        else {
            long time = ++counter;
            //usbHelperListener.onTransmission("Data : -- " + spn + " : " + "\tData Length : " + spn.length() + "\tExact Length : " + time);
        }
    }

    private void returnDataToUser(String[] result, int start, int end, String command, long time1) {
        for (int i = start; i < end; i++) {
            long time = ++counter;
            Log.e(TAG, "receiveTransmittedData : " + command + " : " + System.currentTimeMillis() + "\tCounter : " + time);
            //usbHelperListener.onTransmission("Data : " + spn + "\t" + command + " : " + System.currentTimeMillis() + "\tCou
            usbHelperListener.onTransmission(result[i] + "\t" + " : " + System.currentTimeMillis() + "\tCounter : " + time);
            /*if (result[i].matches("[0-9]+")) {
                long time = ++counter;
                usbHelperListener.onTransmission(result[i] + "\t" + " : " + System.currentTimeMillis() + "\tCounter : " + time);
            } else Log.w(TAG, "Invalid Data" + result[i]);*/
        }
    }

    /**
     * @param input
     * @return SHA 256 of given input
     * @throws NoSuchAlgorithmException
     */
    public static byte[] getSHA(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param hash as byte array
     * @return hex string of given input
     */
    public static String toHexString(byte[] hash) {
        BigInteger number = new BigInteger(1, hash);
        StringBuilder hexString = new StringBuilder(number.toString(16));
        while (hexString.length() < 64) {
            hexString.insert(0, '0');
        }
        return hexString.toString();
    }

    /**
     * Outside Accessible Method
     **/
    public void setCommunication(UsbHelperListener usbHelperListener) {
        initRegister();
        this.usbHelperListener = usbHelperListener;
        if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }

    public void onSendCommand(String cmd) {
        switch (cmd) {
            case ConstantHelper.REQUEST_TO_UNLOCK:
                command = ConstantHelper.REQUEST_TO_UNLOCK;
                break;
            case ConstantHelper.REQUEST_TO_CONNECT:
                command = ConstantHelper.REQUEST_TO_CONNECT;
                break;
            case ConstantHelper.START_KEY:
                command = ConstantHelper.START_KEY;
                break;
            case ConstantHelper.STOP_KEY:
                command = ConstantHelper.STOP_KEY;
                break;
            default:
                command = cmd;
                break;
        }
        send(command);
    }

    public void onStartTransmission() {
        counter = 0;
        Thread thread = new Thread(() -> {
            send(command = ConstantHelper.START_KEY);
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            send(command = ConstantHelper.STOP_KEY);
        });
        thread.start();
    }

    public void onStopTransmission() {
        send(command = ConstantHelper.STOP_KEY);
    }

    public void removeInstance() {
        disconnect();
    }
}
