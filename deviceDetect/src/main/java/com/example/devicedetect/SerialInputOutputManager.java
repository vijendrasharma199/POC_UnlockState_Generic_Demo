package com.example.devicedetect;

import android.os.Process;
import android.util.Log;

import com.example.devicedetect.driver.UsbSerialPort;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Utility class which services a {@link UsbSerialPort} in its {@link #run()} method.
 */
public class SerialInputOutputManager implements Runnable {

    public enum State {
        STOPPED, RUNNING, STOPPING
    }

    public static int counter = 0;
    public static boolean DEBUG = false;

    private static final String TAG = SerialInputOutputManager.class.getSimpleName();
    private static final int BUFSIZ = 4096;

    /**
     * default read timeout is infinite, to avoid data loss with bulkTransfer API
     */
    private final int mReadTimeout = 0;
    private final int mWriteTimeout = 0;

    private final Object mReadBufferLock = new Object();
    private final Object mWriteBufferLock = new Object();

    private final ByteBuffer mReadBuffer; // default size = getReadEndpoint().getMaxPacketSize()
    private final ByteBuffer mWriteBuffer = ByteBuffer.allocate(BUFSIZ);

    private final int mThreadPriority = Process.THREAD_PRIORITY_URGENT_AUDIO;
    private State mState = State.STOPPED; // Synchronized by 'this'

    private final UsbSerialPort mSerialPort;

    ShareDataListener mShareDataListener;

    interface ShareDataListener {
        /**
         * Calling when incoming data is available
         */
        void onTransmissionData(byte[] data);

        /**
         * Called when {@link SerialInputOutputManager#run()} aborts due to an error.
         */
        void onRunError(Exception e);
    }

    public synchronized void setShareDataListener(ShareDataListener listener) {
        mShareDataListener = listener;
    }

    public synchronized ShareDataListener getShareDataListener() {
        return mShareDataListener;
    }


    public SerialInputOutputManager(UsbSerialPort serialPort, ShareDataListener mShareDataListener) {
        mSerialPort = serialPort;
        this.mShareDataListener = mShareDataListener;
        mReadBuffer = ByteBuffer.allocate(serialPort.getReadEndpoint().getMaxPacketSize());
    }

    /**
     * start SerialInputOutputManager in separate thread
     */
    public void start() {
        if (mState != State.STOPPED) throw new IllegalStateException("already started");
        new Thread(this, this.getClass().getSimpleName()).start();
    }

    /**
     * stop SerialInputOutputManager thread
     * when using readTimeout == 0 (default), additionally use usbSerialPort.close() to
     * interrupt blocking read
     */
    public synchronized void stop() {
        if (getState() == State.RUNNING) {
            Log.i(TAG, "Stop requested");
            mState = State.STOPPING;
        }
    }

    public synchronized State getState() {
        return mState;
    }

    /**
     * Continuously services the read and write buffers until {@link #stop()} is
     * called, or until a driver exception is raised.
     */
    @Override
    public void run() {
        synchronized (this) {
            if (getState() != State.STOPPED) {
                throw new IllegalStateException("Already running");
            }
            mState = State.RUNNING;
        }
        Log.i(TAG, "Running ...");
        try {
            if (mThreadPriority != Process.THREAD_PRIORITY_DEFAULT)
                Process.setThreadPriority(mThreadPriority);
            while (true) {
                if (getState() != State.RUNNING) {
                    Log.i(TAG, "Stopping mState=" + getState());
                    break;
                }
                step();
            }
        } catch (Exception e) {
            Log.w(TAG, "Run ending due to exception: " + e.getMessage(), e);
            final ShareDataListener listener = getShareDataListener();
            if (listener != null) {
                listener.onRunError(e);
            }
        } finally {
            synchronized (this) {
                mState = State.STOPPED;
                Log.i(TAG, "Stopped");
            }
        }
    }

    private void step() throws IOException {
        // Handle incoming data.
        byte[] buffer;
        synchronized (mReadBufferLock) {
            buffer = mReadBuffer.array();
        }
        int len = mSerialPort.read(buffer, mReadTimeout);

        if (len > 0) {
            if (DEBUG) {
                Log.d(TAG, "Read data len=" + len);
            }
            final ShareDataListener mSListener = getShareDataListener();
            if (mSListener != null) {
                final byte[] data = new byte[len];
                System.arraycopy(buffer, 0, data, 0, len);
                int loop = counter;
                Log.v("TimeLog", "Read Data : " + System.currentTimeMillis() + " \t Data : " + new String(data) + "\t counter : " + loop);
                mSListener.onTransmissionData(data);
            }
        }

        // Handle outgoing data.
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
            if (DEBUG) {
                Log.d(TAG, "Writing data len=" + len);
            }
            mSerialPort.write(buffer, mWriteTimeout);
        }
    }
}
