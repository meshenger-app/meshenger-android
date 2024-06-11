package org.rivchain.cuplink.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import org.rivchain.cuplink.R

object PermissionManager {
    @JvmStatic
    fun havePostNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) ==
                PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun haveCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) ==
                PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun haveMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) ==
                PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun haveDrawOverlaysPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            !Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    @JvmStatic
    fun buildPostPermissionDialog(
        context: Context,
        onAccept: (() -> Unit),
        onDecline: (() -> Unit)
    ): AlertDialog {
        val builder = AlertDialog.Builder(context, R.style.FullPPTCDialog)
        builder.setMessage(R.string.permission_notification_text)
            .setPositiveButton(R.string.button_yes) { dialog, _ ->
                dialog.dismiss()
                onAccept()
            }
            .setNegativeButton(R.string.button_no) { dialog, _ ->
                dialog.dismiss()
                onDecline()
            }
        return builder.create()
    }
}