package org.rivchain.cuplink

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.rivchain.cuplink.MainService.MainBinder
import org.rivchain.cuplink.util.AddressUtils
import org.rivchain.cuplink.util.Log
import org.rivchain.cuplink.util.PowerManager

// the main view with tabs
class MainActivity : BaseActivity(), ServiceConnection {

    private var service: MainService? = null
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
        bindService(Intent(this, MainService::class.java), this, 0)
        // need to be called before super.onCreate()
        applyNightMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initToolbar()

        viewPager = findViewById(R.id.container)
        viewPager.adapter = ViewPagerFragmentAdapter(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (PowerManager.needsFixing(this)) {
                PowerManager.callPowerManager(this)
                PowerManager.callAutostartManager(this)
                Log.d(this, "Power management fix enabled")
            } else {
                Log.d(this, "Power management fix disabled")
            }
        } else {
            Log.d(this, "Power management fix skipped")
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(this.baseContext)
        preferences.edit(commit = true) { putBoolean(PREF_KEY_ENABLED, true) }
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        if (tabLayout.selectedTabPosition == 0) {
            super.onBackPressedDispatcher.onBackPressed()
            finish()
        } else {
            tabLayout.getTabAt(0)?.select()
        }
    }
    override fun onDestroy() {
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

            val storedAddresses = service!!.getSettings().addresses
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

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        Log.d(this, "onServiceConnected()")
        val binder = iBinder as MainBinder
        service = binder.getService()

        val settings = service!!.getSettings()

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
            //tabLayout.visibility = View.VISIBLE
            tabLayout.visibility = View.GONE
        }

        val toolbarLabel = findViewById<TextView>(R.id.toolbar_label)
        if (settings.showUsernameAsLogo) {
            toolbarLabel.visibility = View.VISIBLE
            toolbarLabel.text = settings.username
        } else {
            // default
            toolbarLabel.visibility = View.GONE
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.contentDescription = when (position) {
                0 -> getString(R.string.title_contacts)
                else -> {
                    getString(R.string.title_calls)
                }
            }
            tab.icon = when (position) {
                0 -> resources.getDrawable(R.drawable.ic_contacts, theme)
                else -> {
                    resources.getDrawable(R.drawable.ic_call_accept, theme)
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
                MainService.stopPacketsStream(this)
                finish()
            }
        }
    }

    // request password for setting activity
    private fun showMenuPasswordDialog(itemId: Int, menuPassword: String) {
        val view: View = LayoutInflater.from(this).inflate(R.layout.dialog_enter_database_password, null)
        val b = AlertDialog.Builder(this, R.style.PPTCDialog)
        b.setView(view)
        b.setCancelable(false)
        val dialog = b.create()
        dialog.setCanceledOnTouchOutside(false)

        val passwordEditText = view.findViewById<TextInputEditText>(R.id.change_password_edit_textview)
        val exitButton = view.findViewById<Button>(R.id.change_password_cancel_button)
        val okButton = view.findViewById<Button>(R.id.change_password_ok_button)
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

        val settings = service!!.getSettings()
        if (settings.menuPassword.isEmpty()) {
            menuAction(item.itemId)
        } else {
            showMenuPasswordDialog(item.itemId, settings.menuPassword)
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

    class ViewPagerFragmentAdapter(private val fm: FragmentActivity) : FragmentStateAdapter(fm) {
        var ready = false

        override fun getItemCount(): Int {
            return if (ready) 2 else 0
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> {
                    val fragment = ContactListFragment()
                    fragment.setService((fm as MainActivity).service!!)
                    fragment
                }
                else -> {
                    val fragment = EventListFragment()
                    fragment.setService((fm as MainActivity).service!!)
                    fragment
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeeplinkIntent(intent)
    }


    private fun handleDeeplinkIntent(intent: Intent) {
        val data = intent.data
        if (data != null) {
            AddContactActivity.handlePotentialCupLinkContactUrl(this, data.toString())
        }
    }

    companion object {

        private var addressWarningShown = false

        fun clearTop(context: Context): Intent {
            val intent = Intent(context, MainActivity::class.java)

            intent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

            return intent
        }
    }
}
