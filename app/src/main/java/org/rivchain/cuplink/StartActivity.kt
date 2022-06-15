package org.rivchain.cuplink

import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import org.rivchain.cuplink.MainService.MainBinder

/*
* Show splash screen, name setup dialog, database password dialog and
* start background service before starting the MainActivity.
*/
class StartActivity : CupLinkActivity() {

    private lateinit var mainServiceIntent: Intent
    private lateinit var serviceConnection: ServiceConnection
    private lateinit var binder: MainBinder
    private var startState = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val type = Typeface.createFromAsset(assets, "rounds_black.otf")
        val splashText = findViewById<View>(R.id.splashText)
        ( splashText as TextView).typeface = type
        mainServiceIntent = Intent(this, MainService::class.java)
        serviceConnection = object : ServiceConnection {

            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                this@StartActivity.binder = binder as MainBinder
                log("onServiceConnected")
                if (startState == 0) {
                    if (binder.isFirstStart()) {
                        // show delayed splash page
                        Handler().postDelayed({ continueInit() }, 1000)
                    } else {
                        // show contact list as fast as possible
                        continueInit()
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                binder.shutdown()
            }
        }
        // start MainService and call back via onServiceConnected()
        startService(mainServiceIntent)
        hideActionBar(splashText.rootView)
    }

    private fun continueInit() {
        startState += 1
        when (startState) {
            1 -> {
                log("init 1: load database")
                // open without password
                binder.loadDatabase()
                continueInit()
            }
            2 -> {
                log("init 2: check database")
                continueInit()
            }
            3 -> {
                log("init 3: check username")
                if (binder.settings.username.isEmpty()) {
                    // set username
                    showMissingUsernameDialog()
                } else {
                    continueInit()
                }
            }
            4 -> {
                log("init 4: check key pair")
                continueInit()
                Thread.sleep(500)
            }
            5 -> {
                log("init 5: check addresses")
                //if (binder.isFirstStart()) {
                    //showMissingAddressDialog()
                    //do address setup after UI loading
                //} else {
                //    continueInit()
                //}
                continueInit()
            }
            6 -> {
                log("init 6: start contact list")
                // set night mode
                val nightMode = binder.settings.nightMode
                AppCompatDelegate.setDefaultNightMode(
                    if (nightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )

                // all done - show contact list
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bindService(mainServiceIntent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        unbindService(serviceConnection)
    }

    private fun showMissingAddressDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Setup")
        builder.setMessage("There is something to configure. Just tap skip button.")
        builder.setPositiveButton(R.string.ok) { dialog: DialogInterface, id: Int ->
            showMissingAddressDialog()
            dialog.cancel()
        }
        builder.setNegativeButton(R.string.skip) { dialog: DialogInterface, id: Int ->
            dialog.cancel()
            // continue with out address configuration
            continueInit()
        }
        builder.show()
    }

    // initial dialog to set the username
    private fun showMissingUsernameDialog() {
        val tw = TextView(this)
        tw.setText(R.string.name_prompt)
        //tw.setTextColor(Color.BLACK);
        tw.textSize = 20f
        tw.gravity = Gravity.CENTER_HORIZONTAL
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(tw)
        val et = EditText(this)
        et.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        et.isSingleLine = true
        layout.addView(et)
        layout.setPadding(40, 80, 40, 40)
        //layout.setGravity(Gravity.CENTER_HORIZONTAL);
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.hello)
        builder.setView(layout)
        builder.setNegativeButton(R.string.cancel) { dialogInterface, i ->
            binder.shutdown()
            finish()
        }
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        builder.setPositiveButton(R.string.next) { dialogInterface, i -> }
        val dialog = builder.create()
        dialog.setOnShowListener { newDialog: DialogInterface ->
            val okButton = (newDialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                    // nothing to do
                }

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    // nothing to do
                }

                override fun afterTextChanged(editable: Editable) {
                    okButton.isClickable = editable.isNotEmpty()
                    okButton.alpha = if (editable.isNotEmpty()) 1.0f else 0.5f
                }
            })
            okButton.isClickable = false
            okButton.alpha = 0.5f
            if (et.requestFocus()) {
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
                //imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        }
        dialog.show()

        // override handler (to be able to dismiss the dialog manually)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { v: View? ->
            imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY)
            val username = et.text.toString()
            if (Utils.isValidName(username)) {
                binder.settings.username = username
                try {
                    binder.saveDatabase()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // close dialog
                dialog.dismiss()
                //dialog.cancel(); // needed?
                continueInit()
            } else {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun log(s: String) {
        Log.d(this, s)
    }
}