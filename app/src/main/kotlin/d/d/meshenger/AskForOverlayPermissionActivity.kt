package d.d.meshenger

import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts

class AskForOverlayPermissionActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this, "onCreate()")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_ask_for_overlay_permission)

        findViewById<Button>(R.id.SkipButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.AskButton).setOnClickListener {
            // ask for permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    requestDrawOverlaysPermissionLauncher.launch(intent)
            } else {
                finish()
            }
        }
    }

    private var requestDrawOverlaysPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }
}
