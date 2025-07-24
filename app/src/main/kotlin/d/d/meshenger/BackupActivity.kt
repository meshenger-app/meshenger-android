/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.app.Activity
import d.d.meshenger.Utils.writeExternalFile
import d.d.meshenger.Utils.readExternalFile
import android.widget.TextView
import android.os.Bundle
import android.content.Intent
import d.d.meshenger.MainService.MainBinder
import android.widget.Toast
import android.content.ComponentName
import android.content.DialogInterface
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import java.lang.Exception

class BackupActivity : BaseActivity(), ServiceConnection {
    private var dialog: AlertDialog? = null
    private var binder: MainBinder? = null
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private lateinit var passwordEditText: TextView

    private fun showMessage(title: String, message: String) {
        val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        dialog = builder.show()
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
        bindService(Intent(this, MainService::class.java), this, 0)
        initViews()
    }

    override fun onDestroy() {
        dialog?.dismiss()

        super.onDestroy()

        if (binder != null) {
            unbindService(this)
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder
        initViews()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }

    private fun initViews() {
        if (binder == null) {
            return
        }

        importButton = findViewById(R.id.ImportButton)
        exportButton = findViewById(R.id.ExportButton)
        passwordEditText = findViewById(R.id.PasswordEditText)
        importButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/json"
            importFileLauncher.launch(intent)
        }

        exportButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_TITLE, "meshenger-backup.json")
            intent.type = "application/json"
            exportFileLauncher.launch(intent)
        }
    }

    private var importFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            importDatabase(uri)
        }
    }

    private var exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            exportDatabase(uri)
        }
    }

    private fun exportDatabase(uri: Uri) {
        val password = passwordEditText.text.toString()
        try {
            val database = binder!!.getDatabase()
            val dbData = Database.toData(database, password)

            if (dbData != null) {
                writeExternalFile(this, uri, dbData)
                Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.failed_to_export_database, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            showMessage(getString(R.string.error), e.message ?: "unknown")
        }
    }

    private fun importDatabase(uri: Uri) {
        val binder = this.binder ?: return
        val newDatabase : Database

        try {
            val password = passwordEditText.text.toString()
            val db = readExternalFile(this, uri)
            newDatabase = Database.fromData(db, password)
        } catch (e: Database.WrongPasswordException) {
            Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
            return
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            return
        }

        val contactCount = newDatabase.contacts.contactList.size
        val eventCount = newDatabase.events.eventList.size
        val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        builder.setTitle(R.string.dialog_title_import_backup)
        builder.setMessage(String.format(getString(R.string.import_dialog), contactCount, eventCount))
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(R.string.button_yes) { dialog: DialogInterface, _: Int ->
            binder.getService().mergeDatabase(newDatabase)
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
        }

        builder.setNegativeButton(R.string.button_no) { dialog: DialogInterface, _: Int ->
            dialog.cancel()
        }

        // create dialog box
        val alert = builder.create()
        alert.show()
    }
}