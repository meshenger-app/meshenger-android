package d.d.meshenger.call

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.BluetoothProfile.ServiceListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Process.myPid
import android.os.Process.myUid
import androidx.core.content.ContextCompat
import d.d.meshenger.Log
import d.d.meshenger.call.AppRTCUtils.getThreadInfo
import org.webrtc.ThreadUtils


/**
 * AppRTCProximitySensor manages functions related to Bluetooth devices in the
 * AppRTC demo.
 */
open class AppRTCBluetoothManager private constructor(val context: Context, val mAudioManager: AppRTCAudioManager) {

    companion object {
        private const val TAG = "AppRTCBluetoothManager"

        // Timeout interval for starting or stopping audio to a Bluetooth SCO device.
        private const val BLUETOOTH_SCO_TIMEOUT_MS = 4000

        // Maximum number of SCO connection attempts.
        private const val MAX_SCO_CONNECTION_ATTEMPTS = 2


        /** Construction.  */
        fun create(context: Context, audioManager: AppRTCAudioManager): AppRTCBluetoothManager {
            Log.d(TAG, "create" + getThreadInfo())
            return AppRTCBluetoothManager(context, audioManager)
        }
    }

    // Bluetooth connection state.
    enum class State {
        // Bluetooth is not available; no adapter or Bluetooth is off.
        UNINITIALIZED,  // Bluetooth error happened when trying to start Bluetooth.
        ERROR,  // Bluetooth proxy object for the Headset profile exists, but no connected headset devices,

        // SCO is not started or disconnected.
        HEADSET_UNAVAILABLE,  // Bluetooth proxy object for the Headset profile connected, connected Bluetooth headset

        // present, but SCO is not started or disconnected.
        HEADSET_AVAILABLE,  // Bluetooth audio SCO connection with remote device is closing.
        SCO_DISCONNECTING,  // Bluetooth audio SCO connection with remote device is initiated.
        SCO_CONNECTING,  // Bluetooth audio SCO connection with remote device is established.
        SCO_CONNECTED
    }


    private val audioManager: AudioManager
    private var apprtcContext: Context
//    private var apprtcAudioManager: AppRTCAudioManager //IODevBlue: Redundant Variable

    private var handler: Handler

    var scoConnectionAttempts = 0
    private var bluetoothState: State
    private var bluetoothServiceListener: ServiceListener

    private var bluetoothAdapter: BluetoothAdapter? = null

    private var bluetoothHeadset: BluetoothHeadset? = null

    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothHeadsetReceiver: BroadcastReceiver

    // Runs when the Bluetooth timeout expires. We use that timeout after calling
    // startScoAudio() or stopScoAudio() because we're not guaranteed to get a
    // callback after those calls.
    private val bluetoothTimeoutRunnable = Runnable { bluetoothTimeout() }

    /**
     * Implementation of an interface that notifies BluetoothProfile IPC clients when they have been
     * connected to or disconnected from the service.
     */
    private inner class BluetoothServiceListener : ServiceListener {
        // Called to notify the client when the proxy object has been connected to the service.
        // Once we have the profile proxy object, we can use it to monitor the state of the
        // connection and perform other operations that are relevant to the headset profile.
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HEADSET || bluetoothState === State.UNINITIALIZED) {
                return
            }
            Log.d(TAG, "BluetoothServiceListener.onServiceConnected: BT state=$bluetoothState")
            // Android only supports one connected Bluetooth Headset at a time.
            bluetoothHeadset = proxy as BluetoothHeadset
            updateAudioDeviceState()
            Log.d(TAG, "onServiceConnected done: BT state=$bluetoothState")
        }

        /** Notifies the client when the proxy object has been disconnected from the service.  */
        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HEADSET || bluetoothState === State.UNINITIALIZED) {
                return
            }
            Log.d(TAG, "BluetoothServiceListener.onServiceDisconnected: BT state = $bluetoothState")
            stopScoAudio()
            bluetoothHeadset = null
            bluetoothDevice = null
            bluetoothState = State.HEADSET_UNAVAILABLE
            updateAudioDeviceState()
            Log.d(TAG, "onServiceDisconnected done: BT state = $bluetoothState")
        }
    }

    // Intent broadcast receiver which handles changes in Bluetooth device availability.
    // Detects headset changes and Bluetooth SCO state changes.
    private inner class BluetoothHeadsetBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (bluetoothState === State.UNINITIALIZED) {
                return
            }
            val action = intent.action
            // Change in connection state of the Headset profile. Note that the
            // change does not tell us anything about whether we're streaming
            // audio to BT over SCO. Typically received when user turns on a BT
            // headset while audio is active using another audio device.
            if (action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothHeadset.EXTRA_STATE,
                    BluetoothHeadset.STATE_DISCONNECTED
                )
                Log.d(
                    TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                            + "a=ACTION_CONNECTION_STATE_CHANGED, "
                            + "s=" + stateToString(state) + ", "
                            + "sb=" + isInitialStickyBroadcast + ", "
                            + "BT state: " + bluetoothState
                )
                when(state) {

                    BluetoothHeadset.STATE_CONNECTED -> {
                        scoConnectionAttempts = 0
                        updateAudioDeviceState()
                    }

                    BluetoothHeadset.STATE_CONNECTING -> {}

                    BluetoothHeadset.STATE_DISCONNECTING -> {}

                    BluetoothHeadset.STATE_DISCONNECTED -> {
                        // Bluetooth is probably powered off during the call.
                        stopScoAudio()
                        updateAudioDeviceState()
                    }
                }
                // Change in the audio (SCO) connection state of the Headset profile.
                // Typically received after call to startScoAudio() has finalized.
            } else if (action == BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED
                )
                Log.d(
                    TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                            + "a=ACTION_AUDIO_STATE_CHANGED, "
                            + "s=" + stateToString(state) + ", "
                            + "sb=" + isInitialStickyBroadcast + ", "
                            + "BT state: " + bluetoothState
                )
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    cancelTimer()
                    if (bluetoothState === State.SCO_CONNECTING) {
                        Log.d(TAG, "+++ Bluetooth audio SCO is now connected")
                        bluetoothState = State.SCO_CONNECTED
                        scoConnectionAttempts = 0
                        updateAudioDeviceState()
                    } else {
                        Log.w(TAG, "Unexpected state BluetoothHeadset.STATE_AUDIO_CONNECTED")
                    }
                } else if (state == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
                    Log.d(TAG, "+++ Bluetooth audio SCO is now connecting...")
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    Log.d(TAG, "+++ Bluetooth audio SCO is now disconnected")
                    if (isInitialStickyBroadcast) {
                        Log.d(TAG, "Ignore STATE_AUDIO_DISCONNECTED initial sticky broadcast.")
                        return
                    }
                    updateAudioDeviceState()
                }
            }
            Log.d(TAG, "onReceive done: BT state=$bluetoothState")
        }
    }

    init {
        Log.d(TAG, "ctor")
        ThreadUtils.checkIsOnMainThread()
        apprtcContext = context
        this.audioManager = getAudioManagerService(context)
        bluetoothState = State.UNINITIALIZED
        bluetoothServiceListener = BluetoothServiceListener()
        bluetoothHeadsetReceiver = BluetoothHeadsetBroadcastReceiver()
        handler = Handler(Looper.getMainLooper())

    }

    /** Returns the internal state.  */
    fun getState(): State {
        ThreadUtils.checkIsOnMainThread()
        return bluetoothState
    }

    /**
     * Activates components required to detect Bluetooth devices and to enable
     * BT SCO (audio is routed via BT SCO) for the headset profile. The end
     * state will be HEADSET_UNAVAILABLE but a state machine has started which
     * will start a state change sequence where the final outcome depends on
     * if/when the BT headset is enabled.
     * Example of state change sequence when start() is called while BT device
     * is connected and enabled:
     * UNINITIALIZED --> HEADSET_UNAVAILABLE --> HEADSET_AVAILABLE -->
     * SCO_CONNECTING --> SCO_CONNECTED <==> audio is now routed via BT SCO.
     * Note that the AppRTCAudioManager is also involved in driving this state
     * change.
     */
    fun start() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "start")
        if (!hasPermission(apprtcContext, Manifest.permission.BLUETOOTH)) {
            Log.w(
                TAG,
                "Process (pid=" + myPid().toString() + ") lacks BLUETOOTH permission"
            )
            return
        }
        if (bluetoothState !== State.UNINITIALIZED) {
            Log.w(TAG, "Invalid BT state")
            return
        }
        bluetoothHeadset = null
        bluetoothDevice = null
        scoConnectionAttempts = 0
        // Get a handle to the default local Bluetooth adapter.
        val bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
//        BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Device does not support Bluetooth")
            return
        }
        // Ensure that the device supports use of BT SCO audio for off call use cases.
        if (!audioManager.isBluetoothScoAvailableOffCall) {
            Log.e(TAG, "Bluetooth SCO audio is not available off call")
            return
        }
        logBluetoothAdapterInfo(bluetoothAdapter!!)
        // Establish a connection to the HEADSET profile (includes both Bluetooth Headset and
        // Hands-Free) proxy object and install a listener.
        if (!getBluetoothProfileProxy(
                apprtcContext, bluetoothServiceListener, BluetoothProfile.HEADSET
            )
        ) {
            Log.e(TAG, "BluetoothAdapter.getProfileProxy(HEADSET) failed")
            return
        }
        // Register receivers for BluetoothHeadset change notifications.
        val bluetoothHeadsetFilter = IntentFilter()
        // Register receiver for change in connection state of the Headset profile.
        bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        // Register receiver for change in audio connection state of the Headset profile.
        bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        registerReceiver(bluetoothHeadsetReceiver, bluetoothHeadsetFilter)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED){
            Log.d(TAG, "HEADSET profile state: ${stateToString(bluetoothAdapter!!.getProfileConnectionState(BluetoothProfile.HEADSET))}")
        }

        Log.d(TAG, "Bluetooth proxy for headset profile has started")
        bluetoothState = State.HEADSET_UNAVAILABLE
        Log.d(TAG, "start done: BT state=$bluetoothState")
    }


    /** Stops and closes all components related to Bluetooth audio.  */
    fun stop() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "stop: BT state=$bluetoothState")
        if (bluetoothAdapter == null) {
            return
        }
        // Stop BT SCO connection with remote device if needed.
        stopScoAudio()
        // Close down remaining BT resources.
        if (bluetoothState === State.UNINITIALIZED) {
            return
        }
        unregisterReceiver(bluetoothHeadsetReceiver)
        cancelTimer()
        if (bluetoothHeadset != null) {
            bluetoothAdapter!!.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
            bluetoothHeadset = null
        }
        bluetoothAdapter = null
        bluetoothDevice = null
        bluetoothState = State.UNINITIALIZED
        Log.d(TAG, "stop done: BT state=$bluetoothState")
    }


    /**
     * Starts Bluetooth SCO connection with remote device.
     * Note that the phone application always has the priority on the usage of the SCO connection
     * for telephony. If this method is called while the phone is in call it will be ignored.
     * Similarly, if a call is received or sent while an application is using the SCO connection,
     * the connection will be lost for the application and NOT returned automatically when the call
     * ends. Also note that: up to and including API version JELLY_BEAN_MR1, this method initiates a
     * virtual voice call to the Bluetooth headset. After API version JELLY_BEAN_MR2 only a raw SCO
     * audio connection is established.
     * TODO(henrika): should we add support for virtual voice call to BT headset also for JBMR2 and
     * higher. It might be required to initiates a virtual voice call since many devices do not
     * accept SCO audio without a "call".
     */
    fun startScoAudio(): Boolean {
        ThreadUtils.checkIsOnMainThread()
        Log.d(
            TAG, "startSco: BT state=" + bluetoothState + ", "
                    + "attempts: " + scoConnectionAttempts + ", "
                    + "SCO is on: " + isScoOn()
        )
        if (scoConnectionAttempts >= MAX_SCO_CONNECTION_ATTEMPTS) {
            Log.e(TAG, "BT SCO connection fails - no more attempts")
            return false
        }
        if (bluetoothState !== State.HEADSET_AVAILABLE) {
            Log.e(TAG, "BT SCO connection fails - no headset available")
            return false
        }
        // Start BT SCO channel and wait for ACTION_AUDIO_STATE_CHANGED.
        Log.d(TAG, "Starting Bluetooth SCO and waits for ACTION_AUDIO_STATE_CHANGED...")
        // The SCO connection establishment can take several seconds, hence we cannot rely on the
        // connection to be available when the method returns but instead register to receive the
        // intent ACTION_SCO_AUDIO_STATE_UPDATED and wait for the state to be SCO_AUDIO_STATE_CONNECTED.
        bluetoothState = State.SCO_CONNECTING
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
        scoConnectionAttempts++
        startTimer()
        Log.d(
            TAG, "startScoAudio done: BT state=" + bluetoothState + ", "
                    + "SCO is on: " + isScoOn()
        )
        return true
    }


    /** Stops Bluetooth SCO connection with remote device.  */
    fun stopScoAudio() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(
            TAG, "stopScoAudio: BT state=" + bluetoothState + ", "
                    + "SCO is on: " + isScoOn()
        )
        if (bluetoothState !== State.SCO_CONNECTING && bluetoothState !== State.SCO_CONNECTED) {
            return
        }
        cancelTimer()
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        bluetoothState = State.SCO_DISCONNECTING
        Log.d(
            TAG, "stopScoAudio done: BT state=" + bluetoothState + ", "
                    + "SCO is on: " + isScoOn()
        )
    }

    /**
     * Use the BluetoothHeadset proxy object (controls the Bluetooth Headset
     * Service via IPC) to update the list of connected devices for the HEADSET
     * profile. The internal state will change to HEADSET_UNAVAILABLE or to
     * HEADSET_AVAILABLE and |bluetoothDevice| will be mapped to the connected
     * device if available.
     */
    fun updateDevice() {
        if (bluetoothState === State.UNINITIALIZED || bluetoothHeadset == null) {
            return
        }
        Log.d(TAG, "updateDevice")
        // Get connected devices for the headset profile. Returns the set of
        // devices which are in state STATE_CONNECTED. The BluetoothDevice class
        // is just a thin wrapper for a Bluetooth hardware address.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED){
            val devices = bluetoothHeadset!!.connectedDevices
            if (devices.isEmpty()) {
                bluetoothDevice = null
                bluetoothState = State.HEADSET_UNAVAILABLE
                Log.d(TAG, "No connected bluetooth headset")
            } else {
                // Always use first device in list. Android only supports one device.
                bluetoothDevice = devices[0]
                bluetoothState = State.HEADSET_AVAILABLE
                Log.d(
                    TAG, "Connected bluetooth headset: "
                            + "name=" + bluetoothDevice!!.name + ", "
                            + "state=" + stateToString(
                        bluetoothHeadset!!.getConnectionState(
                            bluetoothDevice
                        )
                    )
                            + ", SCO audio=" + bluetoothHeadset!!.isAudioConnected(bluetoothDevice)
                )
            }
            Log.d(TAG, "updateDevice done: BT state=$bluetoothState")

        }

        }

    /**
     * Stubs for test mocks.
     */
    protected fun getAudioManagerService(context: Context): AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    protected fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?) {
        apprtcContext.registerReceiver(receiver, filter)
    }

    protected fun unregisterReceiver(receiver: BroadcastReceiver?) {
        apprtcContext.unregisterReceiver(receiver)
    }

    protected fun getBluetoothProfileProxy(
        context: Context, listener: ServiceListener, profile: Int
    ): Boolean = bluetoothAdapter!!.getProfileProxy(context, listener, profile)

    protected fun hasPermission(context: Context, permission: String): Boolean =
        (apprtcContext.checkPermission(permission, myPid(), myUid())
                == PackageManager.PERMISSION_GRANTED)

    /** Logs the state of the local Bluetooth adapter.  */
    @SuppressLint("HardwareIds")
    fun logBluetoothAdapterInfo(localAdapter: BluetoothAdapter) {
        //TODO (IODevBlue): This Log cannot be possible because calls requires the LOCAL_MAC_ADDRESS permission that is only granted to system apps
//        Log.d(
//            TAG, "BluetoothAdapter: "
//                    + "enabled=" + localAdapter.isEnabled + ", "
//                    + "state=" + stateToString(localAdapter.state) + ", "
//                    + "name=" + localAdapter.name + ", "
//                    + "address=" + localAdapter.address
//        )
        // Log the set of BluetoothDevice objects that are bonded (paired) to the local adapter.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED){
            val pairedDevices = localAdapter.bondedDevices
            if (pairedDevices.isNotEmpty()) {
                Log.d(TAG, "paired devices:")
                for (device in pairedDevices) {
                    Log.d(TAG, " name=" + device.name + ", address=" + device.address)
                }
            }
        }

    }

    /** Ensures that the audio manager updates its list of available audio devices.  */
    fun updateAudioDeviceState() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "updateAudioDeviceState")
        mAudioManager.updateAudioDeviceState()
    }

    /** Starts timer which times out after BLUETOOTH_SCO_TIMEOUT_MS milliseconds.  */
    fun startTimer() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "startTimer")
        handler.postDelayed(bluetoothTimeoutRunnable, BLUETOOTH_SCO_TIMEOUT_MS.toLong())
    }

    /** Cancels any outstanding timer tasks.  */
    fun cancelTimer() {
        ThreadUtils.checkIsOnMainThread()
        Log.d(TAG, "cancelTimer")
        handler.removeCallbacks(bluetoothTimeoutRunnable)
    }

    /**
     * Called when start of the BT SCO channel takes too long time. Usually
     * happens when the BT device has been turned on during an ongoing call.
     */
    fun bluetoothTimeout() {
        ThreadUtils.checkIsOnMainThread()
        if (bluetoothState === State.UNINITIALIZED || bluetoothHeadset == null) {
            return
        }
        Log.d(
            TAG, "bluetoothTimeout: BT state=" + bluetoothState + ", "
                    + "attempts: " + scoConnectionAttempts + ", "
                    + "SCO is on: " + isScoOn()
        )
        if (bluetoothState !== State.SCO_CONNECTING) {
            return
        }
        // Bluetooth SCO should be connecting; check the latest result.
        var scoConnected = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED){
            val devices = bluetoothHeadset!!.connectedDevices
            if (devices.size > 0) {
                bluetoothDevice = devices[0]
                if (bluetoothHeadset!!.isAudioConnected(bluetoothDevice)) {
                    Log.d(TAG, "SCO connected with " + bluetoothDevice!!.name)
                    scoConnected = true
                } else {
                    Log.d(TAG, "SCO is not connected with " + bluetoothDevice!!.name)
                }
            }
        }

        if (scoConnected) {
            // We thought BT had timed out, but it's actually on; updating state.
            bluetoothState = State.SCO_CONNECTED
            scoConnectionAttempts = 0
        } else {
            // Give up and "cancel" our request by calling stopBluetoothSco().
            Log.w(TAG, "BT failed to connect after timeout")
            stopScoAudio()
        }
        updateAudioDeviceState()
        Log.d(TAG, "bluetoothTimeout done: BT state=$bluetoothState")
    }

    /** Checks whether audio uses Bluetooth SCO.  */
    fun isScoOn(): Boolean = audioManager.isBluetoothScoOn

    /** Converts BluetoothAdapter states into local string representations.  */
    fun stateToString(state: Int): String {
        return when (state) {
            BluetoothAdapter.STATE_DISCONNECTED -> "DISCONNECTED"
            BluetoothAdapter.STATE_CONNECTED -> "CONNECTED"
            BluetoothAdapter.STATE_CONNECTING -> "CONNECTING"
            BluetoothAdapter.STATE_DISCONNECTING -> "DISCONNECTING"
            BluetoothAdapter.STATE_OFF -> "OFF"
            BluetoothAdapter.STATE_ON -> "ON"
            BluetoothAdapter.STATE_TURNING_OFF ->  "TURNING_OFF"       // Indicates the local Bluetooth adapter is turning off. Local clients should immediately
                // attempt graceful disconnection of any remote links.

            BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"        // Indicates the local Bluetooth adapter is turning on. However local clients should wait
                // for STATE_ON before attempting to use the adapter.

            else -> "INVALID"
        }
    }

}