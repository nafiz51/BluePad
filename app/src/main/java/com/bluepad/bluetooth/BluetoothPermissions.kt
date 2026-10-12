package com.bluepad.bluetooth

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object BluetoothPermissions {
    fun granted(context: Context) = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun request(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 31) activity.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
    }
}
