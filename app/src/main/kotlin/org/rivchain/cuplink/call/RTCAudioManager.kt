/*
 * This file originates from the WebRTC project that is governed by a BSD-style license.
 * The code was rewritten, but many comments remain.
 */
package org.rivchain.cuplink.call
//package org.appspot.apprtc;

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import org.rivchain.cuplink.Log
import org.rivchain.cuplink.Utils
import java.util.*

/**
 * RTCAudioManager manages all audio related parts.
 */
class RTCAudioManager(contextArg: Context) {
    enum class SpeakerphoneMode {
        AUTO, ON, OFF
    }

    enum class AudioDevice {
        SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH
    }

    interface AudioManagerEvents {
        // Bluetooth is requested by the user, but the permissions are missing.
        fun onBluetoothConnectPermissionRequired()
        // Callback fired once audio device is changed.
        fun onAudioDeviceChanged(oldDevice: AudioDevice, newDevice: AudioDevice)
    }

    private val audioManager = contextArg.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioManagerEvents: AudioManagerEvents? = null
    private var audioManagerInitialized = false

    private var savedAudioMode = AudioManager.MODE_INVALID
    private var savedIsSpeakerPhoneOn = false
    private var savedIsMicrophoneMute = false

    private var speakerphoneMode = SpeakerphoneMode.AUTO
    private var isProximityNear = true // default to speaker turned off for AUTO 
    private var isSpeakerphoneOn = false

    private val bluetoothManager = RTCBluetoothManager(contextArg, this)
    private var requestBluetoothPermissions = true

    /**
     * This method needs to be called when the proximity sensor reports a state change,
     * e.g. from "NEAR to FAR" or from "FAR to NEAR".
     * A call is used to assist device switching (close to ear <=> use headset earpiece if
     * available, far from ear <=> use speaker phone).
     */
    fun onProximitySensorChangedState(isProximityNear: Boolean) {
        Log.d(this, "onProximitySensorChangedState() isProximityNear=$isProximityNear")
        this.isProximityNear = isProximityNear

        if (audioManagerInitialized) {
            updateAudioDeviceState()
        }
    }

    fun getSpeakerphoneMode(): SpeakerphoneMode {
        return speakerphoneMode
    }

    fun setSpeakerphoneMode(mode: SpeakerphoneMode) {
        this.speakerphoneMode = mode
        updateAudioDeviceState()
    }

    fun setEventListener(audioManagerEvents: AudioManagerEvents? = null) {
        this.audioManagerEvents = audioManagerEvents
    }

    /**
     * Checks whether a wired headset is connected or not.
     * This is not a valid indication that audio playback is actually over
     * the wired headset as audio routing depends on other conditions. We
     * only use it as an early indicator (during initialization) of an attached
     * wired headset.
     */
    @Deprecated("")
    private fun hasWiredHeadset(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            audioManager.isWiredHeadsetOn()
        } else {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                    Log.d(this, "hasWiredHeadset() found wired headset")
                    return true
                } else if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                    Log.d(this, "hasWiredHeadset() found USB audio device")
                    return true
                }
            }
            false
        }
    }

    fun getAudioDevice(): AudioDevice {
        return if (bluetoothManager.isBluetoothHeadsetConnected()) {
            AudioDevice.BLUETOOTH
        } else if (isSpeakerphoneOn) {
            AudioDevice.SPEAKER_PHONE
        } else if (hasWiredHeadset()) {
            AudioDevice.WIRED_HEADSET
        } else {
            AudioDevice.EARPIECE
        }
    }

    // Callback method for changes in audio focus.
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    fun start() {
        Log.d(this, "start()")
        Utils.checkIsOnMainThread()
        if (audioManagerInitialized) {
            Log.w(this, "start() already active")
            return
        }

        audioManagerInitialized = true
        requestBluetoothPermissions = true

        // Store current audio state so we can restore it when stop() is called.
        savedAudioMode = audioManager.mode
        savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn
        savedIsMicrophoneMute = audioManager.isMicrophoneMute

        bluetoothManager.start()

        // Create an AudioManager.OnAudioFocusChangeListener instance.
        audioFocusChangeListener = object : AudioManager.OnAudioFocusChangeListener {
            // Called on the listener to notify if the audio focus for this listener has been changed.
            // The `focusChange` value indicates whether the focus was gained, whether the focus was lost,
            // and whether that loss is transient, or whether the new focus holder will hold it for an
            // unknown amount of time.
            // TODO: possibly extend support of handling audio-focus changes. Only contains
            // logging for now.
            override fun onAudioFocusChange(focusChange: Int) {
                val typeOfChange = when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
                    AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
                    else -> "AUDIOFOCUS_INVALID"
                }
                Log.d(this, "onAudioFocusChange: $typeOfChange")
            }
        }

        // Request audio playout focus (without ducking) and install listener for changes in focus.
        val result = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(this, "start() Audio focus request granted for VOICE_CALL streams")
        } else {
            Log.w(this, "start() Audio focus request failed")
        }

        // Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
        // required to be in this mode when playout and/or recording starts for
        // best possible VoIP performance.
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        updateAudioDeviceState()
    }

    fun stop() {
        Log.d(this, "stop()")
        Utils.checkIsOnMainThread()
        if (!audioManagerInitialized) {
            Log.w(this, "stop() Was not initialized.")
            return
        }
        audioManagerInitialized = false

        bluetoothManager.stop()

        // Restore previously stored audio states.
        audioManager.isSpeakerphoneOn = savedIsSpeakerPhoneOn
        audioManager.isMicrophoneMute = savedIsMicrophoneMute

        @SuppressLint("WrongConstant")
        audioManager.mode = savedAudioMode

        audioManager.abandonAudioFocus(audioFocusChangeListener)
        audioFocusChangeListener = null

        isProximityNear = true
        isSpeakerphoneOn = false
        requestBluetoothPermissions = true
    }

    fun getMicrophoneEnabled(): Boolean {
        return !audioManager.isMicrophoneMute
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        audioManager.isMicrophoneMute = !enabled
    }

    fun updateAudioDeviceState() {
        Utils.checkIsOnMainThread()
        Log.d(this, "updateAudioDeviceState()")

        if (!audioManagerInitialized) {
            Log.d(this, "updateAudioDeviceState() RTCAudioManager not running")
            return
        }

        val oldAudioDevice = getAudioDevice()

        if (bluetoothManager.hasBluetoothPermissions()) {
            Log.d(this, "updateAudioDeviceState() BT device handling")
            if (speakerphoneMode == SpeakerphoneMode.AUTO) {
                bluetoothManager.tryConnect()
            } else {
                bluetoothManager.tryDisconnect()
            }
        } else {
            Log.d(this, "updateAudioDeviceState() BT permissions missing")
            if (speakerphoneMode == SpeakerphoneMode.AUTO && requestBluetoothPermissions) {
                audioManagerEvents?.onBluetoothConnectPermissionRequired()
                requestBluetoothPermissions = false
            }
        }

        // The main audio logic.
        val isBluetoothConnected = bluetoothManager.isBluetoothHeadsetConnected()
        isSpeakerphoneOn = when (speakerphoneMode) {
            SpeakerphoneMode.AUTO -> (!isProximityNear && !isBluetoothConnected)
            SpeakerphoneMode.ON -> true
            SpeakerphoneMode.OFF -> false
        }

        val newAudioDevice = getAudioDevice()

        if (audioManager.isSpeakerphoneOn != isSpeakerphoneOn) {
            audioManager.isSpeakerphoneOn = isSpeakerphoneOn
        }

        Log.d(this, "updateAudioDeviceState() "
            + "isSpeakerphoneOn: $isSpeakerphoneOn, "
            + "isProximityNear: $isProximityNear, "
            + "isBluetoothHeadsetOn: $isBluetoothConnected, "
            + "oldDevice: $oldAudioDevice, "
            + "newDevice: $newAudioDevice")

        if (oldAudioDevice != newAudioDevice) {
            audioManagerEvents?.onAudioDeviceChanged(oldAudioDevice, newAudioDevice)
        }
    }

    fun startBluetooth() {
        Log.d(this, "startBluetooth()")
        bluetoothManager.start()
    }
}
