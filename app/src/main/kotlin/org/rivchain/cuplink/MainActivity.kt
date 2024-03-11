package org.rivchain.cuplink

import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.rivchain.cuplink.MainService.MainBinder
import org.rivchain.cuplink.util.PowerManager

// the main view with tabs
class MainActivity : BaseActivity(), ServiceConnection {
    internal var binder: MainBinder? = null
    private lateinit var viewPager: ViewPager2

    private fun initToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.apply {
            setNavigationOnClickListener {
                finish()
            }
        }
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowTitleEnabled(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this, "onCreate()")

        // need to be called before super.onCreate()
        applyNightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initToolbar()
        permissionToDrawOverlays()

        instance = this

        viewPager = findViewById(R.id.container)
        viewPager.adapter = ViewPagerFragmentAdapter(this)

        bindService(Intent(this, MainService::class.java), this, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (PowerManager.needsFixing(this)) {
                PowerManager.callPowerManager(this)
                PowerManager.callAutostartManager(this)
                Toast.makeText(this, "Power management fix enabled", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Power management fix disabled", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Power management fix skipped", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        unbindService(this)
    }

    private fun isWifiConnected(): Boolean {
        val context = this as Context
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                //activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            val connManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI) ?: return false
            @Suppress("DEPRECATION")
            return mWifi.isConnected
        }
    }

    private fun showInvalidAddressSettingsWarning() {
        Handler(Looper.getMainLooper()).postDelayed({
            val localBinder = this@MainActivity.binder
            if (localBinder == null) {
                Log.w(this, "showInvalidAddressSettingsWarning() binder is null")
                return@postDelayed
            }

            val storedAddresses = localBinder.getSettings().addresses
            val storedIPAddresses = storedAddresses.filter { AddressUtils.isIPAddress(it) || AddressUtils.isMACAddress(it) }
            if (storedAddresses.isNotEmpty() && storedIPAddresses.isEmpty()) {
                // ignore, we only have domains configured
            } else if (storedAddresses.isEmpty()) {
                // no addresses configured at all
                Toast.makeText(this, R.string.warning_no_addresses_configured, Toast.LENGTH_LONG).show()
            } else {
                if (isWifiConnected()) {
                    val systemAddresses = AddressUtils.collectAddresses().map { it.address }
                    if (storedIPAddresses.intersect(systemAddresses.toSet()).isEmpty()) {
                        // none of the configured addresses are used in the system
                        // addresses might have changed!
                        Toast.makeText(this, R.string.warning_no_addresses_found, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, 700)
    }

    private fun permissionToDrawOverlays() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                requestDrawOverlaysPermissionLauncher.launch(intent)
            }
        }
    }

    private var requestDrawOverlaysPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        Log.d(this, "onServiceConnected()")
        binder = iBinder as MainBinder

        val settings = binder!!.getSettings()

        // data source for the views was not ready before
        (viewPager.adapter as ViewPagerFragmentAdapter).let {
            it.ready = true
            it.notifyDataSetChanged()
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        if (settings.disableCallHistory) {
            tabLayout.visibility = View.GONE
        } else {
            // default
            tabLayout.visibility = View.VISIBLE
        }

        val toolbarLogo = findViewById<ImageView>(R.id.toolbar_logo)
        val toolbarLabel = findViewById<TextView>(R.id.toolbar_label)
        if (settings.showUsernameAsLogo) {
            toolbarLogo.visibility = View.GONE
            toolbarLabel.visibility = View.VISIBLE
            toolbarLabel.text = settings.username
        } else {
            // default
            toolbarLogo.visibility = View.VISIBLE
            toolbarLabel.visibility = View.GONE
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.title_contacts)
                else -> {
                    val eventsMissed = binder!!.getEvents().eventsMissed
                    if (eventsMissed == 0) {
                        getString(R.string.title_calls)
                    } else {
                        String.format("%s (%d)", getString(R.string.title_calls), eventsMissed)
                    }
                }
            }
        }.attach()

        if (!addressWarningShown) {
            // only show once since app start
            showInvalidAddressSettingsWarning()
            addressWarningShown = true
        }

        MainService.refreshEvents(this)
        MainService.refreshContacts(this)
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }

    private fun menuAction(itemId: Int) {
        when (itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.action_backup -> {
                startActivity(Intent(this, BackupActivity::class.java))
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }
            R.id.action_exit -> {
                MainService.stop(this)
                finish()
            }
        }
    }

    // request password for setting activity
    private fun showMenuPasswordDialog(itemId: Int, menuPassword: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_enter_database_password)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val passwordEditText = dialog.findViewById<EditText>(R.id.change_password_edit_textview)
        val exitButton = dialog.findViewById<Button>(R.id.change_password_cancel_button)
        val okButton = dialog.findViewById<Button>(R.id.change_password_ok_button)
        okButton.setOnClickListener {
            val password = passwordEditText.text.toString()
            if (menuPassword == password) {
                // start menu action
                menuAction(itemId)
            } else {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
            }

            // close dialog
            dialog.dismiss()
        }

        exitButton.setOnClickListener {
            // close dialog
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(this, "onOptionsItemSelected()")

        val binder = binder
        if (binder != null) {
            val settings = binder.getSettings()
            if (settings.menuPassword.isEmpty()) {
                menuAction(item.itemId)
            } else {
                showMenuPasswordDialog(item.itemId, settings.menuPassword)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun updateEventTabTitle() {
        Log.d(this, "updateEventTabTitle()")
        // update event tab title
        (viewPager.adapter as ViewPagerFragmentAdapter?)?.notifyDataSetChanged()
    }

    override fun onResume() {
        Log.d(this, "onResume()")
        super.onResume()

        updateEventTabTitle()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(this, "onCreateOptionsMenu()")
        menuInflater.inflate(R.menu.menu_main_activity, menu)
        return true
    }

    class ViewPagerFragmentAdapter(fm: FragmentActivity) : FragmentStateAdapter(fm) {
        var ready = false

        override fun getItemCount(): Int {
            return if (ready) 2 else 0
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ContactListFragment()
                else -> EventListFragment()
            }
        }
    }

    companion object {
        private var addressWarningShown = false
        var instance: MainActivity? = null
    }
}
