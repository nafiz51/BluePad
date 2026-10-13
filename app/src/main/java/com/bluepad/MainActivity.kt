package com.bluepad

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.Build
import android.os.Bundle
import com.bluepad.diagnostics.Diagnostics
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
import com.bluepad.ui.Appearance
import com.bluepad.ui.UiStyle
import com.bluepad.ui.MainUiPolicy
import com.bluepad.ui.UiNavigation
import com.bluepad.ui.KeyboardTogglePresentation

class MainActivity : Activity() {
    private companion object { const val TAG = "BluePadUi" }
    private lateinit var hid: HidManager
    private lateinit var toolbarStatus: TextView
    private lateinit var connectionLabel: TextView
    private lateinit var connectionSummary: LinearLayout
    private lateinit var connectionIndicator: View
    private lateinit var settingsScroll: ScrollView
    private lateinit var connectionScroll: ScrollView
    private var navigation = UiNavigation()
    private var renderedPanel = UiNavigation.Panel.MAIN
    private var imeVisible = false
    private lateinit var connectionToggle: ImageButton
    private lateinit var themeToggle: ImageButton
    private lateinit var settingsToggle: ImageButton
    private lateinit var appearancePicker: Spinner
    private lateinit var mainRoot: LinearLayout
    private lateinit var mouseButtonsRow: LinearLayout
    private lateinit var uiStyle: UiStyle
    private var creditsDialog: AlertDialog? = null
    private lateinit var status: TextView
    private lateinit var devices: Spinner
    private lateinit var connect: Button
    private lateinit var disconnect: Button
    private lateinit var reconnectLast: Button
    private var renderedInputToken = -1L
    private lateinit var click: Button
    private lateinit var rightClick: Button
    private var physicalButtons = 0
    private var pointerCustomized = false
    private var zoomCompatibility = ZoomCompatibility.STANDARD
    private lateinit var pad: TouchpadView
    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboardToggle: ImageButton
    private val keyboardLayout = KeyboardLayoutState()
    private val keyboardPresentation = KeyboardTogglePresentation()
    private var layoutTrace = false
    private var layoutCount = 0
    private var layoutLogTime = 0L
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private var lastState = HidManager.State()
    private var paired = emptyList<BluetoothDevice>()
    private var visible = false
    private inline fun trace(message: () -> String) = Diagnostics.debug(TAG,message)
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
        val appearancePreferences = getSharedPreferences("appearance",MODE_PRIVATE)
        val appearance = Appearance.restore(appearancePreferences.getString("theme",null))
        val systemDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        setTheme(if (appearance.isDark(systemDark)) R.style.BluePadDark else R.style.BluePadLight)
        super.onCreate(savedInstanceState)
        navigation = UiNavigation.restore(savedInstanceState?.getString("panel"))
        uiStyle = UiStyle(appearance.isDark(systemDark),resources.displayMetrics.density)
        layoutTrace = Diagnostics.enabled && Diagnostics.isEnabled("BluePadPerf")
        hid = HidManager(this) { if (!isDestroyed) render(it) }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(uiStyle.background) }
        mainRoot = root
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
        val toolbar = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = dp(MainUiPolicy.TOOLBAR_DP) }
        root.addView(toolbar,LinearLayout.LayoutParams(-1,-2))
        toolbar.addView(TextView(this).apply {
            setText(R.string.app_name); gravity = android.view.Gravity.CENTER_VERTICAL; textSize = 16f; setTypeface(typeface,android.graphics.Typeface.BOLD)
            setPadding(dp(4),0,dp(8),0)
        },LinearLayout.LayoutParams(-2,-1).apply { gravity = android.view.Gravity.CENTER_VERTICAL })
        toolbar.setPadding(dp(8),0,dp(8),0)
        connectionIndicator = View(this).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(uiStyle.muted) } }
        toolbar.addView(connectionIndicator,LinearLayout.LayoutParams(dp(7),dp(7)).apply { marginEnd = dp(8) })
        connectionSummary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(48); isFocusable = false
        }
        toolbarStatus = TextView(this).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 13f; setTypeface(typeface,android.graphics.Typeface.BOLD) }
        connectionLabel = TextView(this).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 11f }
        connectionSummary.addView(toolbarStatus); connectionSummary.addView(connectionLabel)
        toolbar.addView(connectionSummary,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd = dp(8) })
        val connectionPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        connectionScroll = ScrollView(this).apply { visibility = View.GONE; addView(connectionPanel) }
        val settings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        settingsScroll = ScrollView(this).apply { visibility = View.GONE; addView(settings) }
        settings.visibility = View.VISIBLE
        fun section(label: Int) {
            settings.addView(TextView(this).apply {
                setText(label); textSize = 14f; setTypeface(typeface,android.graphics.Typeface.BOLD)
                setPadding(0,dp(16),0,dp(4))
            })
        }
        settings.setPadding(dp(16),0,dp(16),dp(12))
        connectionPanel.setPadding(dp(16),dp(8),dp(16),dp(12))
        fun panelHeight(available: Int): Int {
            val keyboardHeight = if (::keyboardView.isInitialized && keyboardView.visibility == View.VISIBLE) keyboardView.height else 0
            val buttonHeight = if (::mouseButtonsRow.isInitialized) mouseButtonsRow.height.coerceAtLeast(dp(48)) else dp(48)
            return MainUiPolicy.panelHeight(available,toolbar.height.coerceAtLeast(dp(48))+buttonHeight+dp(48)+keyboardHeight)
        }
        settingsToggle = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            contentDescription = "Sensitivity, scrolling and zoom compatibility settings"
            isFocusable = false
            setOnClickListener { navigation.toggle(UiNavigation.Panel.SETTINGS); renderPanels() }
        }
        connectionSummary.setOnClickListener { navigation.toggle(UiNavigation.Panel.CONNECTION); renderPanels() }
        connectionToggle = ImageButton(this).apply {
            setImageResource(R.drawable.ic_connection); isFocusable = false
            setOnClickListener { navigation.toggle(UiNavigation.Panel.CONNECTION); renderPanels() }
        }
        toolbar.addView(connectionToggle,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd = dp(4) })
        themeToggle = ImageButton(this).apply {
            isFocusable = false
            setOnClickListener {
                chooseAppearance(Appearance.oppositeDisplayed(uiStyle.dark))
            }
        }
        toolbar.addView(themeToggle,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd = dp(4) })
        toolbar.addView(settingsToggle,LinearLayout.LayoutParams(dp(48),dp(48)))
        keyboardToggle = ImageButton(this).apply {
            setImageResource(R.drawable.ic_keyboard)
            contentDescription = "Show keyboard"
            isFocusable = false
            setOnClickListener {
                navigation.closePanel(); renderPanels()
                if (keyboardLayout.toggle()) {
                    keyboardView.visibility = View.VISIBLE
                    render(lastState)
                    keyboardView.openIme()
                } else dismissKeyboard()
            }
        }

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
        section(R.string.section_connection)
        settings.addView(Switch(this).apply {
            setText(R.string.automatic_reconnect)
            isChecked = getSharedPreferences("connection",MODE_PRIVATE).getBoolean("automatic",true)
            setOnCheckedChangeListener { _,enabled -> hid.setAutomaticReconnect(enabled) }
        })
        zoomCompatibility = ZoomCompatibility.restore(preferences.getString("zoom_compatibility",null),preferences.getString("host_os",null))
        preferences.edit().putString("zoom_compatibility",zoomCompatibility.name).remove("host_os").apply()
        section(R.string.section_touchpad)
        pointerCustomized = preferences.contains("sensitivity")
        val initialSensitivity = PointerSensitivity.fromSaved(if (pointerCustomized) preferences.getFloat("sensitivity",PointerSensitivity.DEFAULT) else null)
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
                    if (fromUser) pointerCustomized = true
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
        section(R.string.section_zoom)
        settings.addView(TextView(this).apply { setText(R.string.zoom_compatibility) })
        settings.addView(Spinner(this).apply {
            contentDescription = getString(R.string.zoom_compatibility)
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
        section(R.string.section_appearance)
        appearancePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.theme_system),getString(R.string.theme_light),getString(R.string.theme_dark)))
            setSelection(appearance.ordinal)
            contentDescription = getString(R.string.section_appearance)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val next = Appearance.entries[position]
                    if(next != Appearance.restore(appearancePreferences.getString("theme",null))) {
                        chooseAppearance(next)
                    }
                }
            }
        }
        settings.addView(appearancePicker)
        settings.addView(Button(this).apply {
            setText(R.string.credits)
            isFocusable = false
            setOnClickListener { showCredits() }
        })
        pad.text = ""
        pad.background = uiStyle.card(uiStyle.palette.touchpad)
        pad.setVisualColors(uiStyle.palette.strip,uiStyle.palette.separator)
        root.addView(pad, LinearLayout.LayoutParams(-1,0,1f).apply {
            setMargins(dp(8),dp(4),dp(8),dp(4))
        })
        val buttonRow = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = dp(MainUiPolicy.BUTTON_ROW_DP) }
        mouseButtonsRow = buttonRow
        fun mouseButton(mask: Int, label: Int) = MouseClickButton(this,
            { physicalButton(mask,true) }, { physicalButton(mask,false) }
        ).apply { setText(label); isAllCaps = false; minWidth = 0; minimumWidth = 0 }
        click = mouseButton(1,R.string.left_click)
        rightClick = mouseButton(2,R.string.right_click)
        buttonRow.addView(click,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd = dp(4) })
        buttonRow.addView(rightClick,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd = dp(8) })
        buttonRow.addView(keyboardToggle,LinearLayout.LayoutParams(dp(48),dp(48)))
        buttonRow.setPadding(dp(8),0,dp(8),0)
        root.addView(buttonRow,LinearLayout.LayoutParams(-1,-2))
        keyboardView = KeyboardView(this,{ trace { "keyboard route connected=${lastState.connected} mode=${keyboardLayout.open} visible=$visible token=$renderedInputToken" }; hid.keyTap(it) },{ bit,down -> hid.modifier(bit,down) },{ hid.releaseInputs() })
        keyboardView.visibility = View.GONE
        root.addView(keyboardView,LinearLayout.LayoutParams(-1,-2))
        root.viewTreeObserver.addOnGlobalLayoutListener {
            if (layoutTrace) {
                layoutCount++
                val now = android.os.SystemClock.uptimeMillis()
                if (now-layoutLogTime >= 1000) {
                    Log.d("BluePadPerf","uptime=$now layouts=$layoutCount keyboard=${keyboardLayout.open} panel=${navigation.panel} rootHeight=${root.height}")
                    layoutLogTime = now; layoutCount = 0
                }
            }
            if (keyboardView.visibility == View.GONE && navigation.panel == UiNavigation.Panel.MAIN) return@addOnGlobalLayoutListener
            val available = root.height-root.paddingTop-root.paddingBottom
            if (keyboardView.visibility == View.VISIBLE && keyboardView.contentHeight > 0) {
                val target = MainUiPolicy.keyboardHeight(available,toolbar.height+mouseButtonsRow.height+dp(48),keyboardView.contentHeight,dp(48))
                if (keyboardView.layoutParams.height != target) keyboardView.layoutParams = keyboardView.layoutParams.apply { height = target }
            }
            val targetHeight = panelHeight(available)
            if (settingsScroll.visibility == View.VISIBLE && settingsScroll.layoutParams.height != targetHeight) {
                settingsScroll.layoutParams = settingsScroll.layoutParams.apply { height = targetHeight }
            }
            if (connectionScroll.visibility == View.VISIBLE && connectionScroll.layoutParams.height != targetHeight) {
                connectionScroll.layoutParams = connectionScroll.layoutParams.apply { height = targetHeight }
            }
        }
        uiStyle.applyTree(root)
        connectionToggle.background = uiStyle.toolbarIconBackground()
        themeToggle.background = uiStyle.toolbarIconBackground()
        settingsToggle.background = uiStyle.toolbarIconBackground()
        connectionLabel.setTextColor(uiStyle.muted)
        updateThemeIcon()
        setContentView(root)
        renderPanels()
        render(HidManager.State())
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        chooseAppearance(Appearance.restore(getSharedPreferences("appearance",MODE_PRIVATE).getString("theme",null)))
    }
    private fun systemIsDark() = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    private fun updateThemeIcon() {
        themeToggle.setImageResource(if(uiStyle.dark) R.drawable.ic_sun else R.drawable.ic_moon)
        themeToggle.contentDescription = getString(if(uiStyle.dark) R.string.switch_light else R.string.switch_dark)
    }
    private fun showCredits() {
        if (creditsDialog?.isShowing == true) return
        val themed = android.view.ContextThemeWrapper(this,if(uiStyle.dark) R.style.BluePadDark else R.style.BluePadLight)
        val dialog = AlertDialog.Builder(themed)
            .setMessage(R.string.credits_message)
            .setPositiveButton(android.R.string.ok,null)
            .create()
        creditsDialog = dialog
        dialog.setOnDismissListener { if (creditsDialog === dialog) creditsDialog = null }
        dialog.show()
        styleCreditsDialog()
    }
    private fun styleCreditsDialog() {
        creditsDialog?.takeIf { it.isShowing }?.let { dialog ->
            dialog.window?.setBackgroundDrawable(uiStyle.card())
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(uiStyle.foreground)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { uiStyle.applyTree(it) }
        }
    }
    private fun chooseAppearance(next: Appearance) {
        val preferences = getSharedPreferences("appearance",MODE_PRIVATE)
        preferences.edit().putString("theme",next.name).apply()
        val dark = next.isDark(systemIsDark())
        if (dark != uiStyle.dark) {
            setTheme(if(dark) R.style.BluePadDark else R.style.BluePadLight)
            uiStyle = UiStyle(dark,resources.displayMetrics.density)
            mainRoot.setBackgroundColor(uiStyle.background)
            uiStyle.applyTree(mainRoot)
            pad.background = uiStyle.card(uiStyle.palette.touchpad)
            pad.setVisualColors(uiStyle.palette.strip,uiStyle.palette.separator)
            connectionToggle.background = uiStyle.toolbarIconBackground()
            themeToggle.background = uiStyle.toolbarIconBackground()
            settingsToggle.background = uiStyle.toolbarIconBackground()
            connectionLabel.setTextColor(uiStyle.muted)
            (connectionIndicator.background as android.graphics.drawable.GradientDrawable).setColor(if(lastState.connected) uiStyle.accent else uiStyle.muted)
            window.statusBarColor = uiStyle.background
            window.navigationBarColor = uiStyle.background
            if (Build.VERSION.SDK_INT >= 30) {
                val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if(dark) 0 else mask,mask)
            } else {
                val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                window.decorView.systemUiVisibility = if(dark) window.decorView.systemUiVisibility and mask.inv() else window.decorView.systemUiVisibility or mask
            }
        }
        themeToggle.background = uiStyle.toolbarIconBackground()
        updateThemeIcon()
        styleCreditsDialog()
        if(appearancePicker.selectedItemPosition != next.ordinal) appearancePicker.setSelection(next.ordinal)
    }
    private fun renderPanels() {
        val returningToTyping = renderedPanel != UiNavigation.Panel.MAIN && navigation.panel == UiNavigation.Panel.MAIN
        renderedPanel = navigation.panel
        settingsScroll.visibility = if (navigation.panel == UiNavigation.Panel.SETTINGS) View.VISIBLE else View.GONE
        connectionScroll.visibility = if (navigation.panel == UiNavigation.Panel.CONNECTION) View.VISIBLE else View.GONE
        if (::keyboardView.isInitialized) {
            keyboardView.setTypingFocusAllowed(navigation.panel == UiNavigation.Panel.MAIN)
            if (returningToTyping && keyboardLayout.open) keyboardView.openIme()
        }
        updateBackHandler()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("panel",navigation.panel.name)
        super.onSaveInstanceState(outState)
    }
    private fun handleBack(): Boolean = when(navigation.back(imeVisible || keyboardLayout.open)) {
        UiNavigation.Back.DISMISS_IME -> { dismissKeyboard(); true }
        UiNavigation.Back.CLOSE_PANEL -> { navigation.closePanel(); renderPanels(); true }
        UiNavigation.Back.EXIT -> false
    }
    private fun physicalButton(mask: Int, down: Boolean) {
        physicalButtons = if (down) physicalButtons or mask else physicalButtons and mask.inv()
        pad.setPhysicalButtonHeld(physicalButtons != 0)
        hid.physicalButton(mask,down)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun updateKeyboardToggle() {
        val open = keyboardLayout.open
        if (keyboardPresentation.update(open)) {
            keyboardToggle.setImageResource(if (open) R.drawable.ic_close else R.drawable.ic_keyboard)
            keyboardToggle.isSelected = open
            keyboardToggle.contentDescription = if (open) "Hide keyboard" else "Show keyboard"
        }
    }
    private fun observeKeyboard(shown: Boolean) {
        if (!::keyboardView.isInitialized) return
        if (Diagnostics.enabled && Diagnostics.isEnabled(TAG))
            trace { "IME visibility shown=$shown mode=${keyboardLayout.open} panel=${navigation.panel}" }
        imeVisible = shown
        if (keyboardLayout.observe(shown)) dismissKeyboard()
        updateKeyboardToggle()
        updateBackHandler()
    }
    private fun dismissKeyboard() {
        keyboardLayout.close(); imeVisible = false
        pad.isEnabled = false; click.isEnabled = false; rightClick.isEnabled = false
        keyboardView.closeIme()
        keyboardView.visibility = View.GONE
        hid.releaseInputs()
        render(lastState.copy(modifiers = 0))
        updateKeyboardToggle()
    }
    private fun updateBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (visible && navigation.back(imeVisible || keyboardLayout.open) != UiNavigation.Back.EXIT && backCallback == null) {
                val callback = android.window.OnBackInvokedCallback { handleBack() }
                backCallback = callback
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback)
            } else if (!visible || navigation.back(imeVisible || keyboardLayout.open) == UiNavigation.Back.EXIT) {
                backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
                backCallback = null
            }
        }
    }
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (Build.VERSION.SDK_INT < 33 && keyCode == android.view.KeyEvent.KEYCODE_BACK && handleBack()) return true
        return super.onKeyUp(keyCode,event)
    }
    override fun onStart() {
        super.onStart(); visible = true; updateBackHandler(); trace { "onStart" }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply { addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
        hid.start()
    }
    override fun onStop() { trace { "onStop" }; if (pointerCustomized) getSharedPreferences("touchpad", MODE_PRIVATE).edit().putFloat("sensitivity", pad.sensitivity).apply(); visible = false; pad.isEnabled = false; click.isEnabled = false; rightClick.isEnabled = false; keyboardLayout.close(); imeVisible = false; updateBackHandler(); keyboardView.closeIme(); keyboardView.visibility = View.GONE; keyboardView.render(false,0); hid.stop(); unregisterReceiver(receiver); super.onStop() }
    override fun onDestroy() { creditsDialog?.dismiss(); hid.destroy(); super.onDestroy() }
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
        updateKeyboardToggle()
        updateBackHandler()
        val hostName = state.devices.indexOfFirst { it.address == state.hostAddress }.takeIf { it >= 0 }
            ?.let { state.deviceNames.getOrNull(it)?.substringBeforeLast(" (") }
        toolbarStatus.text = MainUiPolicy.hostLabel(state.connected,hostName,getString(R.string.select_host))
        connectionLabel.text = state.connection
        (connectionIndicator.background as android.graphics.drawable.GradientDrawable).setColor(if(state.connected) uiStyle.accent else uiStyle.muted)
        connectionToggle.isSelected = state.connected
        connectionToggle.contentDescription = getString(R.string.connection_controls)
        connectionSummary.contentDescription = "Bluetooth connection: ${state.connection}. ${hostName ?: ""}. Select device and connect."
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
        trace { "render visible=$visible connected=${state.connected} registered=${state.registered} padEnabled=${pad.isEnabled} clickEnabled=${click.isEnabled}" }
        keyboardView.render(visible && state.connected && keyboardLayout.open,state.modifiers)
        if (!click.isEnabled) click.isPressed = false
    }
}
