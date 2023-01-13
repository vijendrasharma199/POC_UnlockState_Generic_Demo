package com.example.devicedetect.driver;

import android.hardware.usb.UsbDevice;

import java.util.List;

/**
 * @author mike wakerly (opensource@hoho.com)
 */
public interface UsbSerialDriver {

    /**
     * return the device
     */
    UsbDevice getDevice();

    /**
     * return the ports
     */
    List<UsbSerialPort> getPorts();
}
