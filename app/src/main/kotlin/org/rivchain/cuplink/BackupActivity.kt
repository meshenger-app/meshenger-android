package org.rivchain.cuplink

import android.app.Activity
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import org.rivchain.cuplink.MainService.MainBinder
import org.rivchain.cuplink.util.Utils.readExternalFile
import org.rivchain.cuplink.util.Utils.writeExternalFile

class BackupActivity : BaseActivity(), ServiceConnection {
    private var dialog: AlertDialog? = null
    private var service: MainService? = null
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private lateinit var passwordEditText: TextView

    private fun showMessage(title: String, message: String) {
        val builder = AlertDialog.Builder(this, R.style.FullPPTCDialog)
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

        if (service != null) {
            unbindService(this)
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        service = (iBinder as MainBinder).getService()
        initViews()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }

    private fun initViews() {
        if (service == null) {
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
            intent.putExtra(Intent.EXTRA_TITLE, "cuplink-backup.json")
            intent.type = "application/json"
            exportFileLauncher.launch(intent)
        }
    }

    private var importFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            val uri = intent.data ?: return@registerForActivityResult
            importDatabase(uri)
        }
    }

    private var exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            val uri: Uri = intent.data ?: return@registerForActivityResult
            exportDatabase(uri)
        }
    }

    private fun exportDatabase(uri: Uri) {
        val password = passwordEditText.text.toString()
        try {
            val database = service!!.getDatabase()
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
        val service = this.service ?: return
        val newDatabase: Database

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
        val peersCount = newDatabase.mesh.getPeers().length()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import_backup, null)
        val titleTextView = dialogView.findViewById<TextView>(R.id.checkboxDialogTitle)
        val contactsCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.contacts)
        val callsCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.calls)
        val peersCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.peers)
        val settingsCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.settings)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.CancelButton)
        val okButton = dialogView.findViewById<MaterialButton>(R.id.OkButton)

        titleTextView.text = getString(R.string.dialog_title_import_backup)
        contactsCheckbox.text = getString(R.string.title_contacts) + "($contactCount)"
        callsCheckbox.text = getString(R.string.title_calls) + "($eventCount)"
        peersCheckbox.text = getString(R.string.peers) + "($peersCount)"

        // Define the color state list for the checkbox text color
        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked), // checked state
                intArrayOf(-android.R.attr.state_checked) // unchecked state
            ),
            intArrayOf(
                ContextCompat.getColor(this, R.color.light_light_grey), // color for checked state
                ContextCompat.getColor(this, R.color.light_grey) // color for unchecked state
            )
        )

        // Apply the color state list to the checkboxes
        contactsCheckbox.setTextColor(colorStateList)
        callsCheckbox.setTextColor(colorStateList)
        peersCheckbox.setTextColor(colorStateList)
        settingsCheckbox.setTextColor(colorStateList)

        val builder = AlertDialog.Builder(this, R.style.PPTCDialog).setView(dialogView)
        val alertDialog = builder.create()

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
        }

        okButton.setOnClickListener {
            if (!contactsCheckbox.isChecked &&
                !callsCheckbox.isChecked &&
                !peersCheckbox.isChecked &&
                !settingsCheckbox.isChecked){
                Toast.makeText(this, "Please select Contacts, Calls, Peers or Settings", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (contactsCheckbox.isChecked) {
                // Handle contacts import
                service.importContacts(newDatabase)
            }
            if (callsCheckbox.isChecked) {
                // Handle calls import
                service.importCalls(newDatabase)
            }
            if (peersCheckbox.isChecked) {
                // Handle peers import
                service.importPeers(newDatabase)
            }
            if (settingsCheckbox.isChecked) {
                // Handle peers import
                service.importSettings(newDatabase)
            }
            service.saveDatabase()
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()

            // Restart service
            restartService()
            alertDialog.dismiss()
        }

        alertDialog.setCancelable(false) // prevent key shortcut to cancel dialog
        alertDialog.show()
    }
}