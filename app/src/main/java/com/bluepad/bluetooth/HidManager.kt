package com.bluepad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.bluepad.hid.CompositeReports
import com.bluepad.hid.HidInputState
import com.bluepad.hid.UsKeyboard
import com.bluepad.hid.MouseReports
import java.util.concurrent.Executors

/** All mutable state lives on a serial worker. UI receives immutable snapshots. */
@SuppressLint("MissingPermission")
class HidManager(context: Context, private val update: (State) -> Unit) {
    private companion object { const val TAG = "BluePadHid" }
    data class State(val bluetooth: String = "Checking", val hid: String = "Inactive", val connection: String = "Disconnected",
        val devices: List<BluetoothDevice> = emptyList(), val deviceNames: List<String> = emptyList(), val registered: Boolean = false,
        val connected: Boolean = false, val modifiers: Int = 0, val busy: Boolean = false, val error: String = "")
    private val app = context.applicationContext
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var state = State()
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
    // Opt in for a short hardware trace: adb shell setprop log.tag.BluePadHid DEBUG
    private fun trace(message: String) { if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message) }
    private fun publish() { state = state.copy(modifiers = keyboard.modifiers); trace("state session=$generation active=$active registered=${state.registered} connected=${state.connected} busy=${state.busy} profile=${profile != null} host=${host != null} hid=${state.hid} connection=${state.connection}"); val snapshot = state; main.post { update(snapshot) } }
    private fun work(block: () -> Unit) { synchronized(worker) { if (worker.isShutdown) return; worker.execute {
        try { block() } catch (e: SecurityException) { Log.e(TAG, "Bluetooth permission failure; closing session", e); close(); state = State(bluetooth = "Permission required", error = "Grant Nearby devices permission."); publish() }
        catch (e: RuntimeException) { Log.e(TAG, "Bluetooth operation failure; closing session", e); close(); state = state.copy(hid = "Failed", error = e.message ?: "Bluetooth operation failed"); publish() }
    } } }
    fun start() = work {
        trace("start active=$active")
        if (active) return@work
        active = true
        if (!BluetoothPermissions.granted(app)) { state = State(bluetooth = "Permission required"); active = false; publish(); return@work }
        if (adapter == null || !adapter.isEnabled) {
            state = State(bluetooth = if (adapter == null) "Unavailable" else "Disabled"); active = false; publish(); return@work
        }
        val paired = adapter.bondedDevices.sortedBy { it.address }
        state = State(bluetooth = "Enabled", hid = "Acquiring HID profile", devices = paired,
            deviceNames = paired.map { "${it.name ?: "Paired device"} (${it.address})" })
        val session = ++generation
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(type: Int, proxy: BluetoothProfile) {
                synchronized(worker) {
                    if (worker.isShutdown) { safely { adapter.closeProfileProxy(type, proxy) }; return }
                    work {
                        trace("profile acquired callbackSession=$session currentSession=$generation active=$active")
                        if (!active || session != generation) { adapter.closeProfileProxy(type, proxy); return@work }
                        cancelTimeout()
                        profile = proxy as BluetoothHidDevice
                        state = state.copy(hid = "Registering")
                        if (Log.isLoggable(TAG, Log.DEBUG)) trace("register descriptor=${CompositeReports.descriptor.joinToString(" ") { "%02x".format(it.toInt() and 255) }} mouseId=2 mouseBytes=4 keyboardId=1 keyboardBytes=8")
                        val accepted = profile!!.registerApp(BluetoothHidDeviceAppSdpSettings("BluePad", "Mouse and US keyboard", "BluePad",
                            BluetoothHidDevice.SUBCLASS1_COMBO, CompositeReports.descriptor), null, null, java.util.concurrent.Executor { task -> work { task.run() } }, callback(session))
                        trace("registerApp accepted=$accepted; waiting for registration callback")
                        if (!accepted) { close(); state = state.copy(hid = "Registration rejected", error = "Another HID app or device policy may prevent registration.") }
                        else armTimeout("HID registration timed out",HidDeadline.Operation.REGISTRATION)
                        publish()
                    }
                }
            }
            override fun onServiceDisconnected(type: Int) = work {
                if (session == generation) { close(); state = state.copy(hid = "HID service lost", error = "Tap Refresh to retry."); publish() }
            }
        }
        if (!adapter.getProfileProxy(app, listener, BluetoothProfile.HID_DEVICE)) {
            close(); state = state.copy(hid = "HID profile unavailable", error = "This device does not expose a usable HID Device profile.")
        } else armTimeout("HID profile unavailable or service timed out",HidDeadline.Operation.PROFILE)
        publish()
    }
    private fun callback(session: Int) = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            trace("app callbackSession=$session currentSession=$generation active=$active registered=$registered pluggedHost=${pluggedDevice != null}")
            if (session != generation || !active) return
            cancelTimeout(HidDeadline.Operation.REGISTRATION)
            state = state.copy(registered = registered, hid = if (registered) "Registered" else "Unregistered")
            if (!registered) { close(); state = state.copy(error = "Registration lost. Keep BluePad visible; tap Refresh to retry.") }
            publish()
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, connectionState: Int) {
            trace("connection callbackSession=$session currentSession=$generation active=$active state=$connectionState matchingHost=${host == null || host == device}")
            if (session != generation || !active) return
            if (host != null && host != device) { profile?.disconnect(device); return }
            when (connectionState) {
                BluetoothProfile.STATE_CONNECTED -> { if (disconnecting || !state.registered) { profile?.disconnect(device); return }; cancelTimeout(); host = device; inputs.releaseAll(bootProtocol); keyboardLeds = 0; state = state.copy(connected = true, busy = false, connection = "Connected"); send(MouseReports.encode(false)); sendKeyboard(keyboard.report()) }
                BluetoothProfile.STATE_CONNECTING -> {
                    host = device; state = state.copy(busy = true,connection = "Connecting")
                    if (!disconnecting) armTimeout("Connection timed out",HidDeadline.Operation.CONNECTION)
                }
                BluetoothProfile.STATE_DISCONNECTING -> state = state.copy(connected = false, busy = true, connection = "Disconnecting")
                BluetoothProfile.STATE_DISCONNECTED -> { cancelTimeout(); host = null; disconnecting = false; bootProtocol = false; inputs.releaseAll(bootProtocol); keyboardLeds = 0; state = state.copy(connected = false, busy = false, connection = "Disconnected") }
            }
            publish()
        }
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            trace("getReport callbackSession=$session type=$type id=$id bufferSize=$bufferSize")
            if (session != generation || !active) return
            val report = when (type) {
                BluetoothHidDevice.REPORT_TYPE_INPUT -> CompositeReports.input(id.toInt(),bootProtocol,buttons.move(0,0),keyboard.report())
                BluetoothHidDevice.REPORT_TYPE_OUTPUT -> if (id.toInt() == CompositeReports.KEYBOARD_ID) byteArrayOf(keyboardLeds.toByte()) else null
                else -> null
            }
            if (report == null) profile?.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            else if (bufferSize != 0 && bufferSize < report.size) profile?.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_PARAM)
            else profile?.replyReport(device,type,id,report)
        }
        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            if (session != generation || !active) return
            bootProtocol = protocol == BluetoothHidDevice.PROTOCOL_BOOT_MODE
            trace("protocol=$protocol boot=$bootProtocol; wheel available only in report mode")
        }
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            if (session != generation || !active) return
            val leds = if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT) CompositeReports.leds(id.toInt(),data) else null
            if (leds != null) { keyboardLeds = leds; profile?.reportError(device,BluetoothHidDevice.ERROR_RSP_SUCCESS) }
            else profile?.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_PARAM)
        }
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            if (session != generation || !active) return
            CompositeReports.leds(reportId.toInt(),data)?.let { keyboardLeds = it }
        }
        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            trace("virtual cable unplug callbackSession=$session currentSession=$generation")
            if (session == generation && active) { host = null; bootProtocol = false; inputs.releaseAll(bootProtocol); keyboardLeds = 0; state = state.copy(connected = false, busy = false, connection = "Unplugged"); publish() }
        }
    }
    fun connect(device: BluetoothDevice) = work {
        if (!state.registered || state.busy || host != null) return@work
        host = device
        if (profile?.connect(device) == true) { state = state.copy(busy = true, connection = "Connecting", error = ""); armTimeout("Connection timed out",HidDeadline.Operation.CONNECTION) }
        else { host = null; state = state.copy(error = "Connection request rejected") }
        publish()
    }
    fun disconnect() = work { if (host == null || disconnecting) return@work; disconnecting = true; releaseInternal(); host?.let { profile?.disconnect(it) }; state = state.copy(connected = false, busy = true, connection = "Disconnecting"); armTimeout("Disconnect timed out",HidDeadline.Operation.DISCONNECTION); publish() }
    fun move(dx: Int, dy: Int) {
        val queuedAt = SystemClock.elapsedRealtime()
        work {
            trace("move dx=$dx dy=$dy queueMs=${SystemClock.elapsedRealtime() - queuedAt} connected=${state.connected} registered=${state.registered} active=$active")
            if (state.connected) send(buttons.move(dx, dy)) else trace("move dropped: disconnected")
        }
    }
    fun scroll(wheel: Int) {
        val queuedAt = SystemClock.elapsedRealtime()
        work {
            if (Log.isLoggable(TAG, Log.DEBUG)) trace("scroll wheel=$wheel queueMs=${SystemClock.elapsedRealtime() - queuedAt} connected=${state.connected} boot=$bootProtocol")
            if (state.connected && !bootProtocol) send(buttons.scroll(wheel))
            else trace("scroll dropped connected=${state.connected} boot=$bootProtocol")
        }
    }
    fun press() = work { trace("press connected=${state.connected}"); if (state.connected) send(buttons.press()) }
    fun release() = work {
        val report = buttons.releasePhysical()
        if (state.connected) send(report)
    }
    fun gestureButton(mask: Int, down: Boolean) = work {
        if (state.connected && (!down || !buttons.physicalHeld())) send(buttons.change("gesture",mask,down))
    }
    fun physicalButton(mask: Int, down: Boolean) = work {
        if (down && !state.connected) return@work
        val report = buttons.change("physical",mask,down)
        if (state.connected) send(report)
    }
    fun keyDown(usage: Int) = work { if (state.connected) keyboard.press(usage)?.let { sendKeyboard(it) }; publish() }
    fun keyUp(usage: Int) = work { keyboard.release(usage)?.let { sendKeyboard(it) }; publish() }
    fun modifier(bit: Int, down: Boolean) = work { if (state.connected) keyboard.modifier(bit,down)?.let { sendKeyboard(it) }; publish() }
    fun keyTap(stroke: UsKeyboard.Stroke) = work {
        if (state.connected) {
            val reports = keyboard.tap(stroke)
            if (Log.isLoggable(TAG,Log.DEBUG)) trace("keyboard engine generated reports=${reports.size}")
            reports.forEach { sendKeyboard(it) }
        } else Log.w(TAG,"Keyboard event dropped: disconnected")
        publish()
    }
    fun releaseInputs() = work { releaseInternal(); publish() }
    private fun releaseInternal() {
        inputs.releaseAll(bootProtocol).forEach { if (state.connected) transmit(it.id,it.payload) }
    }
    private fun send(report: ByteArray) = transmit(CompositeReports.MOUSE_ID,MouseReports.forProtocol(report,bootProtocol))
    private fun sendKeyboard(report: ByteArray) { if (state.connected) transmit(CompositeReports.KEYBOARD_ID,report) }
    private fun transmit(id: Int, payload: ByteArray) {
        val sequence = ++reportSequence
        val device = host ?: return
        // Never log keyboard contents; keep transport metadata for debugging.
        if (Log.isLoggable(TAG,Log.DEBUG)) trace("send sequence=$sequence id=$id length=${payload.size}")
        if (id == CompositeReports.MOUSE_ID && Log.isLoggable(TAG,Log.DEBUG)) {
            trace("mouse payload=${payload.joinToString(" ") { "%02x".format(it.toInt() and 255) }} x=${payload[1]} y=${payload[2]}")
        }
        val accepted = profile?.sendReport(device,id,payload)
        trace("send sequence=$sequence accepted=$accepted (request acceptance, not host delivery)")
        if (accepted != true) {
            Log.w(TAG,"Report rejected; releasing mouse and keyboard before disconnect")
            inputs.releaseAll(bootProtocol)
            safely { profile?.sendReport(device,CompositeReports.MOUSE_ID,MouseReports.forProtocol(MouseReports.encode(false),bootProtocol)) }
            safely { profile?.sendReport(device,CompositeReports.KEYBOARD_ID,keyboard.report()) }
            disconnecting = true
            safely { profile?.disconnect(device) }
            state = state.copy(connected = false,busy = true,connection = "Disconnecting",error = "HID report rejected; reconnect.")
            armTimeout("Report failure recovery timed out",HidDeadline.Operation.RECOVERY); publish()
        }
    }
    private fun cancelTimeout(expected: HidDeadline.Operation? = null) {
        if (!deadline.cancel(expected)) return
        timeout?.let { main.removeCallbacks(it) }; timeout = null
    }
    private fun armTimeout(message: String, operation: HidDeadline.Operation) {
        if (!deadline.begin(operation)) return
        timeout?.let { main.removeCallbacks(it) }
        trace("deadline armed operation=$operation timeoutMs=12000")
        val session = generation
        timeout = Runnable { work { if (session == generation && active) { close(); state = state.copy(hid = "Inactive", error = "$message. Check the host HID service and bond state before retrying."); publish() } } }
        main.postDelayed(timeout!!, 12000)
    }
    fun stop() = work { trace("stop"); close(); state = state.copy(hid = "Inactive", error = ""); publish() }
    private fun close() {
        trace("close session=$generation connected=${state.connected} pressed=${buttons.pressed}")
        active = false; generation++; cancelTimeout(); val neutral = inputs.releaseAll(bootProtocol); keyboardLeds = 0
        val old = profile; val device = host
        profile = null; host = null; disconnecting = false
        state = state.copy(registered = false, connected = false, busy = false, connection = "Disconnected")
        if (old != null) {
            if (device != null) {
                neutral.forEach { report -> safely { old.sendReport(device,report.id,report.payload) } }
                safely { old.disconnect(device) }
            }
            safely { old.unregisterApp() }
            safely { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, old) }
        }
        bootProtocol = false
    }
    private fun safely(block: () -> Unit) { try { block() } catch (e: RuntimeException) { Log.w(TAG, "Cleanup operation failed; continuing", e) } }
    fun destroy() { synchronized(worker) { stop(); worker.shutdown() } }
}
