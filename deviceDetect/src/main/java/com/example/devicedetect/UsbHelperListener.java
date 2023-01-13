package com.example.devicedetect;

public interface UsbHelperListener {
    void onDeviceConnect();

    void onDeviceVerified(boolean isVerified);

    void onTransmission(String data);

    void onDeviceDisconnect();

    void onConnectionError(String errorMessage);
}
