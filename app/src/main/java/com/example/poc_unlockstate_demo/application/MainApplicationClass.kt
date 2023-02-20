package com.example.poc_unlockstate_demo.application

import android.app.Application
import com.example.devicedetect.MainUsbSerialHelper

class MainApplicationClass : Application() {

    override fun onCreate() {
        super.onCreate()
        //initialize module
        MainUsbSerialHelper.initialize(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        //clear module
        MainUsbSerialHelper.clearInstance()
    }

}