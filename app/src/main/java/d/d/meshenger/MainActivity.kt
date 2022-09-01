package d.d.meshenger

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import d.d.meshenger.MainService.MainBinder

// the main view with tabs
class MainActivity : MeshengerActivity(), ServiceConnection {
    @JvmField
    internal var binder: MainBinder? = null
    private var mViewPager: ViewPager? = null
    private var contactListFragment: ContactListFragment? = null
    private var eventListFragment: EventListFragment? = null
    val PERM_REQUEST_CODE_DRAW_OVERLAYS = 1234
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
        log("onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initToolbar()
        permissionToDrawOverlays();
        MainService.start(this)
        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container)
        contactListFragment = ContactListFragment()
        eventListFragment = EventListFragment()
        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        tabLayout.setupWithViewPager(mViewPager)
        // ask for audio recording permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 2)
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(refreshEventListReceiver, IntentFilter("refresh_event_list"))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(refreshContactListReceiver, IntentFilter("refresh_contact_list"))
    }

    private fun permissionToDrawOverlays() {
        if (Build.VERSION.SDK_INT >= 23) {   //Android M Or Over
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(
                    "package:$packageName"))
                startActivityForResult(intent, PERM_REQUEST_CODE_DRAW_OVERLAYS)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERM_REQUEST_CODE_DRAW_OVERLAYS) {
            if (Build.VERSION.SDK_INT >= 23) {   //Android M Or Over
                if (!Settings.canDrawOverlays(this)) {
                    // ADD UI FOR USER TO KNOW THAT UI for SYSTEM_ALERT_WINDOW permission was not granted earlier...
                }
            }
        }
    }

    override fun onDestroy() {
        log("onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshEventListReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshContactListReceiver)
        super.onDestroy()
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        log("OnServiceConnected")
        binder = iBinder as MainBinder
        val addresses_s = ArrayList<String>()
        var addr = binder?.settings?.addresses


        if (addr!!.isEmpty()) {
            val wifiManager =
                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager != null) {
                val ipAddress: String =
                    Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
                if (ipAddress != null && !ipAddress.equals("0.0.0.0")) {
                    addresses_s.add(ipAddress)
                    binder!!.settings?.addresses = addresses_s
                    binder!!.saveDatabase()
                }
            }
        }
        // in case the language has changed
        val adapter = SectionsPageAdapter(supportFragmentManager)
        adapter.addFragment(contactListFragment, this.resources.getString(R.string.title_contacts))
        adapter.addFragment(eventListFragment, this.resources.getString(R.string.title_history))
        mViewPager!!.adapter = adapter
        contactListFragment!!.onServiceConnected()
        eventListFragment!!.onServiceConnected()
        // call it here because EventListFragment.onResume is triggered twice
        try {
            binder!!.pingContacts()
        } catch (e: Exception) {
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        log("OnServiceDisconnected")
        binder = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        log("onOptionsItemSelected")
        val id = item.itemId
        when (id) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }
            R.id.action_backup -> {
                startActivity(Intent(this, BackupActivity::class.java))
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }
            R.id.action_exit -> {
                MainService.stop(this)
                if (Build.VERSION.SDK_INT >= 21) {
                    finishAndRemoveTask()
                } else {
                    finish()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private val refreshEventListReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            eventListFragment!!.refreshEventList()
        }
    }
    private val refreshContactListReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            contactListFragment!!.refreshContactList()
        }
    }

    override fun onResume() {
        log("OnResume")
        super.onResume()
        bindService(Intent(this, MainService::class.java), this, BIND_AUTO_CREATE)
    }

    override fun onPause() {
        log("onPause")
        super.onPause()
        unbindService(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_mic, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        log("onCreateOptionsMenu")
        menuInflater.inflate(R.menu.menu_main_activity, menu)
        return true
    }

    class SectionsPageAdapter(fm: FragmentManager?) : FragmentPagerAdapter(
        fm!!
    ) {
        private val mFragmentList: MutableList<Fragment> = ArrayList()
        private val mFragmentTitleList: MutableList<String> = ArrayList()
        fun addFragment(fragment: Fragment?, title: String) {
            mFragmentList.add(fragment!!)
            mFragmentTitleList.add(title)
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return mFragmentTitleList[position]
        }

        override fun getItem(position: Int): Fragment {
            return mFragmentList[position]
        }

        override fun getCount(): Int {
            return mFragmentList.size
        }
    }

    private fun log(s: String) {
        Log.d(this, s)
    }
}