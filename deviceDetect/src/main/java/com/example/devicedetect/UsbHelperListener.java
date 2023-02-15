package com.example.devicedetect;

public interface UsbHelperListener {
    void onDeviceConnect();

    void onDeviceVerified();

    void onReceivedData(String data);

    void onDeviceDisconnect();

    void onConnectionError(String errorMessage);
}