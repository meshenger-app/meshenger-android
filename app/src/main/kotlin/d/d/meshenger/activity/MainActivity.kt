package d.d.meshenger.activity

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.google.android.material.tabs.TabLayout
import d.d.meshenger.utils.Log.d
import d.d.meshenger.utils.Log.e
import d.d.meshenger.service.MainService
import d.d.meshenger.base.MeshengerActivity
import d.d.meshenger.R
import d.d.meshenger.utils.Utils.hasPermission
import d.d.meshenger.fragment.ContactListFragment
import d.d.meshenger.fragment.EventListFragment

class MainActivity: MeshengerActivity() {

    companion object {

        private const val TAG = "MainActivity"

        class SectionsPageAdapter(fm: FragmentManager?) :
            FragmentPagerAdapter(fm!!) {
            private val mFragmentList: MutableList<Fragment> = ArrayList()
            private val mFragmentTitleList: MutableList<String> = ArrayList()
            var missedCalls = 0
            fun addFragment(fragment: Fragment, title: String) {
                mFragmentList.add(fragment)
                mFragmentTitleList.add(title)
            }

            override fun getPageTitle(position: Int): CharSequence? {
                d(TAG, "getPageTitle")
                return if (mFragmentList[position] is EventListFragment) {
                    if (missedCalls > 0) {
                        mFragmentTitleList[position] + " (" + missedCalls + ")"
                    } else {
                        mFragmentTitleList[position]
                    }
                } else mFragmentTitleList[position]
            }

            override fun getItem(position: Int): Fragment {
                return mFragmentList[position]
            }

            override fun getCount(): Int {
                return mFragmentList.size
            }
        }

    }

    private lateinit var mViewPager: ViewPager
    private lateinit var contactListFragment: ContactListFragment
    private lateinit var eventListFragment: EventListFragment
    private lateinit var sectionsPageAdapter: SectionsPageAdapter
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container)
        contactListFragment = ContactListFragment()
        eventListFragment = EventListFragment()
        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        tabLayout.setupWithViewPager(mViewPager)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(eventsChangedReceiver, IntentFilter("events_changed"))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(contactsChangedReceiver, IntentFilter("contacts_changed"))

        // in case the language has changed
        sectionsPageAdapter = SectionsPageAdapter(supportFragmentManager)
        sectionsPageAdapter!!.addFragment(
            contactListFragment,
            this.resources.getString(R.string.title_contacts)
        )
        sectionsPageAdapter!!.addFragment(
            eventListFragment,
            this.resources.getString(R.string.title_history)
        )
        mViewPager.setAdapter(sectionsPageAdapter)
        mViewPager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                // ignore scrolling
            }

            override fun onPageSelected(position: Int) {
                d(
                    TAG,
                    "onPageSelected, position: $position"
                )
                currentPage = position
                if (position == 1) {
                    MainService.instance!!.getEvents()!!.setEventsViewedDate()
                    //updateMissedCallsCounter();
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                // ignore scrolling
            }
        })
        contactListFragment!!.refreshContactList()
        eventListFragment!!.refreshEventList()
    }


    override fun onResume() {
        d(TAG, "OnResume")
        super.onResume()
        updateMissedCallsCounter()
        checkPermissions() // TODO: remove
    }

    // TODO: move to CallActivity
    private fun checkPermissions() {
        d(TAG, "checkPermissions")
        val settings = MainService.instance!!.getSettings()
        if (settings!!.recordAudio) {
            if (!hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    1
                )
                return
            }
        }
        if (settings.recordVideo) {
            if (!hasPermission(this, Manifest.permission.CAMERA)) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 2)
                return
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val settings = MainService.instance!!.getSettings()
        when (requestCode) {
            1 -> if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone disabled by default", Toast.LENGTH_LONG).show()
                settings!!.recordAudio = false
            }
            2 -> if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera disabled by default", Toast.LENGTH_LONG).show()
                settings!!.recordVideo = false
            }
            else -> e(
                TAG,
                "Unknown permission requestCode: $requestCode"
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        d(TAG, "onCreateOptionsMenu")
        menuInflater.inflate(R.menu.menu_main_activity, menu)
        return true
    }

    private val eventsChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            d(TAG, "received events_changed")
            eventListFragment.refreshEventList()
            updateMissedCallsCounter()
        }
    }

    private val contactsChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            contactListFragment.refreshContactList()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        d(TAG, "onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(eventsChangedReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(contactsChangedReceiver)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        d(TAG, "onOptionsItemSelected")
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
//                MainService.stop(this)  //TODO: Erratic
                    finishAndRemoveTask()
                    finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // update missed calls in events fragment title
    private fun updateMissedCallsCounter() {
        val missedCalls = MainService.instance!!.getEvents()
            ?.getMissedCalls()?.size
        if (missedCalls != null) {
            this.sectionsPageAdapter.missedCalls = missedCalls
        }
        this.mViewPager.setAdapter(this.sectionsPageAdapter)
        // preserve page selection
        this.mViewPager.setCurrentItem(this.currentPage)
    }
}