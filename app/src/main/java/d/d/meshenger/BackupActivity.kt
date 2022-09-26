package d.d.meshenger

import d.d.meshenger.Utils.writeExternalFile
import d.d.meshenger.Utils.readExternalFile
import android.widget.ImageButton
import android.widget.TextView
import android.os.Bundle
import android.content.Intent
import d.d.meshenger.MainService.MainBinder
import android.widget.Toast
import android.content.ComponentName
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import java.lang.Exception

class BackupActivity : MeshengerActivity(), ServiceConnection {
    private var builder: AlertDialog.Builder? = null
    private var binder: MainBinder? = null
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private lateinit var selectButton: ImageButton
    private lateinit var passwordEditText: TextView

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
        passwordEditText = findViewById(R.id.PasswordEditText)
        importButton.setOnClickListener(View.OnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            startActivityForResult(intent, READ_REQUEST_CODE)
        })
        exportButton.setOnClickListener(View.OnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_TITLE, "meshenger-backup.json")
            intent.type = "application/json"
            startActivityForResult(intent, WRITE_REQUEST_CODE)
        })
    }

    private fun exportDatabase(uri: Uri) {
        val password = passwordEditText.text.toString()
        try {
            val database = binder!!.getDatabase()
            if (database != null) {
                val data = Database.toData(database, password)
                if (data != null) {
                    writeExternalFile(this, uri, data!!)
                    Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            showErrorMessage(resources.getString(R.string.error), e.message)
        }
    }

    private fun importDatabase(uri: Uri?) {
        val password = passwordEditText.text.toString()
        try {
            val data = readExternalFile(this, uri!!)
            val db = Database.fromData(data, password)
            binder!!.getService().replaceDatabase(db)
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage(resources.getString(R.string.error), e.toString())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode != RESULT_OK) {
            return
        }

        val data = intent.data
        if (data == null) {
            return
        }

        when (requestCode) {
            READ_REQUEST_CODE -> importDatabase(data)
            WRITE_REQUEST_CODE -> exportDatabase(data)
        }
    }

    companion object {
        private const val READ_REQUEST_CODE = 0x01
        private const val WRITE_REQUEST_CODE = 0x02
    }
}