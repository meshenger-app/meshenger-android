/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import java.util.Locale
import androidx.core.net.toUri

class AskForMissingPermissionsActivity : BaseActivity() {
    private lateinit var progressTextView: TextView
    private lateinit var questionTextView: TextView
    private lateinit var askButton: Button
    private lateinit var skipButton: Button

    private lateinit var permissions: AskPermissions

    private var questionCounter = 0
    private var questionsToAsk = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this, "onCreate()")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_ask_for_missing_permissions)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.apply {
            setNavigationOnClickListener {
                finish()
            }
        }

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowTitleEnabled(true)
        }

        progressTextView = findViewById(R.id.ProgressTextView)
        questionTextView = findViewById(R.id.QuestionTextView)
        askButton = findViewById(R.id.AskButton)
        skipButton = findViewById(R.id.SkipButton)

        title = getString(R.string.missing_permissions)

        skipButton.setOnClickListener {
            continueQuestions()
        }

        permissions = AskPermissions(applicationContext, Database.firstStart)
        questionsToAsk = permissions.getQuestionCount()

        // start questions
        continueQuestions()
    }

    private class AskPermissions(context: Context, firstStart: Boolean) {
        // required to bring an incoming call from to the foreground
        var doAskOverlayPermission = true

        // required for running the MainService to listen for incoming calls
        var doAskPostNotificationPermission = true

        // required for android.permission.FOREGROUND_SERVICE_CAMERA
        var doAskCameraPermission = true

        // required for android.permission.FOREGROUND_SERVICE_MICROPHONE
        var doAskRecordAudioPermission = true

        // optional
        var doAskBluetoothConnectPermission = false

        // helper to convert Boolean to Int
        private val Boolean.int
            get() = if (this) 1 else 0

        fun getQuestionCount(): Int {
            return (doAskOverlayPermission.int
                + doAskPostNotificationPermission.int
                + doAskCameraPermission.int
                + doAskRecordAudioPermission.int
                + doAskBluetoothConnectPermission.int)
        }

        init {
            if (firstStart) {
                // ask these optional permissions on first start anyway
                doAskBluetoothConnectPermission = true
            }

            if (doAskCameraPermission) {
                doAskCameraPermission = !hasCameraPermission(context)
            }

            if (doAskRecordAudioPermission) {
                doAskRecordAudioPermission = !hasRecordAudioPermission(context)
            }

            if (doAskBluetoothConnectPermission) {
                doAskBluetoothConnectPermission = !hasBluetoothConnectPermission(context)
            }

            if (doAskOverlayPermission) {
                doAskOverlayPermission = !hasDrawOverlayPermission(context)
            }

            if (doAskPostNotificationPermission) {
                doAskPostNotificationPermission = !hasPostNotificationPermission(context)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun askDrawOverlayPermission() {
        updateProgressLabel()
        skipButton.visibility = View.GONE
        questionTextView.text = getString(R.string.ask_for_draw_overlay_permission)
        askButton.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri())
            requestDrawOverlaysPermissionLauncher.launch(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun askNotificationPermission() {
        updateProgressLabel()
        skipButton.visibility = View.GONE
        questionTextView.text = getString(R.string.ask_for_post_notification_permission)
        askButton.setOnClickListener {
            requestPostNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun askRecordAudioPermission() {
        updateProgressLabel()
        skipButton.visibility = View.GONE
        questionTextView.text = getString(R.string.ask_for_record_audio_permission)
        askButton.setOnClickListener {
            requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun askCameraPermission() {
        updateProgressLabel()
        skipButton.visibility = View.GONE
        questionTextView.text = getString(R.string.ask_for_camera_permission)
        askButton.setOnClickListener {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun askRecordBluetoothConnectPermission() {
        updateProgressLabel()
        skipButton.visibility = View.VISIBLE
        questionTextView.text = getString(R.string.ask_for_bluetooth_connect_permission)
        askButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestBluetoothConnectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                requestBluetoothConnectPermissionLauncher.launch(Manifest.permission.BLUETOOTH)
            }
        }
    }

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            continueQuestions()
        } else {
            // this is an optional permission
            continueQuestions()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            continueQuestions()
        } else {
            // this is an optional permission
            continueQuestions()
        }
    }

    private val requestBluetoothConnectPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            continueQuestions()
        } else {
            // this is an optional permission
            continueQuestions()
        }
    }

    private val requestPostNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            continueQuestions()
        } else {
            Toast.makeText(this, R.string.missing_required_permissions, Toast.LENGTH_LONG).show()
            continueQuestions()
        }
    }

    private var requestDrawOverlaysPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (hasDrawOverlayPermission(applicationContext)) {
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            continueQuestions()
        } else {
            Toast.makeText(this, R.string.missing_required_permissions, Toast.LENGTH_LONG).show()
            continueQuestions()
        }
    }

    private fun updateProgressLabel() {
        progressTextView.text = String.format(
            Locale.ROOT, "%d/%d", questionCounter, questionsToAsk)
    }

    private fun continueQuestions() {
        Log.d(this ,"continueQuestions")

        if (permissions.doAskOverlayPermission) {
            permissions.doAskOverlayPermission = false
            questionCounter += 1
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                askDrawOverlayPermission()
                return
            }
        }

        if (permissions.doAskPostNotificationPermission) {
            permissions.doAskPostNotificationPermission = false
            questionCounter += 1
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotificationPermission()
                return
            }
        }

        if (permissions.doAskCameraPermission) {
            permissions.doAskCameraPermission = false
            questionCounter += 1
            askCameraPermission()
            return
        }

        if (permissions.doAskRecordAudioPermission) {
            permissions.doAskRecordAudioPermission = false
            questionCounter += 1
            askRecordAudioPermission()
            return
        }

        if (permissions.doAskBluetoothConnectPermission) {
            permissions.doAskBluetoothConnectPermission = false
            questionCounter += 1
            askRecordBluetoothConnectPermission()
            return
        }

        // switch to MainActivity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        fun permissionsToAsk(context: Context, firstStart: Boolean): Boolean {
            return AskPermissions(context, firstStart).getQuestionCount() > 0
        }

        private fun hasDrawOverlayPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return android.provider.Settings.canDrawOverlays(context)
            } else {
                return true
            }
        }

        private fun hasPostNotificationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return Utils.hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                return true
            }
        }

        private fun hasRecordAudioPermission(context: Context): Boolean {
            return Utils.hasPermission(context, Manifest.permission.RECORD_AUDIO)
        }

        private fun hasCameraPermission(context: Context): Boolean {
            return Utils.hasPermission(context, Manifest.permission.CAMERA)
        }

        private fun hasBluetoothConnectPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return Utils.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                return Utils.hasPermission(context, Manifest.permission.BLUETOOTH)
            }
        }
    }
}
