package com.example.devicedetect.driver;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbManager;

import androidx.annotation.IntDef;

import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface UsbSerialPort extends Closeable {

    /**
     * 5 data bits.
     */
    int DATABITS_5 = 5;
    /**
     * 6 data bits.
     */
    int DATABITS_6 = 6;
    /**
     * 7 data bits.
     */
    int DATABITS_7 = 7;
    /**
     * 8 data bits.
     */
    int DATABITS_8 = 8;

    /**
     * 5 data bits.
     */
    //int DATABITS_5 = 5;
    /**
     * 8 data bits.
     */
    //int DATABITS_8 = 8;

    /**
     * Values for setParameters(..., parity)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PARITY_NONE, PARITY_ODD, PARITY_EVEN, PARITY_MARK, PARITY_SPACE})
    @interface Parity {
    }

    /**
     * No parity.
     */
    int PARITY_NONE = 0;
    /**
     * Odd parity.
     */
    int PARITY_ODD = 1;
    /**
     * Even parity.
     */
    int PARITY_EVEN = 2;
    /**
     * Mark parity.
     */
    int PARITY_MARK = 3;
    /**
     * Space parity.
     */
    int PARITY_SPACE = 4;

    /**
     * 1 stop bit.
     */
    int STOPBITS_1 = 1;
    /**
     * 1.5 stop bits.
     */
    int STOPBITS_1_5 = 3;
    /**
     * 2 stop bits.
     */
    int STOPBITS_2 = 2;

    /**
     * Returns the driver used by this port.
     */
    UsbSerialDriver getDriver();

    /**
     * Returns the currently-bound USB device.
     */
    UsbDevice getDevice();

    /**
     * Returns the read endpoint.
     *
     * @return read endpoint
     */
    UsbEndpoint getReadEndpoint();

    /**
     * Opens and initializes the port. Upon success, caller must ensure that
     * {@link #close()} is eventually called.
     *
     * @param connection an open device connection, acquired with
     *                   {@link UsbManager#openDevice(UsbDevice)}
     * @throws IOException on error opening or initializing the port.
     */
    void open(UsbDeviceConnection connection) throws IOException;

    /**
     * Closes the port and {@link UsbDeviceConnection}
     *
     * @throws IOException on error closing the port.
     */
    void close() throws IOException;

    /**
     * Reads as many bytes as possible into the destination buffer.
     *
     * @param dest    the destination byte buffer
     * @param timeout the timeout for reading in milliseconds, 0 is infinite
     * @return the actual number of bytes read
     * @throws IOException if an error occurred during reading
     */
    int read(final byte[] dest, final int timeout) throws IOException;

    /**
     * Writes as many bytes as possible from the source buffer.
     *
     * @param src     the source byte buffer
     * @param timeout the timeout for writing in milliseconds, 0 is infinite
     * @throws SerialTimeoutException if timeout reached before sending all data.
     *                                ex.bytesTransferred may contain bytes transferred
     * @throws IOException            if an error occurred during writing
     */
    void write(final byte[] src, final int timeout) throws IOException;

    /**
     * Sets various serial port parameters.
     *
     * @param baudRate baud rate as an integer, for example {@code 115200}.
     * @param dataBits one of {@link #DATABITS_5}, {@link #DATABITS_6},
     *                 {@link #DATABITS_7}, or {@link #DATABITS_8}.
     * @param stopBits one of {@link #STOPBITS_1}, {@link #STOPBITS_1_5}, or {@link #STOPBITS_2}.
     * @param parity   one of {@link #PARITY_NONE}, {@link #PARITY_ODD},
     *                 {@link #PARITY_EVEN}, {@link #PARITY_MARK}, or {@link #PARITY_SPACE}.
     * @throws IOException                   on error setting the port parameters
     * @throws UnsupportedOperationException if values are not supported by a specific device
     */
    void setParameters(int baudRate, int dataBits, int stopBits, @Parity int parity) throws IOException;

    /**
     * Returns the current state of the connection.
     */
    boolean isOpen();

}
