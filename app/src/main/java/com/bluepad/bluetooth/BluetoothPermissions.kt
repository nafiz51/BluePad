package com.bluepad.bluetooth

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object BluetoothPermissions {
    const val REQUEST_CODE = 1
    fun requiresConnectPermission(api: Int) = api >= 31
    // The API 31 gate below is centralized and regression-tested. Lint cannot infer it.
    @android.annotation.SuppressLint("InlinedApi")
    fun granted(context: Context) = !requiresConnectPermission(Build.VERSION.SDK_INT) ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    @android.annotation.SuppressLint("InlinedApi")
    fun request(activity: Activity) {
        if (requiresConnectPermission(Build.VERSION.SDK_INT)) activity.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_CODE)
    }
}
