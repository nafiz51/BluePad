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
import android.view.WindowManager
import android.graphics.Rect
import com.bluepad.ui.KeyboardLayoutState
import android.view.View
import com.bluepad.ui.KeyboardView
import com.bluepad.ui.PointerSensitivity
import android.widget.*
import com.bluepad.bluetooth.BluetoothPermissions
import com.bluepad.bluetooth.HidManager
import com.bluepad.ui.TouchpadView
import com.bluepad.ui.MouseClickButton
import com.bluepad.hid.ZoomCompatibility
import com.bluepad.ui.ScrollSettingsView

class MainActivity : Activity() {
    private companion object { const val TAG = "BluePadUi" }
    private lateinit var hid: HidManager
    private lateinit var toolbarStatus: Button
    private lateinit var status: TextView
    private lateinit var devices: Spinner
    private lateinit var connect: Button
    private lateinit var disconnect: Button
    private lateinit var reconnectLast: Button
    private var renderedInputToken = -1L
    private lateinit var click: Button
    private lateinit var rightClick: Button
    private var physicalButtons = 0
    private var zoomCompatibility = ZoomCompatibility.STANDARD
    private lateinit var pad: TouchpadView
    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboardToggle: ImageButton
    private val keyboardLayout = KeyboardLayoutState()
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private var lastState = HidManager.State()
    private var paired = emptyList<BluetoothDevice>()
    private var visible = false
    private fun trace(message: String) { if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message) }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> if (visible) hid.start()
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> hid.stop()
                }
            } else if (visible) hid.refreshBondedDevices()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hid = HidManager(this) { if (!isDestroyed) render(it) }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,ime.bottom))
                observeKeyboard(insets.isVisible(WindowInsets.Type.ime()))
                insets
            }
        } else {
            root.fitsSystemWindows = true
            root.viewTreeObserver.addOnGlobalLayoutListener {
                val frame = Rect(); root.getWindowVisibleDisplayFrame(frame)
                val obscured = root.rootView.height - frame.bottom
                observeKeyboard(obscured > root.rootView.height * 0.15f)
            }
        }
        val toolbar = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        root.addView(toolbar,LinearLayout.LayoutParams(-1,dp(48)))
        toolbarStatus = Button(this).apply { setText(R.string.app_name); isAllCaps = false; isFocusable = false; maxLines = 1; minWidth = 0; minimumWidth = 0; textSize = 14f }
        toolbar.addView(toolbarStatus,LinearLayout.LayoutParams(0,dp(48),1f))
        val connectionPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val connectionScroll = ScrollView(this).apply { visibility = View.GONE; addView(connectionPanel) }
        val settings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val settingsScroll = ScrollView(this).apply { visibility = View.GONE; addView(settings) }
        settings.visibility = View.VISIBLE
        val settingsToggle = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            contentDescription = "Sensitivity, scrolling and zoom compatibility settings"
            isFocusable = false
            setOnClickListener {
                connectionScroll.visibility = View.GONE
                if (settingsScroll.visibility == View.GONE) {
                    val available = root.height - root.paddingTop - root.paddingBottom
                    settingsScroll.layoutParams.height = (available / 2).coerceAtLeast(dp(48))
                    settingsScroll.visibility = View.VISIBLE
                } else settingsScroll.visibility = View.GONE
            }
        }
        toolbarStatus.setOnClickListener {
            settingsScroll.visibility = View.GONE
            if (connectionScroll.visibility == View.GONE) {
                val available = root.height - root.paddingTop - root.paddingBottom
                connectionScroll.layoutParams.height = (available / 2).coerceAtLeast(dp(48))
                connectionScroll.visibility = View.VISIBLE
            } else connectionScroll.visibility = View.GONE
        }
        toolbar.addView(settingsToggle,LinearLayout.LayoutParams(dp(48),dp(48)))
        keyboardToggle = ImageButton(this).apply {
            setImageResource(R.drawable.ic_keyboard)
            contentDescription = "Show keyboard"
            isFocusable = false
            setOnClickListener {
                settingsScroll.visibility = View.GONE; connectionScroll.visibility = View.GONE
                if (keyboardLayout.toggle()) {
                    keyboardView.visibility = View.VISIBLE
                    render(lastState)
                    keyboardView.openIme()
                } else dismissKeyboard()
            }
        }
        toolbar.addView(keyboardToggle,LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(connectionScroll,LinearLayout.LayoutParams(-1,0))
        root.addView(settingsScroll,LinearLayout.LayoutParams(-1,0))
        status = TextView(this); connectionPanel.addView(status)
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() }; connectionPanel.addView(this) }
        button("Grant permission / Enable Bluetooth") {
            if (!BluetoothPermissions.granted(this)) BluetoothPermissions.request(this)
            else try { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (_: SecurityException) { status.setText(R.string.permission_required) } catch (_: RuntimeException) { status.setText(R.string.enable_unavailable) }
        }
        button("Refresh paired devices and HID") { hid.stop(); if (visible) hid.start() }
        connectionPanel.addView(TextView(this).apply { setText(R.string.pair_first) })
        devices = Spinner(this); connectionPanel.addView(devices)
        connect = button("Connect") { paired.getOrNull(devices.selectedItemPosition)?.let { hid.connect(it) } }
        disconnect = button("Disconnect") { hid.disconnect() }
        reconnectLast = button(getString(R.string.reconnect_last)) { hid.reconnectLast() }
        pad = TouchpadView(this, { dx,dy -> hid.move(dx,dy) }, { wheel -> hid.scroll(wheel) }, { mask, down -> hid.gestureButton(mask,down) }, { steps -> hid.zoom(zoomCompatibility,steps) })
        val preferences = getSharedPreferences("touchpad", MODE_PRIVATE)
        settings.addView(Switch(this).apply {
            setText(R.string.automatic_reconnect)
            isChecked = getSharedPreferences("connection",MODE_PRIVATE).getBoolean("automatic",true)
            setOnCheckedChangeListener { _,enabled -> hid.setAutomaticReconnect(enabled) }
        })
        zoomCompatibility = ZoomCompatibility.restore(preferences.getString("zoom_compatibility",null),preferences.getString("host_os",null))
        preferences.edit().putString("zoom_compatibility",zoomCompatibility.name).remove("host_os").apply()
        settings.addView(TextView(this).apply { setText(R.string.zoom_compatibility) })
        settings.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf(getString(R.string.zoom_standard),getString(R.string.zoom_macos)))
            setSelection(zoomCompatibility.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    zoomCompatibility = ZoomCompatibility.entries[position]
                    preferences.edit().putString("zoom_compatibility",zoomCompatibility.name).apply()
                }
            }
        })
        val initialSensitivity = PointerSensitivity.restore(preferences.getFloat("sensitivity", PointerSensitivity.DEFAULT))
        pad.sensitivity = initialSensitivity
        val sensitivityLabel = TextView(this)
        fun showSensitivity(value: Float) { sensitivityLabel.text = getString(R.string.sensitivity, value) }
        showSensitivity(initialSensitivity)
        settings.addView(sensitivityLabel)
        settings.addView(SeekBar(this).apply {
            max = PointerSensitivity.SLIDER_MAX
            progress = PointerSensitivity.progress(initialSensitivity)
            contentDescription = getString(R.string.pointer_sensitivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    pad.sensitivity = PointerSensitivity.value(progress)
                    showSensitivity(pad.sensitivity)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {
                    preferences.edit().putFloat("sensitivity", pad.sensitivity).apply()
                }
            })
        })
        settings.addView(ScrollSettingsView(this) { pad.scrollSettings = it })
        root.addView(pad, LinearLayout.LayoutParams(-1,0,1f))
        val buttonRow = LinearLayout(this)
        fun mouseButton(mask: Int, label: Int) = MouseClickButton(this,
            { physicalButton(mask,true) }, { physicalButton(mask,false) }
        ).apply { setText(label); isAllCaps = false; minWidth = 0; minimumWidth = 0 }
        click = mouseButton(1,R.string.left_click)
        rightClick = mouseButton(2,R.string.right_click)
        buttonRow.addView(click,LinearLayout.LayoutParams(0,dp(48),1f))
        buttonRow.addView(rightClick,LinearLayout.LayoutParams(0,dp(48),1f))
        root.addView(buttonRow,LinearLayout.LayoutParams(-1,dp(48)))
        keyboardView = KeyboardView(this,{ hid.keyTap(it) },{ bit,down -> hid.modifier(bit,down) },{ hid.releaseInputs() })
        keyboardView.visibility = View.GONE
        root.addView(keyboardView,LinearLayout.LayoutParams(-1,-2))
        setContentView(root)
        render(HidManager.State())
    }
    private fun physicalButton(mask: Int, down: Boolean) {
        physicalButtons = if (down) physicalButtons or mask else physicalButtons and mask.inv()
        pad.setPhysicalButtonHeld(physicalButtons != 0)
        hid.physicalButton(mask,down)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun observeKeyboard(shown: Boolean) {
        if (!::keyboardView.isInitialized) return
        if (keyboardLayout.observe(shown)) dismissKeyboard()
        keyboardToggle.isSelected = keyboardLayout.open
        keyboardToggle.contentDescription = if (keyboardLayout.open) "Hide keyboard" else "Show keyboard"
    }
    private fun dismissKeyboard() {
        keyboardLayout.close()
        pad.isEnabled = false; click.isEnabled = false; rightClick.isEnabled = false
        keyboardView.closeIme()
        keyboardView.visibility = View.GONE
        hid.releaseInputs()
        render(lastState.copy(modifiers = 0))
        keyboardToggle.isSelected = false
        keyboardToggle.contentDescription = "Show keyboard"
    }
    private fun updateBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (keyboardLayout.open && backCallback == null) {
                val callback = android.window.OnBackInvokedCallback { dismissKeyboard() }
                backCallback = callback
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback)
            } else if (!keyboardLayout.open) {
                backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
                backCallback = null
            }
        }
    }
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (Build.VERSION.SDK_INT < 33 && keyCode == android.view.KeyEvent.KEYCODE_BACK && keyboardLayout.open) {
            dismissKeyboard(); return true
        }
        return super.onKeyUp(keyCode,event)
    }
    override fun onStart() {
        super.onStart(); visible = true; trace("onStart")
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply { addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
        hid.start()
    }
    override fun onStop() { trace("onStop"); getSharedPreferences("touchpad", MODE_PRIVATE).edit().putFloat("sensitivity", pad.sensitivity).apply(); visible = false; pad.isEnabled = false; click.isEnabled = false; rightClick.isEnabled = false; keyboardLayout.close(); updateBackHandler(); keyboardView.closeIme(); keyboardView.visibility = View.GONE; keyboardView.render(false,0); hid.stop(); unregisterReceiver(receiver); super.onStop() }
    override fun onDestroy() { hid.destroy(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if (visible) { hid.stop(); hid.start() }
    }
    @SuppressLint("MissingPermission", "SetTextI18n")
    private fun render(state: HidManager.State) {
        if (renderedInputToken != state.inputToken) {
            pad.isEnabled = false; click.isEnabled = false; rightClick.isEnabled = false
            keyboardView.render(false,0); keyboardView.resetInput()
            renderedInputToken = state.inputToken
        }
        hid.acknowledgeInputs(state.inputToken)
        lastState = state
        updateBackHandler()
        toolbarStatus.text = "Bluetooth · ${state.connection}"
        toolbarStatus.contentDescription = "Bluetooth connection: ${state.connection}. Select device and connect."
        status.text = "Bluetooth: ${state.bluetooth}\nHID: ${state.hid}\nHost: ${state.connection}\n${state.error}"
        if (paired != state.devices) {
            val previous = paired.getOrNull(devices.selectedItemPosition)?.address ?: getSharedPreferences("connection",MODE_PRIVATE).getString("last_host",null)
            paired = state.devices
            devices.adapter = ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,state.deviceNames)
            val index = paired.indexOfFirst { it.address == previous }; if (index >= 0) devices.setSelection(index)
        }
        state.hostAddress?.let { address -> val index = paired.indexOfFirst { it.address == address }; if (index >= 0) devices.setSelection(index) }
        connect.isEnabled = visible && state.registered && !state.connected && !state.busy && paired.isNotEmpty()
        devices.isEnabled = !state.connected && !state.busy
        disconnect.isEnabled = visible && (state.connected || state.busy || state.connection == "Reconnecting")
        reconnectLast.isEnabled = visible && state.bluetooth == "Enabled" && state.lastHostAvailable && !state.connected && !state.busy
        pad.isEnabled = visible && state.connected
        click.isEnabled = visible && state.connected
        rightClick.isEnabled = visible && state.connected
        trace("render visible=$visible connected=${state.connected} registered=${state.registered} padEnabled=${pad.isEnabled} clickEnabled=${click.isEnabled}")
        keyboardView.render(visible && state.connected && keyboardLayout.open,state.modifiers)
        if (!click.isEnabled) click.isPressed = false
    }
}
