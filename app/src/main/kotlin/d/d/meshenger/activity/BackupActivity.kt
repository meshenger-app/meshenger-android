package d.d.meshenger.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import d.d.meshenger.*
import d.d.meshenger.base.MeshengerActivity
import d.d.meshenger.model.Database
import d.d.meshenger.service.MainService
import d.d.meshenger.utils.Utils


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
    private
    fun showErrorMessage(title: String?, message: String?) {
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
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
        importButton.setOnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            startActivityForResult(intent, READ_REQUEST_CODE)
        }
        exportButton.setOnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_TITLE, "meshenger-backup.json")
            intent.type = "application/json"
            startActivityForResult(intent, WRITE_REQUEST_CODE)
        }
    }

    private fun exportDatabase(uri: Uri?) {
        val password = passwordEditText.text.toString()
        try {
            val db = MainService.instance!!.database
            val data = db?.let { Database.toData(it, password) }
            Utils.writeExternalFile(this, uri, data)
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage(resources.getString(R.string.error), e.message)
        }
    }

    private fun importDatabase(uri: Uri?) {
        val password = passwordEditText.text.toString()
        try {
            val data = Utils.readExternalFile(this, uri)
            val db = Database.fromData(data, password)
            MainService.instance!!.replaceDatabase(db)
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorMessage(resources.getString(R.string.error), e.toString())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            return
        }
        if (data == null || data.data == null) {
            return
        }
        when (requestCode) {
            READ_REQUEST_CODE -> importDatabase(data.data)
            WRITE_REQUEST_CODE -> exportDatabase(data.data)
        }
    }
}



