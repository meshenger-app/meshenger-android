package d.d.meshenger

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.transition.Visibility
import d.d.meshenger.MainService.MainBinder
import java.util.Locale

class AskForMissingPermissionsActivity : BaseActivity(), ServiceConnection {
    private lateinit var progressTextView: TextView
    private lateinit var questionTextView: TextView
    private lateinit var askButton: Button
    private lateinit var skipButton: Button

    // required
    private var doAskOverlayPermission = true
    private var doAskPostNotificationPermission = true

    // optional
    private var doAskCameraPermission = true
    private var doAskRecordAudioPermission = true
    private var doAskBluetoothConnectPermission = true

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

        bindService(Intent(this, MainService::class.java), this, 0)
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        Log.d(this, "onServiceConnected()")
        val binder = iBinder as MainBinder

        val settings = binder.getSettings()

        questionsToAsk = countRequiredPermissions(applicationContext, settings) +
                countOptionalPermissions(applicationContext)

        doAskOverlayPermission = !settings.ignoreOverlayPermission

        // start questions
        continueQuestions()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }

    @SuppressLint("InlinedApi")
    private fun askDrawOverlayPermission() {
        updateProgressLabel()
        skipButton.visibility = View.GONE
        questionTextView.text = getString(R.string.ask_for_draw_overlay_permission)
        askButton.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            requestDrawOverlaysPermissionLauncher.launch(intent)
        }
    }

    @SuppressLint("InlinedApi")
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
        skipButton.visibility = View.VISIBLE
        questionTextView.text = getString(R.string.ask_for_record_audio_permission)
        askButton.setOnClickListener {
            requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun askCameraPermission() {
        updateProgressLabel()
        skipButton.visibility = View.VISIBLE
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
            continueQuestions()
        } else {
            // this is an optional permission
            continueQuestions()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            continueQuestions()
        } else {
            // this is an optional permission
            continueQuestions()
        }
    }

    private val requestBluetoothConnectPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            continueQuestions()
        } else {
            // this is an optional permission
            continueQuestions()
        }
    }

    private val requestPostNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            continueQuestions()
        } else {
            Toast.makeText(this, R.string.missing_required_permissions, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private var requestDrawOverlaysPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (hasDrawOverlayPermission(applicationContext)) {
            continueQuestions()
        } else {
            Toast.makeText(this, R.string.missing_required_permissions, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updateProgressLabel() {
        progressTextView.text = String.format(
            Locale.ROOT, "%d/%d", questionCounter, questionsToAsk)
    }

    private fun continueQuestions() {
        Log.d(this ,"continueQuestions")

        if (doAskOverlayPermission) {
            doAskOverlayPermission = false
            if (!hasDrawOverlayPermission(applicationContext)) {
                questionCounter += 1
                askDrawOverlayPermission()
                return
            }
        }

        if (doAskPostNotificationPermission) {
            doAskPostNotificationPermission = false
            if (!hasPostNotificationPermission(applicationContext)) {
                questionCounter += 1
                askNotificationPermission()
                return
            }
        }

        if (doAskCameraPermission) {
            doAskCameraPermission = false
            if (!hasCameraPermission(applicationContext)) {
                questionCounter += 1
                askCameraPermission()
                return
            }
        }

        if (doAskRecordAudioPermission) {
            doAskRecordAudioPermission = false
            if (!hasRecordAudioPermission(applicationContext)) {
                questionCounter += 1
                askRecordAudioPermission()
                return
            }
        }

        if (doAskBluetoothConnectPermission) {
            doAskBluetoothConnectPermission = false
            if (!hasBluetoothConnectPermission(applicationContext)) {
                questionCounter += 1
                askRecordBluetoothConnectPermission()
                return
            }
        }

        Toast.makeText(this, R.string.done, Toast.LENGTH_LONG).show()

        // switch to MainActivity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        fun countRequiredPermissions(context: Context, settings: Settings): Int {
            var missing = 0

            if (!settings.ignoreOverlayPermission) {
                if (!hasDrawOverlayPermission(context)) {
                    missing += 1
                }
            }

            if (!hasPostNotificationPermission(context)) {
                missing += 1
            }

            return missing
        }

        private fun countOptionalPermissions(context: Context): Int {
            var missing = 0

            if (!hasRecordAudioPermission(context)) {
                missing += 1
            }

            if (!hasCameraPermission(context)) {
                missing += 1
            }

            if (!hasBluetoothConnectPermission(context)) {
                missing += 1
            }

            return missing
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
