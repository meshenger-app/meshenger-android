package org.rivchain.cuplink.rivmesh

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import org.rivchain.cuplink.CallActivity
import org.rivchain.cuplink.MainActivity
import org.rivchain.cuplink.MainApplication
import org.rivchain.cuplink.PREF_KEY_ENABLED
import org.rivchain.cuplink.R

private const val TAG = "TileService"

@RequiresApi(Build.VERSION_CODES.N)
class MeshTileService: TileService(), AppStateReceiver.StateReceiver {

    private lateinit var receiver: AppStateReceiver

    override fun onCreate() {
        super.onCreate()
        receiver = AppStateReceiver(this)
    }

    /**
     * We need to override the method onBind to avoid crashes that were detected on Android 8
     *
     * The possible reason of crashes is described here:
     * https://github.com/aosp-mirror/platform_frameworks_base/commit/ee68fd889c2dfcd895b8e73fc39d7b97826dc3d8
     */
    override fun onBind(intent: Intent?): IBinder? {
        return try {
            super.onBind(intent)
        } catch (th: Throwable) {
            null
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState((application as MainApplication).getCurrentState())
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        updateTileState((application as MainApplication).getCurrentState())
    }

    override fun onStartListening() {
        super.onStartListening()
        receiver.register(this)
        updateTileState((application as MainApplication).getCurrentState())
    }

    override fun onStopListening() {
        super.onStopListening()
        receiver.unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.unregister(this)
    }

    override fun onClick() {
        super.onClick()
        if (isLocked && !isSecure) {
            return
        }
        if (isCallActivityRunning()) {
            bringActivityToFront(CallActivity::class.java)
        } else {
            bringActivityToFront(MainActivity::class.java)
        }
    }
    private fun bringActivityToFront(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun isCallActivityRunning(): Boolean {
        // Assuming there's a static variable in CallActivity that keeps track of its running state
        return CallActivity.isCallInProgress
    }
    private fun updateTileState(state: State) {
        val tile = qsTile ?: return
        val oldState = tile.state
        val preferences = PreferenceManager.getDefaultSharedPreferences(this.baseContext)
        val enabled = preferences.getBoolean(PREF_KEY_ENABLED, true)
        tile.state = when (enabled) {
            false -> Tile.STATE_INACTIVE
            true -> Tile.STATE_ACTIVE
        }
        var changed = oldState != tile.state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val oldText = tile.subtitle
            tile.subtitle = when (state) {
                State.Enabled -> getText(R.string.tile_enabled)
                State.Connected -> getText(R.string.tile_connected)
                State.Calling -> getText(R.string.is_calling)
                State.CallEnded -> getText(R.string.call_ended)
                else -> getText(R.string.tile_disabled)
            }
            changed = changed || (oldText != tile.subtitle)
        }

        // Update tile if changed state
        if (changed) {
            Log.i(TAG, "Updating tile, old state: $oldState, new state: ${tile.state}")
            /*
              Force set the icon in the tile, because there is a problem on icon tint in the Android Oreo.
              Issue: https://github.com/AdguardTeam/AdguardForAndroid/issues/1996
             */
            tile.icon = Icon.createWithResource(applicationContext, R.mipmap.ic_launcher)
            tile.updateTile()
        }
    }

    override fun onStateChange(state: State) {
        updateTileState(state)
    }
}