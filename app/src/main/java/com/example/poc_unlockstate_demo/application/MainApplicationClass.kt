package com.example.poc_unlockstate_demo.application

import android.app.Application
import `in`.sunfox.healthcare.commons.android.sericom.SeriCom

class MainApplicationClass : Application() {

    override fun onCreate() {
        super.onCreate()
        //initialize module
        SeriCom.initialize(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        //clear module
        SeriCom.clearInstance()
    }

}