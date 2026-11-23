package com.faceplugin.facerecognitionsdk.shell

import android.app.Application
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.faceplugin.facerecognitionsdk.shell.engine.BuiltInLicense

class ShellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FaceEngine.start(this, BuiltInLicense.KEY)
    }
}
