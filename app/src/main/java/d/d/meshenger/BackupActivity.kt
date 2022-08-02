package d.d.meshenger

import d.d.meshenger.MeshengerActivity
import android.content.ServiceConnection
import com.github.isabsent.filepicker.SimpleFilePickerDialog.InteractionListenerString
import android.widget.ImageButton
import android.widget.TextView
import d.d.meshenger.MainService.MainBinder
import android.os.Bundle
import d.d.meshenger.R
import android.content.Intent
import d.d.meshenger.MainService
import android.content.ComponentName
import android.os.Environment
import android.os.IBinder
import android.view.View
import android.widget.Button
import d.d.meshenger.BackupActivity
import com.github.isabsent.filepicker.SimpleFilePickerDialog
import d.d.meshenger.Database
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import java.io.File
import java.lang.Exception

class BackupActivity : MeshengerActivity(), ServiceConnection, InteractionListenerString {
    private var builder: AlertDialog.Builder? = null
    private var exportButton: Button? = null
    private var importButton: Button? = null
    private var selectButton: ImageButton? = null
    private var pathEditText: TextView? = null
    private var passwordEditText: TextView? = null
    private var binder: MainBinder? = null
    private fun showErrorMessage(title: String, message: String?) {
        builder!!.setTitle(title)
        builder!!.setMessage(message)
        builder!!.setPositiveButton(android.R.string.ok, null)
        builder!!.show()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)
        val toolbar = findViewById<Toolbar>(R.id.backup_toolbar)
        toolbar.apply {
            setNavigationOnClickListener {
                finish()
            }
        }
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        bindService()
        initViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (binder != null) {
            unbindService(this)
        }
    }

    private fun bindService() {
        // ask MainService to get us the binder object
        val serviceIntent = Intent(this, MainService::class.java)
        bindService(serviceIntent, this, BIND_AUTO_CREATE)
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder
        initViews()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }

    private fun initViews() {
        if (binder == null) {
            return
        }
        builder = AlertDialog.Builder(this)
        importButton = findViewById(R.id.ImportButton)
        exportButton = findViewById(R.id.ExportButton)
        selectButton = findViewById(R.id.SelectButton)
        pathEditText = findViewById(R.id.PathEditText)
        passwordEditText = findViewById(R.id.PasswordEditText)
        importButton?.setOnClickListener(View.OnClickListener { v: View? ->
            if (Utils.hasReadPermission(this@BackupActivity)) {
                importDatabase()
            } else {
                Utils.requestReadPermission(this@BackupActivity, REQUEST_PERMISSION)
            }
        })
        exportButton?.setOnClickListener(View.OnClickListener { v: View? ->
            if (Utils.hasReadPermission(this@BackupActivity) && Utils.hasWritePermission(this@BackupActivity)) {
                exportDatabase()
            } else {
                Utils.requestWritePermission(this@BackupActivity, REQUEST_PERMISSION)
                Utils.requestReadPermission(this@BackupActivity, REQUEST_PERMISSION)
            }
        })
        selectButton?.setOnClickListener(View.OnClickListener { v: View? ->
            if (Utils.hasReadPermission(this@BackupActivity)) {
                val rootPath = Environment.getExternalStorageDirectory().absolutePath
                showListItemDialog(
                    resources.getString(R.string.button_select),
                    rootPath,
                    SimpleFilePickerDialog.CompositeMode.FILE_OR_FOLDER_SINGLE_CHOICE,
                    SELECT_PATH_REQUEST
                )
            } else {
                Utils.requestReadPermission(this@BackupActivity, REQUEST_PERMISSION)
            }
        })
    }

    private fun exportDatabase() {
        val password = passwordEditText!!.text.toString()
        val path = pathEditText!!.text.toString()
        if (path == null || path.isEmpty()) {
            showErrorMessage(
                resources.getString(R.string.error),
                resources.getString(R.string.no_path_selected)
            )
            return
        }
        if (File(path).isDirectory || path.endsWith("/")) {
            showErrorMessage(
                resources.getString(R.string.error),
                resources.getString(R.string.no_file_name)
            )
            return
        }
        if (File(path).exists()) {
            showErrorMessage(
                resources.getString(R.string.error),
                resources.getString(R.string.file_already_exists)
            )
            return
        }
        try {
            val db = binder!!.getService().database
            db?.let { Database.store(path, it, password) }
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage(resources.getString(R.string.error), e.message)
        }
    }

    private fun importDatabase() {
        val password = passwordEditText!!.text.toString()
        val path = pathEditText!!.text.toString()
        if (path == null || path.isEmpty()) {
            showErrorMessage(
                resources.getString(R.string.error),
                resources.getString(R.string.no_path_selected)
            )
            return
        }
        if (File(path).isDirectory || path.endsWith("/")) {
            showErrorMessage(
                resources.getString(R.string.error),
                resources.getString(R.string.no_file_name)
            )
            return
        }
        if (!File(path).exists()) {
            showErrorMessage(
                resources.getString(R.string.error),
                resources.getString(R.string.file_does_not_exist)
            )
            return
        }
        try {
            val db = Database.load(path, password)
            binder!!.replaceDatabase(db)
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage(resources.getString(R.string.error), e.toString())
        }
    }

    // path picker
    override fun showListItemDialog(
        title: String,
        folderPath: String,
        mode: SimpleFilePickerDialog.CompositeMode,
        dialogTag: String
    ) {
        SimpleFilePickerDialog.build(folderPath, mode)
            .title(title)
            .show(this, dialogTag)
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        when (dialogTag) {
            SELECT_PATH_REQUEST -> if (extras.containsKey(SimpleFilePickerDialog.SELECTED_SINGLE_PATH)) {
                var path = extras.getString(SimpleFilePickerDialog.SELECTED_SINGLE_PATH)
                if (File(path).isDirectory) {
                    // append slash
                    if (!path!!.endsWith("/")) {
                        path += "/"
                    }
                    path += "meshenger_backup.json"
                }
                pathEditText!!.text = path
            }
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSION -> if (Utils.allGranted(grantResults)) {
                // permissions granted
                Toast.makeText(
                    applicationContext,
                    "Permissions granted - please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                showErrorMessage("Permissions Required", "Action cannot be performed.")
            }
        }
    }

    private fun log(s: String) {
        Log.d(this, s)
    }

    companion object {
        private const val SELECT_PATH_REQUEST = "SELECT_PATH_REQUEST"
        private const val REQUEST_PERMISSION = 0x01
    }
}