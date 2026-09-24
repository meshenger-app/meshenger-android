/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import d.d.meshenger.MainService.Companion.DEFAULT_PORT
import org.json.JSONObject
import org.json.JSONArray
import java.util.*

class Settings {
    var serverPort = DEFAULT_PORT
    var username = ""
    var secretKey = byteArrayOf()
    var publicKey = byteArrayOf()
    var nightMode = "auto" // on, off, auto
    var speakerphoneMode = "auto" // on, off, auto
    var blockUnknown = false
    var useNeighborTable = false
    var guessEUI64Address = true
    var promptOutgoingCalls = false
    var videoHardwareAcceleration = true
    var disableCallHistory = false
    var disableProximitySensor = false
    var disableAudioProcessing = false
    var showUsernameAsLogo = true
    var pushToTalk = false
    var startOnBootup = false
    var connectRetries = 1
    var connectTimeout = 500
    var enableMicrophoneByDefault = true
    var enableCameraByDefault = false
    var selectFrontCameraByDefault = true
    var disableCpuOveruseDetection = false
    var autoAcceptCalls = false
    var menuPassword = ""
    var hideMenus = false
    var videoDegradationMode = "balanced"
    var cameraResolution = "auto"
    var cameraFramerate = "auto"
    var automaticStatusUpdates = true
    var themeName = "sky_blue"
    var skipStartupPermissionCheck = false
    var audioBitrateMax = "auto" // not used yet
    var videoBitrateMax = "auto" // not used yet
    var settingsMode = "basic"
    // List of IP-Addresses and domain names, without port or interfaces names
    var addresses = mutableListOf<String>()

    fun getOwnContact(): Contact {
        return Contact(username, publicKey, addresses, serverPort)
    }

    fun destroy() {
        publicKey.fill(0)
        secretKey.fill(0)
    }

    companion object {
        fun fromJSON(obj: JSONObject): Settings {
            val s = Settings()
            s.serverPort = obj.optInt("server_port", s.serverPort)
            s.username = obj.getString("username")
            s.secretKey = Utils.hexStringToByteArray(obj.getString("secret_key"))
            s.publicKey = Utils.hexStringToByteArray(obj.getString("public_key"))
            s.nightMode = obj.getString("night_mode")
            s.speakerphoneMode = obj.getString("speakerphone_mode")
            s.blockUnknown = obj.getBoolean("block_unknown")
            s.useNeighborTable = obj.getBoolean("use_neighbor_table")
            s.guessEUI64Address = obj.getBoolean("guess_eui64_address")
            s.videoHardwareAcceleration = obj.getBoolean("video_hardware_acceleration")
            s.disableAudioProcessing = obj.getBoolean("disable_audio_processing")
            s.connectTimeout = obj.getInt("connect_timeout")
            s.disableCallHistory = obj.getBoolean("disable_call_history")
            s.disableProximitySensor = obj.getBoolean("disable_proximity_sensor")
            s.promptOutgoingCalls = obj.getBoolean("prompt_outgoing_calls")
            s.showUsernameAsLogo = obj.getBoolean("show_username_as_logo")
            s.pushToTalk = obj.getBoolean("push_to_talk")
            s.startOnBootup = obj.getBoolean("start_on_bootup")
            s.connectRetries = obj.getInt("connect_retries")
            s.enableMicrophoneByDefault = obj.getBoolean("enable_microphone_by_default")
            s.enableCameraByDefault = obj.getBoolean("enable_camera_by_default")
            s.selectFrontCameraByDefault = obj.getBoolean("select_front_camera_by_default")
            s.disableCpuOveruseDetection = obj.getBoolean("disable_cpu_overuse_detection")
            s.autoAcceptCalls = obj.getBoolean("auto_accept_calls")
            s.menuPassword = obj.getString("menu_password")
            s.videoDegradationMode = obj.getString("video_degradation_mode")
            s.cameraResolution = obj.getString("camera_resolution")
            s.cameraFramerate = obj.getString("camera_framerate")
            s.automaticStatusUpdates = obj.getBoolean("automatic_status_updates")
            s.themeName = obj.getString("theme_name")
            s.skipStartupPermissionCheck = obj.getBoolean("skip_startup_permission_check")
            s.audioBitrateMax = obj.getString("audio_bitrate_max")
            s.videoBitrateMax = obj.getString("video_bitrate_max")
            s.hideMenus = obj.getBoolean("hide_menus")
            s.settingsMode = obj.getString("settings_mode")

            val array = obj.getJSONArray("addresses")
            val addresses = mutableListOf<String>()
            for (i in 0 until array.length()) {
                var address = array[i].toString()
                if (AddressUtils.isIPAddress(address) || AddressUtils.isDomain(address)) {
                    address = address.lowercase(Locale.ROOT)
                } else if (AddressUtils.isMACAddress(address)) {
                    // For backwards compatibility. Going forward a
                    // MAC address is embedded in an EUI64 link local address
                    address = AddressUtils.getLinkLocalFromMAC(address)!!
                } else {
                    Log.d("Settings", "invalid address $address")
                    continue
                }
                if (address !in addresses) {
                    addresses.add(address)
                }
            }
            s.addresses = addresses.toMutableList()

            return s
        }

        fun toJSON(s: Settings): JSONObject {
            val obj = JSONObject()
            obj.put("server_port", s.serverPort)
            obj.put("username", s.username)
            obj.put("secret_key", Utils.byteArrayToHexString(s.secretKey))
            obj.put("public_key", Utils.byteArrayToHexString(s.publicKey))
            obj.put("night_mode", s.nightMode)
            obj.put("speakerphone_mode", s.speakerphoneMode)
            obj.put("block_unknown", s.blockUnknown)
            obj.put("use_neighbor_table", s.useNeighborTable)
            obj.put("guess_eui64_address", s.guessEUI64Address)
            obj.put("connect_timeout", s.connectTimeout)
            obj.put("video_hardware_acceleration", s.videoHardwareAcceleration)
            obj.put("disable_audio_processing", s.disableAudioProcessing)
            obj.put("disable_call_history", s.disableCallHistory)
            obj.put("disable_proximity_sensor", s.disableProximitySensor)
            obj.put("prompt_outgoing_calls", s.promptOutgoingCalls)
            obj.put("show_username_as_logo", s.showUsernameAsLogo)
            obj.put("push_to_talk", s.pushToTalk)
            obj.put("start_on_bootup", s.startOnBootup)
            obj.put("connect_retries", s.connectRetries)
            obj.put("enable_microphone_by_default", s.enableMicrophoneByDefault)
            obj.put("enable_camera_by_default", s.enableCameraByDefault)
            obj.put("select_front_camera_by_default", s.selectFrontCameraByDefault)
            obj.put("disable_cpu_overuse_detection", s.disableCpuOveruseDetection)
            obj.put("auto_accept_calls", s.autoAcceptCalls)
            obj.put("menu_password", s.menuPassword)
            obj.put("video_degradation_mode", s.videoDegradationMode)
            obj.put("camera_resolution", s.cameraResolution)
            obj.put("camera_framerate", s.cameraFramerate)
            obj.put("automatic_status_updates", s.automaticStatusUpdates)
            obj.put("theme_name", s.themeName)
            obj.put("skip_startup_permission_check", s.skipStartupPermissionCheck)
            obj.put("audio_bitrate_max", s.audioBitrateMax)
            obj.put("video_bitrate_max", s.videoBitrateMax)
            obj.put("hide_menus", s.hideMenus)
            obj.put("settings_mode", s.settingsMode)

            val addresses = JSONArray()
            for (i in s.addresses.indices) {
                addresses.put(s.addresses[i])
            }
            obj.put("addresses", addresses)

            return obj
        }
    }
}
