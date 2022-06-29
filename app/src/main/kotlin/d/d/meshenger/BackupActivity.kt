package d.d.meshenger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import d.d.meshenger.Utils.readExternalFile
import d.d.meshenger.Utils.writeExternalFile


class BackupActivity: MeshengerActivity() {

    companion object {
        private const val TAG = "BackupActivity"
        private const val READ_REQUEST_CODE = 0x01
        private const val WRITE_REQUEST_CODE = 0x02
    }

    private lateinit var builder: AlertDialog.Builder
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private lateinit var selectButton: ImageButton
    private lateinit var passwordEditText: TextView
    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if(result.resultCode != RESULT_OK) return@registerForActivityResult

        if(intent == null || intent.data == null) return@registerForActivityResult

        when(result.resultCode) {
            READ_REQUEST_CODE -> importDatabase(intent.data!!)
            WRITE_REQUEST_CODE -> exportDatabase(intent.data!!)
        }
    }


    private fun showErrorMessage(title: String, message: String?) {
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(R.string.ok, null)
        builder.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)
        initViews()
    }

    private fun initViews() {
        builder = AlertDialog.Builder(this)
        importButton = findViewById(R.id.ImportButton)
        exportButton = findViewById(R.id.ExportButton)
        selectButton = findViewById(R.id.SelectButton)
        passwordEditText = findViewById(R.id.PasswordEditText)
        importButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            resultLauncher.launch(intent)
//            startActivityForResult(intent, READ_REQUEST_CODE) //TODO(IODevBlue): Use contracts
        }
        exportButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_TITLE, "meshenger-backup.json")
            intent.type = "application/json"
            resultLauncher.launch(intent)
//            startActivityForResult(intent, WRITE_REQUEST_CODE) //TODO(IODevBlue): Use contracts
        }
    }

    private fun exportDatabase(uri: Uri) {
        val password = passwordEditText.text.toString()
        try {
            val db = MainService.instance!!.database
            val data = Database.toData(db!!, password)
            writeExternalFile(this, uri, data)
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage(resources.getString(R.string.error), e.message)
        }
    }

    private fun importDatabase(uri: Uri) {
        val password = passwordEditText.text.toString()
        try {
            val data = readExternalFile(this, uri)
            val db = Database.fromData(data, password)
            MainService.instance!!.replaceDatabase(db)
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage(resources.getString(R.string.error), e.toString())
        }
    }
}