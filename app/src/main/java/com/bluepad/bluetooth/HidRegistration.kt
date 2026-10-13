package com.bluepad.bluetooth

import android.bluetooth.BluetoothHidDevice

/** Existing SDP inputs, centralized for compatibility regression checks. */
object HidRegistration {
    const val NAME = "BluePad"
    const val DESCRIPTION = "Mouse and US keyboard"
    const val PROVIDER = "BluePad"
    const val SUBCLASS = BluetoothHidDevice.SUBCLASS1_COMBO
}
