package org.rivchain.cuplink.rivmesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

const val STATE_STARTING = "starting"
const val STATE_ENABLED = "enabled"
const val STATE_DISABLED = "disabled"
const val STATE_CONNECTED = "connected"
const val STATE_RECONNECTING = "reconnecting"
const val STATE_CALLING= "calling"
const val STATE_CALL_ENDED = "call_ended"

class AppStateReceiver(var receiver: StateReceiver): BroadcastReceiver() {

    companion object {
        const val APP_STATE_INTENT = "org.rivchain.cuplink.rivmesh.MeshStateReceiver.STATE"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        val state = when (intent?.getStringExtra("state")) {
            STATE_STARTING -> State.Starting
            STATE_ENABLED -> State.Enabled
            STATE_DISABLED -> State.Disabled
            STATE_CONNECTED -> State.Connected
            STATE_RECONNECTING -> State.Reconnecting
            STATE_CALLING -> State.Calling
            STATE_CALL_ENDED -> State.CallEnded
            else -> State.Unknown
        }
        receiver.onStateChange(state)
    }

    fun register(context: Context) {
        LocalBroadcastManager.getInstance(context).registerReceiver(
            this, IntentFilter(APP_STATE_INTENT)
        )
    }

    fun unregister(context: Context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
    }

    interface StateReceiver {
        fun onStateChange(state: State)
    }
}

/**
 * A class-supporter with an App state.
 * Calling state is received by CallActivity
 */
enum class State {
    Starting, Unknown, Disabled, Enabled, Connected, Reconnecting, Calling, CallEnded;
}