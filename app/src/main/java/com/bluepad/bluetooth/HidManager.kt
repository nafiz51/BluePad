package com.bluepad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.bluepad.diagnostics.Diagnostics
import com.bluepad.diagnostics.ConnectionTimeline
import android.os.Build
import com.bluepad.BuildConfig
import android.util.Log
import com.bluepad.hid.CompositeReports
import com.bluepad.hid.HidInputState
import com.bluepad.hid.UsKeyboard
import com.bluepad.hid.MouseReports
import com.bluepad.hid.ZoomCompatibility
import com.bluepad.hid.ZoomReports
import java.util.concurrent.Executors

/** All mutable state lives on a serial worker. UI receives immutable snapshots. */
@SuppressLint("MissingPermission")
class HidManager(context: Context, private val update: (State) -> Unit) {
    private companion object { const val TAG = "BluePadHid" }
    data class State(val bluetooth: String = "Checking", val hid: String = "Inactive", val connection: String = "Disconnected",
        val devices: List<BluetoothDevice> = emptyList(), val deviceNames: List<String> = emptyList(), val registered: Boolean = false,
        val connected: Boolean = false, val inputToken: Long = 0, val hostAddress: String? = null, val lastHostAvailable: Boolean = false, val modifiers: Int = 0, val busy: Boolean = false, val error: String = "", val diagnosticsAvailable: Boolean = false)
    private val app = context.applicationContext
    private val debugTrace = Diagnostics.enabled
    private val timeline = ConnectionTimeline({ SystemClock.uptimeMillis() })
    private val diagnosticEnvironment = "version=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} api=${Build.VERSION.SDK_INT} android=${Build.VERSION.RELEASE} manufacturer=${Build.MANUFACTURER} model=${Build.MODEL}"
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var state = State()
    private val preferences = app.getSharedPreferences("connection",Context.MODE_PRIVATE)
    private val reconnect = ReconnectPolicy(preferences.getBoolean("automatic",true),preferences.getBoolean("paused",false))
    private val inputSession = InputSession()
    private var retryAddress: String? = preferences.getString("last_host",null)
    private var foreground = false
    private var retry: Runnable? = null
    private var retryTicket = 0
    private var active = false
    private var generation = 0
    private var profile: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var disconnecting = false
    private var bootProtocol = false
    private val inputs = HidInputState()
    private val keyboard = inputs.keyboard
    private var keyboardLeds = 0
    private val buttons = inputs.mouse
    private var timeout: Runnable? = null
    private val deadline = HidDeadline()
    private var reportSequence = 0L
    // Diagnostic builds also require an explicit per-tag DEBUG opt-in.
    private inline fun trace(message: () -> String) = Diagnostics.debug(TAG,message)
    private inline fun lifecycle(message: () -> String) {
        val event = message()
        if (event.startsWith("get_report") || event.startsWith("set_report") || event.startsWith("set_protocol")) return
        timeline.record("session=$generation $event")
        if (BuildConfig.DEBUG) Log.i("BluePadConnection", "uptime=${SystemClock.uptimeMillis()} session=$generation $event")
    }
    fun copyDiagnostics(copied: (String) -> Unit) = work {
        val report = timeline.report(diagnosticEnvironment)
        main.post { copied(report) }
    }
    private fun publish() {
        if (state.connection == "Connection failed" || when (state.hid) { "Failed", "Registration rejected", "HID profile unavailable", "HID service lost" -> true; else -> false }) timeline.failure()
        state = state.copy(diagnosticsAvailable = timeline.failed,modifiers = keyboard.modifiers,inputToken = inputSession.token,hostAddress = host?.address,lastHostAvailable = state.devices.any { it.address == preferences.getString("last_host",null) }); trace { "state session=$generation active=$active registered=${state.registered} connected=${state.connected} busy=${state.busy} profile=${profile != null} host=${host != null} hid=${state.hid} connection=${state.connection}" }; val snapshot = state; main.post { update(snapshot) } }
    private fun work(block: () -> Unit) { synchronized(worker) { if (worker.isShutdown) return; worker.execute {
        try { block() } catch (e: SecurityException) { Log.e(TAG, "Bluetooth permission failure; closing session", e); lifecycle { "operation_exception type=SecurityException" }; timeline.failure(); close(); state = State(bluetooth = "Permission required", error = "Grant Nearby devices permission."); publish() }
        catch (e: RuntimeException) { Log.e(TAG, "Bluetooth operation failure; closing session", e); lifecycle { "operation_exception type=${e.javaClass.simpleName}" }; timeline.failure(); close(); state = state.copy(hid = "Failed", error = e.message ?: "Bluetooth operation failed"); publish() }
    } } }
    fun start() = work {
        if (!foreground) { reconnect.foreground(); retryAddress = preferences.getString("last_host",null) }
        foreground = true; startInternal()
    }
    private fun startInternal() {
        lifecycle { "start active=$active foreground=$foreground permission=${BluetoothPermissions.granted(app)} adapterState=${if (BluetoothPermissions.granted(app)) adapter?.state else null}" }
        trace { "start active=$active" }
        if (active) return
        active = true
        if (!BluetoothPermissions.granted(app)) { state = State(bluetooth = "Permission required"); active = false; publish(); return }
        if (adapter == null || !adapter.isEnabled) {
            state = State(bluetooth = if (adapter == null) "Unavailable" else "Disabled"); active = false; publish(); return
        }
        val paired = adapter.bondedDevices.sortedBy { it.address }
        lifecycle { "bonded_hosts count=${paired.size}" }
        state = State(bluetooth = "Enabled", hid = "Acquiring HID profile",connection = if (reconnect.connecting) "Reconnecting" else "Disconnected", devices = paired,
            deviceNames = paired.map { "${it.name ?: "Paired device"} (${it.address})" },error = if (reconnect.paused) "Automatic reconnection paused. Connect manually to resume." else "")
        val session = ++generation
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(type: Int, proxy: BluetoothProfile) {
                synchronized(worker) {
                    if (worker.isShutdown) { safely { adapter.closeProfileProxy(type, proxy) }; return }
                    work {
                        lifecycle { "profile_acquired callbackSession=$session active=$active type=$type" }
                        trace { "profile acquired callbackSession=$session currentSession=$generation active=$active" }
                        if (!active || session != generation) { adapter.closeProfileProxy(type, proxy); return@work }
                        cancelTimeout()
                        profile = proxy as BluetoothHidDevice
                        state = state.copy(hid = "Registering")
                        if (Diagnostics.isEnabled(TAG)) trace { "register descriptor=${CompositeReports.descriptor.joinToString(" ") { "%02x".format(it.toInt() and 255) }} mouseId=2 mouseBytes=4 keyboardId=1 keyboardBytes=8" }
                        lifecycle { "register_request" }
                        val accepted = profile!!.registerApp(BluetoothHidDeviceAppSdpSettings(HidRegistration.NAME, HidRegistration.DESCRIPTION, HidRegistration.PROVIDER,
                            HidRegistration.SUBCLASS, CompositeReports.descriptor), null, null, java.util.concurrent.Executor { task -> work { task.run() } }, callback(session))
                        lifecycle { "register_result accepted=$accepted descriptorBytes=${CompositeReports.descriptor.size} subclass=${BluetoothHidDevice.SUBCLASS1_COMBO} mouseId=2 mouseBytes=4 keyboardId=1 keyboardBytes=8" }
                        trace { "registerApp accepted=$accepted; waiting for registration callback" }
                        if (!accepted) { close(reason = "register rejected"); state = state.copy(hid = "Registration rejected", error = "Another HID app or device policy may prevent registration."); timeline.failure(); reconnect.failed(); scheduleRetry() }
                        else armTimeout("HID registration timed out",HidDeadline.Operation.REGISTRATION)
                        publish()
                    }
                }
            }
            override fun onServiceDisconnected(type: Int) = work {
                lifecycle { "profile_lost callbackSession=$session type=$type" }
                if (session == generation) { close(reason = "profile service lost"); state = state.copy(hid = "HID service lost", error = "HID service lost."); timeline.failure(); reconnect.failed(); scheduleRetry(); publish() }
            }
        }
        val profileAccepted = adapter.getProfileProxy(app, listener, BluetoothProfile.HID_DEVICE)
        lifecycle { "profile_request accepted=$profileAccepted" }
        if (!profileAccepted) {
            close(reason = "profile request rejected"); state = state.copy(hid = "HID profile unavailable", error = "This device does not expose a usable HID Device profile."); timeline.failure(); reconnect.failed(); scheduleRetry()
        } else armTimeout("HID profile unavailable or service timed out",HidDeadline.Operation.PROFILE)
        publish()
    }
    private fun callback(session: Int) = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            lifecycle { "registration_callback callbackSession=$session active=$active registered=$registered pluggedHost=${pluggedDevice != null}" }
            trace { "app callbackSession=$session currentSession=$generation active=$active registered=$registered pluggedHost=${pluggedDevice != null}" }
            if (session != generation || !active) return
            cancelTimeout(HidDeadline.Operation.REGISTRATION)
            state = state.copy(registered = registered, hid = if (registered) "Registered" else "Unregistered")
            if (!registered) { close(reason = "registration lost"); state = state.copy(error = "Registration lost. Keep BluePad visible; tap Refresh to retry.") }
            if (registered) {
                if (pluggedDevice != null) reconcileConnected(pluggedDevice)
                if (reconnect.connecting && host == null) connectLast()
                else scheduleRetry(immediate = true)
            } else { timeline.failure(); reconnect.failed(); scheduleRetry() }
            publish()
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, connectionState: Int) {
            lifecycle { "connection_callback callbackSession=$session active=$active matchingHost=${host == null || host == device} state=$connectionState hasHost=${host != null} registered=${state.registered} disconnecting=$disconnecting" }
            trace { "connection callbackSession=$session currentSession=$generation active=$active state=$connectionState matchingHost=${host == null || host == device}" }
            if (session != generation || !active) return
            if (connectionState == BluetoothProfile.STATE_DISCONNECTED && host == device && (state.connected || state.busy) && !reconnect.paused) timeline.failure()
            handleConnectionState(session,device,connectionState)
        }
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            lifecycle { "get_report callbackSession=$session matchingHost=${host == null || host == device} type=$type id=$id bufferSize=$bufferSize eligible=${session == generation && active && host == device}" }
            trace { "getReport callbackSession=$session type=$type id=$id bufferSize=$bufferSize" }
            if (session != generation || !active || host != device) return
            val report = when (type) {
                BluetoothHidDevice.REPORT_TYPE_INPUT -> CompositeReports.input(id.toInt(),bootProtocol,buttons.move(0,0),keyboard.report())
                BluetoothHidDevice.REPORT_TYPE_OUTPUT -> if (id.toInt() == CompositeReports.KEYBOARD_ID) byteArrayOf(keyboardLeds.toByte()) else null
                else -> null
            }
            if (report == null) { val accepted = profile?.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID); lifecycle { "get_report_error code=INVALID_RPT_ID accepted=$accepted" } }
            else if (bufferSize != 0 && bufferSize < report.size) { val accepted = profile?.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_PARAM); lifecycle { "get_report_error code=INVALID_PARAM accepted=$accepted" } }
            else { val accepted = profile?.replyReport(device,type,id,report); lifecycle { "get_report_reply bytes=${report.size} accepted=$accepted" } }
        }
        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            lifecycle { "set_protocol callbackSession=$session matchingHost=${host == null || host == device} protocol=$protocol eligible=${session == generation && active && host == device}" }
            if (session != generation || !active || host != device) return
            bootProtocol = protocol == BluetoothHidDevice.PROTOCOL_BOOT_MODE
            trace { "protocol=$protocol boot=$bootProtocol; wheel available only in report mode" }
        }
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            lifecycle { "set_report callbackSession=$session matchingHost=${host == null || host == device} type=$type id=$id bytes=${data.size} eligible=${session == generation && active && host == device}" }
            if (session != generation || !active || host != device) return
            val leds = if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT) CompositeReports.leds(id.toInt(),data) else null
            if (leds != null) { keyboardLeds = leds; val accepted = profile?.reportError(device,BluetoothHidDevice.ERROR_RSP_SUCCESS); lifecycle { "set_report_response code=SUCCESS accepted=$accepted" } }
            else { val accepted = profile?.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_PARAM); lifecycle { "set_report_response code=INVALID_PARAM accepted=$accepted" } }
        }
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            if (session != generation || !active || host != device) return
            CompositeReports.leds(reportId.toInt(),data)?.let { keyboardLeds = it }
        }
        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            lifecycle { "virtual_cable_unplug callbackSession=$session matchingHost=${host == null || host == device}" }
            trace { "virtual cable unplug callbackSession=$session currentSession=$generation" }
            if (session == generation && active && host == device) { cancelTimeout(); reconnect.disconnect(); preferences.edit().putBoolean("paused",true).apply(); cancelRetry(); inputSession.invalidate(); host = null; bootProtocol = false; inputs.releaseAll(bootProtocol); keyboardLeds = 0; state = state.copy(connected = false, busy = false, connection = "Disconnected"); publish() }
        }
    }
    /** Shared by callbacks and authoritative profile snapshots; never infer HID from ACL pairing. */
    private fun handleConnectionState(session: Int, device: BluetoothDevice, connectionState: Int) {
        val bonded = device in adapter!!.bondedDevices
        val observedState = profile?.getConnectionState(device) ?: BluetoothProfile.STATE_DISCONNECTED
        lifecycle { "connection_observed state=$observedState bonded=$bonded paused=${reconnect.paused}" }
        if (!ConnectionCallbacks.accepts(session,generation,active,host?.address,device.address,
                connectionState,observedState,reconnect.paused,bonded)) {
            lifecycle { "connection_callback_ignored policy_mismatch" }
            if (connectionState != BluetoothProfile.STATE_DISCONNECTED && (host != null && host != device || reconnect.paused || !bonded)) profile?.disconnect(device)
            return
        }
        if (connectionState == BluetoothProfile.STATE_CONNECTED && state.connected && host == device) return
        if (connectionState == BluetoothProfile.STATE_DISCONNECTED && host == null && !state.connected) return
        when (connectionState) {
            BluetoothProfile.STATE_CONNECTED -> { if (disconnecting || !state.registered) { profile?.disconnect(device); return }; cancelTimeout(); cancelRetry(); inputSession.invalidate(); host = device; inputs.releaseAll(bootProtocol); keyboardLeds = 0; retryAddress = device.address; preferences.edit().putString("last_host",device.address).apply(); state = state.copy(connected = true, busy = false, connection = "Connected",error = ""); send(MouseReports.encode(false)); sendKeyboard(keyboard.report()); if (state.connected) reconnect.success() }
            BluetoothProfile.STATE_CONNECTING -> {
                cancelRetry(); reconnect.incoming(); inputSession.invalidate(); inputs.releaseAll(bootProtocol); host = device; state = state.copy(connected = false,busy = true,connection = if (reconnect.attempts > 0) "Reconnecting" else "Connecting")
                if (!disconnecting) armTimeout("Connection timed out",HidDeadline.Operation.CONNECTION)
            }
            BluetoothProfile.STATE_DISCONNECTING -> { inputSession.invalidate(); val neutral = inputs.releaseAll(bootProtocol); if (state.connected) neutral.forEach { safely { profile?.sendReport(device,it.id,it.payload) } }; state = state.copy(connected = false, busy = true, connection = "Disconnected"); if (deadline.operation != HidDeadline.Operation.RECOVERY) armTimeout("Disconnect timed out",HidDeadline.Operation.DISCONNECTION) }
            BluetoothProfile.STATE_DISCONNECTED -> { inputSession.invalidate(); cancelTimeout(); host = null; disconnecting = false; bootProtocol = false; inputs.releaseAll(bootProtocol); keyboardLeds = 0; state = state.copy(connected = false, busy = false, connection = "Disconnected"); reconnect.failed(); scheduleRetry() }
        }
        publish()
    }
    private fun reconcileConnected(device: BluetoothDevice): Boolean {
        val observed = profile?.getConnectionState(device) ?: BluetoothProfile.STATE_DISCONNECTED
        lifecycle { "connection_reconcile_check matchingHost=${host == null || host == device} profileState=$observed registered=${state.registered} disconnecting=$disconnecting paused=${reconnect.paused}" }
        if (!ConnectionCallbacks.canReconcile(active,state.registered,disconnecting,reconnect.paused,
                host?.address,device.address,device in adapter!!.bondedDevices,observed)) return false
        lifecycle { "connection_reconciled matchingHost=${host == null || host == device} profileState=$observed" }
        handleConnectionState(generation,device,BluetoothProfile.STATE_CONNECTED)
        return state.connected || disconnecting
    }
    fun connect(device: BluetoothDevice) = work {
        lifecycle { "manual_connect matchingHost=${host == null || host == device} registered=${state.registered} connected=${state.connected} busy=${state.busy} hasHost=${host != null}" }
        if (!state.registered || state.connected || state.busy || host != null || device !in adapter!!.bondedDevices) return@work
        cancelRetry(); reconnect.manual(); retryAddress = device.address; preferences.edit().putBoolean("paused",false).apply()
        connectInternal(device,false)
    }
    private fun connectInternal(device: BluetoothDevice, automatic: Boolean) {
        if (!ready() || !state.registered || state.connected || host != null) return
        inputSession.invalidate(); host = device
        state = state.copy(connected = false,busy = true,connection = if (automatic) "Reconnecting" else "Connecting",error = "")
        val current = profile?.getConnectionState(device)
        if (current == BluetoothProfile.STATE_CONNECTED && reconcileConnected(device)) return
        val alreadyRequested = current == BluetoothProfile.STATE_CONNECTING
        val acceptedConnect = alreadyRequested || profile?.connect(device) == true
        lifecycle { "connect_result matchingHost=${host == null || host == device} automatic=$automatic profileState=$current alreadyRequested=$alreadyRequested accepted=$acceptedConnect attempts=${reconnect.attempts}" }
        if (acceptedConnect) armTimeout("Connection timed out",HidDeadline.Operation.CONNECTION)
        else { host = null; timeline.failure(); reconnect.failed(); state = state.copy(busy = false,connection = "Connection failed",error = "Connection request rejected"); scheduleRetry() }
        publish()
    }
    fun reconnectLast() = work {
        if (!ready() || state.connected || state.busy || host != null) return@work
        val device = lastDevice() ?: return@work
        cancelRetry(); reconnect.manual(); retryAddress = device.address; preferences.edit().putBoolean("paused",false).apply(); if (state.registered) connectInternal(device,false) else startInternal()
    }
    fun disconnect() {
        inputSession.invalidate()
        work {
        reconnect.disconnect(); preferences.edit().putBoolean("paused",true).apply(); cancelRetry()
        inputSession.invalidate(); releaseInternal()
        if (host != null && !disconnecting) {
            disconnecting = true; host?.let { val accepted = profile?.disconnect(it); lifecycle { "manual_disconnect hasHost=true accepted=$accepted" } }
            state = state.copy(connected = false,busy = true,connection = "Disconnected")
            armTimeout("Disconnect timed out",HidDeadline.Operation.DISCONNECTION)
        } else state = state.copy(connection = "Disconnected",busy = false)
        state = state.copy(error = "Automatic reconnection paused. Connect manually to resume.")
        publish()
        }
    }
    fun setAutomaticReconnect(enabled: Boolean) = work {
        reconnect.enabled = enabled; preferences.edit().putBoolean("automatic",enabled).apply()
        if (!enabled) { cancelRetry(); if (!state.connected && !state.busy) { reconnect.failed(); state = state.copy(connection = "Disconnected") } }
        else {
            reconnect.paused = false; preferences.edit().putBoolean("paused",false).apply()
            if (!state.connected && host == null) { retryAddress = preferences.getString("last_host",null); reconnect.foreground(); scheduleRetry(immediate = true) }
        }
        publish()
    }
    private fun ready() = foreground && BluetoothPermissions.granted(app) && adapter?.isEnabled == true
    private fun lastDevice() = if (ready()) adapter!!.bondedDevices.firstOrNull { it.address == preferences.getString("last_host",null) } else null
    private fun retryDevice() = if (ready()) adapter!!.bondedDevices.firstOrNull { it.address == retryAddress } else null
    private fun cancelRetry() { retryTicket++; retry?.let { main.removeCallbacks(it) }; retry = null; reconnect.cancelPending() }
    private fun connectLast() {
        val device = retryDevice()
        if (device == null) { reconnect.failed(); state = state.copy(busy = false,connection = "Disconnected"); publish() }
        else connectInternal(device,true)
    }
    private fun scheduleRetry(immediate: Boolean = false) {
        val delay = reconnect.reserve(ready() && retryDevice() != null && !state.connected && !disconnecting,immediate)
        if (delay == null) {
            if (ready() && !state.connected && reconnect.enabled && !reconnect.paused && reconnect.attempts >= 3 && !reconnect.pending && !reconnect.connecting)
                state = state.copy(busy = false,connection = "Connection failed",error = "Automatic retries exhausted. Use Reconnect last host.")
            return
        }
        lifecycle { "retry_scheduled delayMs=$delay attempts=${reconnect.attempts} retryHostAvailable=${retryDevice() != null}" }
        val ticket = ++retryTicket
        state = state.copy(busy = false,connection = "Reconnecting")
        retry = Runnable { work {
            if (ticket != retryTicket) return@work
            if (!ready() || retryDevice() == null) { reconnect.cancel(); state = state.copy(connection = "Disconnected",busy = false); publish(); return@work }
            retry = null
            if (!reconnect.begin()) return@work
            if (active && state.registered) connectLast() else startInternal()
        } }
        main.postDelayed(retry!!,delay); publish()
    }
    fun refreshBondedDevices() = work {
        if (!ready()) return@work
        val paired = adapter!!.bondedDevices.sortedBy { it.address }
        state = state.copy(devices = paired,deviceNames = paired.map { "${it.name ?: "Paired device"} (${it.address})" })
        if (host != null && host !in paired) { close(); timeline.failure(); reconnect.failed(); startInternal() }
        else if (retryDevice() == null) { cancelRetry(); reconnect.cancel(); state = state.copy(connection = if (state.connected) "Connected" else "Disconnected") }
        publish()
    }
    fun acknowledgeInputs(token: Long) { inputSession.acknowledge(token) }
    private fun inputWork(block: () -> Unit) {
        val token = inputSession.capture()
        work { if (inputSession.accepts(token) && state.connected) block() }
    }
    fun move(dx: Int, dy: Int) {
        val queuedAt = SystemClock.elapsedRealtime()
        inputWork {
            trace { "move dx=$dx dy=$dy queueMs=${SystemClock.elapsedRealtime() - queuedAt} connected=${state.connected} registered=${state.registered} active=$active" }
            if (state.connected) send(buttons.move(dx, dy)) else trace { "move dropped: disconnected" }
        }
    }
    fun scroll(wheel: Int) {
        val queuedAt = SystemClock.elapsedRealtime()
        inputWork {
            if (Diagnostics.isEnabled(TAG)) trace { "scroll wheel=$wheel queueMs=${SystemClock.elapsedRealtime() - queuedAt} connected=${state.connected} boot=$bootProtocol" }
            if (state.connected && !bootProtocol) send(buttons.scroll(wheel))
            else trace { "scroll dropped connected=${state.connected} boot=$bootProtocol" }
        }
    }
    fun press() = inputWork { trace { "press connected=${state.connected}" }; if (state.connected) send(buttons.press()) }
    fun release() = inputWork {
        val report = buttons.releasePhysical()
        if (state.connected) send(report)
    }
    fun gestureButton(mask: Int, down: Boolean) {
        val queuedAt = SystemClock.uptimeMillis()
        inputWork {
            val allowed = state.connected && (!down || !buttons.physicalHeld())
            trace { "gesture_submit mask=$mask down=$down queuedAt=$queuedAt queueMs=${SystemClock.uptimeMillis()-queuedAt} allowed=$allowed" }
            if (allowed) send(buttons.change("gesture",mask,down))
        }
    }
    fun physicalButton(mask: Int, down: Boolean) = inputWork {
        if (down && !state.connected) return@inputWork
        val report = buttons.change("physical",mask,down)
        if (state.connected) send(report)
    }
    fun keyDown(usage: Int) = inputWork { if (state.connected) keyboard.press(usage)?.let { sendKeyboard(it) }; publish() }
    fun keyUp(usage: Int) = inputWork { keyboard.release(usage)?.let { sendKeyboard(it) }; publish() }
    fun modifier(bit: Int, down: Boolean) = inputWork { if (state.connected) keyboard.modifier(bit,down)?.let { sendKeyboard(it) }; publish() }
    fun keyTap(stroke: UsKeyboard.Stroke) = inputWork {
        if (state.connected) {
            val reports = keyboard.tap(stroke)
            if (Diagnostics.isEnabled(TAG)) trace { "keyboard engine generated reports=${reports.size}" }
            reports.forEach { sendKeyboard(it) }
        } else Log.w(TAG,"Keyboard event dropped: disconnected")
        publish()
    }
    fun releaseInputs() = work { releaseInternal(); publish() }
    fun zoom(compatibility: ZoomCompatibility, steps: Int) {
        val queuedAt = SystemClock.elapsedRealtime()
        inputWork {
        if (!state.connected || bootProtocol) return@inputWork
        val reports = ZoomReports.sequence(compatibility,steps,keyboard.report(),buttons.move(0,0))
        trace { "zoom compatibility=$compatibility steps=$steps reports=${reports.size} queueMs=${SystemClock.elapsedRealtime() - queuedAt}" }
        reports.forEach { if (state.connected) transmit(it.id,it.payload) }
        }
    }
    private fun releaseInternal() {
        inputs.releaseAll(bootProtocol).forEach { if (state.connected) transmit(it.id,it.payload) }
    }
    private fun send(report: ByteArray) = transmit(CompositeReports.MOUSE_ID,MouseReports.forProtocol(report,bootProtocol))
    private fun sendKeyboard(report: ByteArray) { if (state.connected) transmit(CompositeReports.KEYBOARD_ID,report) }
    private fun transmit(id: Int, payload: ByteArray) {
        if (!state.connected || disconnecting) return
        val sequence = ++reportSequence
        val device = host ?: return
        // Never log keyboard contents; keep transport metadata for debugging.
        if (Diagnostics.isEnabled(TAG)) trace { "send sequence=$sequence id=$id length=${payload.size}" }
        if (id == CompositeReports.MOUSE_ID && Diagnostics.isEnabled(TAG)) {
            trace { "mouse payload=${payload.joinToString(" ") { "%02x".format(it.toInt() and 255) }} x=${payload[1]} y=${payload[2]}" }
        }
        val sendStarted = SystemClock.elapsedRealtime()
        val accepted = profile?.sendReport(device,id,payload)
        trace { "send sequence=$sequence accepted=$accepted sendMs=${SystemClock.elapsedRealtime() - sendStarted} (request acceptance, not host delivery)" }
        if (accepted != true) {
            Log.w(TAG,"Report rejected; releasing mouse and keyboard before disconnect")
            ReportRejectionRecovery.run(disconnecting,
                prepare = {
                    inputSession.invalidate(); inputs.releaseAll(bootProtocol); disconnecting = true
                    state = state.copy(connected = false,busy = true,connection = "Connection failed",error = "HID report rejected; recovering connection.")
                    armTimeout("Report failure recovery timed out",HidDeadline.Operation.RECOVERY); publish()
                },
                release = {
                    safely { profile?.sendReport(device,CompositeReports.MOUSE_ID,MouseReports.forProtocol(MouseReports.encode(false),bootProtocol)) }
                    safely { profile?.sendReport(device,CompositeReports.KEYBOARD_ID,keyboard.report()) }
                },
                disconnect = {
                    var acceptedDisconnect = false
                    safely { acceptedDisconnect = profile?.disconnect(device) == true; lifecycle { "recovery_disconnect accepted=$acceptedDisconnect" } }
                    acceptedDisconnect
                },
                rejected = {
                    close(disconnectHost = false, reason = "report rejection and disconnect rejected"); reconnect.failed()
                    state = state.copy(hid = "Inactive",connection = "Connection failed",error = "HID report and disconnect request rejected; recovering connection.")
                    scheduleRetry(); publish()
                })
        }
    }
    private fun cancelTimeout(expected: HidDeadline.Operation? = null) {
        if (!deadline.cancel(expected)) return
        timeout?.let { main.removeCallbacks(it) }; timeout = null
    }
    private fun armTimeout(message: String, operation: HidDeadline.Operation) {
        if (!deadline.begin(operation)) return
        timeout?.let { main.removeCallbacks(it) }
        lifecycle { "deadline_armed operation=$operation timeoutMs=12000 reason=$message" }
        trace { "deadline armed operation=$operation timeoutMs=12000" }
        val session = generation
        val revision = deadline.revision
        timeout = Runnable { work { if (session == generation && active && deadline.revision == revision && deadline.operation == operation) {
            lifecycle { "deadline_expired operation=$operation revision=$revision hasHost=${host != null} connected=${state.connected} busy=${state.busy}" }
            // A callback can be delayed or absent even though the HID profile completed connection.
            if (operation == HidDeadline.Operation.CONNECTION && host?.let { reconcileConnected(it) } == true) return@work
            close(reason = message); timeline.failure(); reconnect.failed(); state = state.copy(hid = "Inactive",connection = "Connection failed",error = "$message. Keep BluePad visible and enable Bluetooth on the computer. Retry here, or connect the phone from the computer’s Bluetooth settings. Pairing alone does not establish HID.")
            if (operation != HidDeadline.Operation.DISCONNECTION || !reconnect.paused) scheduleRetry()
            publish()
        } } }
        main.postDelayed(timeout!!, 12000)
    }
    fun stop() { inputSession.invalidate(); work { trace { "stop" }; foreground = false; cancelRetry(); reconnect.cancel(); close(reason = "stop/background/Bluetooth off"); state = state.copy(bluetooth = when { !BluetoothPermissions.granted(app) -> "Permission required"; adapter == null -> "Unavailable"; !adapter.isEnabled -> "Disabled"; else -> "Enabled" },hid = "Inactive", error = ""); publish() } }
    private fun close(disconnectHost: Boolean = true, reason: String = "cleanup") {
        lifecycle { "profile_close reason=$reason hasHost=${host != null} registered=${state.registered} connected=${state.connected} operation=${deadline.operation}" }
        trace { "close session=$generation connected=${state.connected} pressed=${buttons.pressed}" }
        cancelRetry(); inputSession.invalidate(); active = false; generation++; cancelTimeout(); val neutral = inputs.releaseAll(bootProtocol); keyboardLeds = 0
        val old = profile; val device = host
        profile = null; host = null; disconnecting = false
        state = state.copy(registered = false, connected = false, busy = false, connection = "Disconnected")
        if (old != null) {
            if (device != null) {
                neutral.forEach { report -> safely { old.sendReport(device,report.id,report.payload) } }
                if (disconnectHost) safely { val accepted = old.disconnect(device); lifecycle { "cleanup_disconnect accepted=$accepted matchingHost=${host == null || host == device} reason=$reason" } }
            }
            safely { val accepted = old.unregisterApp(); lifecycle { "unregister_result accepted=$accepted reason=$reason" } }
            safely { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, old); lifecycle { "profile_proxy_closed reason=$reason" } }
        }
        bootProtocol = false
    }
    private fun safely(block: () -> Unit) { try { block() } catch (e: RuntimeException) { Log.w(TAG, "Cleanup operation failed; continuing", e) } }
    fun destroy() { synchronized(worker) { stop(); worker.shutdown() } }
}
