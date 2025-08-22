/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayoutMediator
import d.d.meshenger.MainService.MainBinder
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable

// the main view with tabs
class MainActivity : BaseActivity(), ServiceConnection {
    internal lateinit var binder: MainBinder
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

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initToolbar()

        instance = this

        viewPager = findViewById(R.id.container)

        // start MainService and call back via onServiceConnected()
        MainService.start(this)

        bindService(Intent(this, MainService::class.java), this, 0)
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
            val storedAddresses = Database.getSettings().addresses
            val storedIPAddresses = storedAddresses.filter { AddressUtils.isIPAddress(it) }
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

    private fun getColorDrawable(attr: Int): Drawable {
        val typedValue = TypedValue()
        val theme = this@MainActivity.getTheme()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data.toDrawable()
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        Log.d(this, "onServiceConnected()")
        this.binder = iBinder as MainBinder
        MainActivity.binder = this.binder

        val adapter = ViewPagerFragmentAdapter(this)

        this.viewPager.adapter = adapter

        val settings = Database.getSettings()

        // data source for the views was not ready before
        adapter.let {
            it.ready = true
            it.disableCallHistory = settings.disableCallHistory
            it.notifyDataSetChanged()
        }

        val tabLayout = findViewById<TabLayout>(R.id.TabLayout)
        if (settings.disableCallHistory) {
            tabLayout.visibility = View.GONE
        } else {
            // default
            tabLayout.visibility = View.VISIBLE
        }

        // workaround since TabLayout with app:tabBackground and xml with
        // selected/unselected themeable tab colors create an exception.
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            init {
                resetBackgroundColor()
            }

            private fun resetBackgroundColor() {
                Log.d(this, "resetBackgroundColor ${tabLayout.size}")
                for (i in 0..tabLayout.size) {
                    val tab = tabLayout.getTabAt(i)
                    if (tab != null) {
                        tab.view.background = getColorDrawable(R.attr.tabColor)
                    }
                }
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                resetBackgroundColor()
                tab.view.background = getColorDrawable(R.attr.tabSelectedColor)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.view.background = getColorDrawable(R.attr.tabColor)
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // nothing to do
            }
        })

        val toolbarLabel = findViewById<TextView>(R.id.toolbar_label)
        if (settings.showUsernameAsLogo) {
            toolbarLabel.visibility = View.VISIBLE
            toolbarLabel.text = settings.username
        } else {
            // default
            toolbarLabel.visibility = View.GONE
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.title_contacts)
                else -> {
                    val eventsMissed = Database.getEvents().eventsMissed
                    if (eventsMissed == 0) {
                        getString(R.string.title_calls)
                    } else {
                        String.format(Locale.getDefault(), "%s (%d)", getString(R.string.title_calls), eventsMissed)
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
            R.string.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.string.menu_backup -> {
                startActivity(Intent(this, BackupActivity::class.java))
            }
            R.string.menu_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }
            R.string.menu_shutdown -> {
                MainService.stop(this)
                finish()
                System.exit(0)
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

        val settings = Database.getSettings()
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

        val hideMenus = Database.getSettings().hideMenus
        val titles =  if (hideMenus) {
            mutableListOf(R.string.menu_settings)
        } else {
            mutableListOf(
                R.string.menu_settings, R.string.menu_backup,
                R.string.menu_about, R.string.menu_shutdown)
        }

        for (title in titles) {
            menu.add(0, title, 0, title)
        }

        return true
    }

    class ViewPagerFragmentAdapter(fm: FragmentActivity) : FragmentStateAdapter(fm) {
        var ready = false
        var disableCallHistory = false

        override fun getItemCount(): Int {
            return if (ready) {
                if (disableCallHistory) {
                    1
                } else {
                    2
                }
            } else {
                0
            }
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
        // to be used by the fragments
        var binder: MainBinder? = null
    }
}
