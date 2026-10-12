package com.bluepad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bluepad.hid.MouseButtons
import com.bluepad.hid.MouseReports
import java.util.concurrent.Executors

/** All mutable state lives on a serial worker. UI receives immutable snapshots. */
@SuppressLint("MissingPermission")
class HidManager(context: Context, private val update: (State) -> Unit) {
    data class State(val bluetooth: String = "Checking", val hid: String = "Inactive", val connection: String = "Disconnected",
        val devices: List<BluetoothDevice> = emptyList(), val deviceNames: List<String> = emptyList(), val registered: Boolean = false,
        val connected: Boolean = false, val busy: Boolean = false, val error: String = "")
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
    private val buttons = MouseButtons()
    private var timeout: Runnable? = null
    private fun publish() { val snapshot = state; main.post { update(snapshot) } }
    private fun work(block: () -> Unit) { synchronized(worker) { if (worker.isShutdown) return; worker.execute {
        try { block() } catch (_: SecurityException) { close(); state = State(bluetooth = "Permission required", error = "Grant Nearby devices permission."); publish() }
        catch (e: RuntimeException) { close(); state = state.copy(hid = "Failed", error = e.message ?: "Bluetooth operation failed"); publish() }
    } } }
    fun start() = work {
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
                        if (!active || session != generation) { adapter.closeProfileProxy(type, proxy); return@work }
                        cancelTimeout()
                        profile = proxy as BluetoothHidDevice
                        state = state.copy(hid = "Registering")
                        val accepted = profile!!.registerApp(BluetoothHidDeviceAppSdpSettings("BluePad", "Relative mouse", "BluePad",
                            BluetoothHidDevice.SUBCLASS1_MOUSE, MouseReports.descriptor), null, null, java.util.concurrent.Executor { task -> work { task.run() } }, callback(session))
                        if (!accepted) { close(); state = state.copy(hid = "Registration rejected", error = "Another HID app or device policy may prevent registration.") }
                        else armTimeout("HID registration timed out")
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
        } else armTimeout("HID profile unavailable or service timed out")
        publish()
    }
    private fun callback(session: Int) = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (session != generation || !active) return
            cancelTimeout()
            state = state.copy(registered = registered, hid = if (registered) "Registered" else "Unregistered")
            if (!registered) { close(); state = state.copy(error = "Registration lost. Keep BluePad visible; tap Refresh to retry.") }
            publish()
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, connectionState: Int) {
            if (session != generation || !active) return
            if (host != null && host != device) { profile?.disconnect(device); return }
            when (connectionState) {
                BluetoothProfile.STATE_CONNECTED -> { if (disconnecting || !state.registered) { profile?.disconnect(device); return }; cancelTimeout(); host = device; buttons.release(); state = state.copy(connected = true, busy = false, connection = "Connected"); send(MouseReports.encode(false)) }
                BluetoothProfile.STATE_CONNECTING -> { host = device; state = state.copy(busy = true, connection = "Connecting") }
                BluetoothProfile.STATE_DISCONNECTING -> state = state.copy(connected = false, busy = true, connection = "Disconnecting")
                BluetoothProfile.STATE_DISCONNECTED -> { cancelTimeout(); host = null; disconnecting = false; buttons.release(); state = state.copy(connected = false, busy = false, connection = "Disconnected") }
            }
            publish()
        }
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            if (session != generation || !active) return
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == 0 && (bufferSize == 0 || bufferSize >= 3))
                profile?.replyReport(device, type, id, buttons.move(0,0))
            else profile?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
        }
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            if (session == generation && active) profile?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
        }
        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            if (session == generation && active) { host = null; buttons.release(); state = state.copy(connected = false, busy = false, connection = "Unplugged"); publish() }
        }
    }
    fun connect(device: BluetoothDevice) = work {
        if (!state.registered || state.busy || host != null) return@work
        host = device
        if (profile?.connect(device) == true) { state = state.copy(busy = true, connection = "Connecting", error = ""); armTimeout("Connection timed out") }
        else { host = null; state = state.copy(error = "Connection request rejected") }
        publish()
    }
    fun disconnect() = work { if (host == null || disconnecting) return@work; disconnecting = true; releaseInternal(); host?.let { profile?.disconnect(it) }; state = state.copy(connected = false, busy = true, connection = "Disconnecting"); armTimeout("Disconnect timed out"); publish() }
    fun move(dx: Int, dy: Int) = work { if (state.connected) send(buttons.move(dx, dy)) }
    fun press() = work { if (state.connected) send(buttons.press()) }
    fun release() = work { releaseInternal() }
    private fun releaseInternal() { val report = buttons.release(); if (state.connected) send(report) }
    private fun send(report: ByteArray) {
        val device = host ?: return
        if (profile?.sendReport(device, 0, report) != true) {
            buttons.release()
            // Best effort neutral report, then disconnect so a failed press/release cannot linger.
            profile?.sendReport(device, 0, MouseReports.encode(false))
            disconnecting = true
            profile?.disconnect(device)
            state = state.copy(connected = false, busy = true, connection = "Disconnecting", error = "Mouse report rejected; reconnect.")
            armTimeout("Report failure recovery timed out"); publish()
        }
    }
    private fun cancelTimeout() { timeout?.let { main.removeCallbacks(it) }; timeout = null }
    private fun armTimeout(message: String) {
        cancelTimeout()
        val session = generation
        timeout = Runnable { work { if (session == generation && active) { close(); state = state.copy(hid = "Inactive", error = "$message. Tap Refresh to retry."); publish() } } }
        main.postDelayed(timeout!!, 12000)
    }
    fun stop() = work { close(); state = state.copy(hid = "Inactive", error = ""); publish() }
    private fun close() {
        active = false; generation++; cancelTimeout(); buttons.release()
        val old = profile; val device = host
        profile = null; host = null; disconnecting = false
        state = state.copy(registered = false, connected = false, busy = false, connection = "Disconnected")
        if (old != null) {
            if (device != null) {
                safely { old.sendReport(device, 0, MouseReports.encode(false)) }
                safely { old.disconnect(device) }
            }
            safely { old.unregisterApp() }
            safely { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, old) }
        }
    }
    private fun safely(block: () -> Unit) { try { block() } catch (_: RuntimeException) { /* Cleanup must continue after radio or permission loss. */ } }
    fun destroy() { synchronized(worker) { stop(); worker.shutdown() } }
}
