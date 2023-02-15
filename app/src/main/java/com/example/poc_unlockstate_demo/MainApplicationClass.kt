package com.example.poc_unlockstate_demo

import android.app.Application
import android.util.Log
import com.example.devicedetect.MainUsbSerialHelper
import com.example.devicedetect.UsbHelperListener

class MainApplicationClass : Application() {

    override fun onCreate() {
        super.onCreate()
        //initialize module
        /*MainUsbSerialHelper.initialize(applicationContext, object : UsbHelperListener {
            override fun onDeviceConnect() {
            }

            override fun onDeviceVerified() {
            }

            override fun onReceivedData(data: String?) {
                Log.w("APPLICATION_CLASS", "onReceivedData: $data")
            }

            override fun onDeviceDisconnect() {
            }

            override fun onConnectionError(errorMessage: String?) {
            }

        })*/
        MainUsbSerialHelper.initialize(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        //clear module
        MainUsbSerialHelper.clearInstance()
    }

}