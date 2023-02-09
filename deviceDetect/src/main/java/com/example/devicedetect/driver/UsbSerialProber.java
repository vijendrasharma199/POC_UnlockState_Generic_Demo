package com.example.devicedetect.driver;

import android.hardware.usb.UsbDevice;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class UsbSerialProber {

    private final ProbeTable mProbeTable;

    public UsbSerialProber(ProbeTable probeTable) {
        mProbeTable = probeTable;
    }

    public static UsbSerialProber getDefaultProber() {
        return new UsbSerialProber(getDefaultProbeTable());
    }

    public static ProbeTable getDefaultProbeTable() {
        final ProbeTable probeTable = new ProbeTable();
        probeTable.addDriver(CdcAcmSerialDriver.class);
//        probeTable.addDriver(Cp21xxSerialDriver.class);
        return probeTable;
    }


    /**
     * Probes a single device for a compatible driver.
     *
     * @param usbDevice the usb device to probe
     * @return a new {@link UsbSerialDriver} compatible with this device, or
     * {@code null} if none available.
     */
    public UsbSerialDriver probeDevice(final UsbDevice usbDevice) {
        final int vendorId = usbDevice.getVendorId();
        final int productId = usbDevice.getProductId();

        final Class<? extends UsbSerialDriver> driverClass = mProbeTable.findDriver(vendorId, productId);
        if (driverClass != null) {
            final UsbSerialDriver driver;
            try {
                final Constructor<? extends UsbSerialDriver> ctor = driverClass.getConstructor(UsbDevice.class);
                driver = ctor.newInstance(usbDevice);
            } catch (NoSuchMethodException | IllegalArgumentException | InstantiationException |
                     IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return driver;
        }
        return null;
    }

}
