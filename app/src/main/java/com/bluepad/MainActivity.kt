package com.bluepad

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowInsets
import android.widget.*
import com.bluepad.bluetooth.BluetoothPermissions
import com.bluepad.bluetooth.HidManager
import com.bluepad.ui.TouchpadView
import com.bluepad.ui.LeftClickButton

class MainActivity : Activity() {
    private companion object { const val TAG = "BluePadUi" }
    private lateinit var hid: HidManager
    private lateinit var status: TextView
    private lateinit var devices: Spinner
    private lateinit var connect: Button
    private lateinit var disconnect: Button
    private lateinit var click: Button
    private lateinit var pad: TouchpadView
    private var paired = emptyList<BluetoothDevice>()
    private var visible = false
    private fun trace(message: String) { if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message) }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { trace("broadcast action=${intent.action} visible=$visible; restarting HID"); hid.stop(); if (visible) hid.start() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hid = HidManager(this) { if (!isDestroyed) render(it) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(24 + bars.left,24 + bars.top,24 + bars.right,24 + bars.bottom)
            } else applyLegacyInsets(view, insets)
            insets
        }
        status = TextView(this); root.addView(status)
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() }; root.addView(this) }
        button("Grant permission / Enable Bluetooth") {
            if (!BluetoothPermissions.granted(this)) BluetoothPermissions.request(this)
            else try { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (_: SecurityException) { status.setText(R.string.permission_required) } catch (_: RuntimeException) { status.setText(R.string.enable_unavailable) }
        }
        button("Refresh paired devices and HID") { hid.stop(); if (visible) hid.start() }
        root.addView(TextView(this).apply { setText(R.string.pair_first) })
        devices = Spinner(this); root.addView(devices)
        connect = button("Connect") { paired.getOrNull(devices.selectedItemPosition)?.let { hid.connect(it) } }
        disconnect = button("Disconnect") { hid.disconnect() }
        pad = TouchpadView(this) { dx,dy -> hid.move(dx,dy) }; root.addView(pad, LinearLayout.LayoutParams(-1,0,1f))
        click = LeftClickButton(this, { hid.press() }, { hid.release() }).apply { setText(R.string.left_click) }
        root.addView(click)
        setContentView(root)
        render(HidManager.State())
    }
    @Suppress("DEPRECATION")
    private fun applyLegacyInsets(view: android.view.View, insets: WindowInsets) {
        view.setPadding(24 + insets.systemWindowInsetLeft,24 + insets.systemWindowInsetTop,24 + insets.systemWindowInsetRight,24 + insets.systemWindowInsetBottom)
    }
    override fun onStart() {
        super.onStart(); visible = true; trace("onStart")
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply { addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
        hid.start()
    }
    override fun onStop() { trace("onStop"); visible = false; hid.stop(); unregisterReceiver(receiver); super.onStop() }
    override fun onDestroy() { hid.destroy(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if (visible) { hid.stop(); hid.start() }
    }
    @SuppressLint("MissingPermission", "SetTextI18n")
    private fun render(state: HidManager.State) {
        status.text = "Bluetooth: ${state.bluetooth}\nHID: ${state.hid}\nHost: ${state.connection}\n${state.error}"
        if (paired != state.devices) {
            val previous = paired.getOrNull(devices.selectedItemPosition)?.address
            paired = state.devices
            devices.adapter = ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,state.deviceNames)
            val index = paired.indexOfFirst { it.address == previous }; if (index >= 0) devices.setSelection(index)
        }
        connect.isEnabled = visible && state.registered && !state.connected && !state.busy && paired.isNotEmpty()
        devices.isEnabled = !state.connected && !state.busy
        disconnect.isEnabled = visible && (state.connected || state.busy)
        pad.isEnabled = visible && state.connected
        click.isEnabled = visible && state.connected
        trace("render visible=$visible connected=${state.connected} registered=${state.registered} padEnabled=${pad.isEnabled} clickEnabled=${click.isEnabled}")
        if (!click.isEnabled) click.isPressed = false
    }
}
