package d.d.meshenger;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


// the main view with tabs
public class MainActivity extends MeshengerActivity implements ServiceConnection {
    public MainService.MainBinder binder;
    private ViewPager mViewPager;
    private ContactListFragment contactListFragment;
    private EventListFragment eventListFragment;
    private SectionsPageAdapter sectionsPageAdapter;
    private int currentPage = 0;
    private Date eventListAccessed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log("onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);

        contactListFragment = new ContactListFragment();
        eventListFragment = new EventListFragment();

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        // ask for audio recording permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 2);
        }

        this.eventListAccessed = new Date();

        LocalBroadcastManager.getInstance(this).registerReceiver(refreshEventListReceiver, new IntentFilter("refresh_event_list"));
        LocalBroadcastManager.getInstance(this).registerReceiver(refreshContactListReceiver, new IntentFilter("refresh_contact_list"));

        bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        log("onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshEventListReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshContactListReceiver);

        unbindService(this);
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        log("OnServiceConnected");
        this.binder = (MainService.MainBinder) iBinder;

        // in case the language has changed
        this.sectionsPageAdapter = new SectionsPageAdapter(getSupportFragmentManager());
        this.sectionsPageAdapter.addFragment(contactListFragment, this.getResources().getString(R.string.title_contacts));
        this.sectionsPageAdapter.addFragment(eventListFragment, this.getResources().getString(R.string.title_history));
        this.mViewPager.setAdapter(this.sectionsPageAdapter);

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
               // ignore scrolling
            }

            @Override
            public void onPageSelected(int position) {
                log( "onPageSelected, position: " + position);
                MainActivity.this.currentPage = position;
                if (position == 1) {
                    MainActivity.this.eventListAccessed = new Date();
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
               // ignore scrolling
            }
        });

        contactListFragment.refreshContactList();
        eventListFragment.refreshEventList();

        this.binder.pingContacts();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        log("OnServiceDisconnected");
        this.binder = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        log("onOptionsItemSelected");
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                break;
            }
            case R.id.action_backup: {
                startActivity(new Intent(this, BackupActivity.class));
                break;
            }
            case R.id.action_about: {
                startActivity(new Intent(this, AboutActivity.class));
                break;
            }
            case R.id.action_exit: {
                MainService.stop(this);
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    finishAndRemoveTask();
                } else {
                    finish();
                }
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateMissedCallsCounter() {
        // update missed called notification
        if (MainActivity.this.binder == null) {
            return;
        }

        int missedCalls = 0;
        Date since = this.eventListAccessed;
        for (CallEvent event : this.binder.getEventsCopy()) {
            if (event.date.compareTo(since) >= 0 && event.isMissedCall()) {
                missedCalls += 1;
            }
        }

        this.sectionsPageAdapter.missedCalls = missedCalls;
        this.mViewPager.setAdapter(this.sectionsPageAdapter);
        // preserve page selection
        this.mViewPager.setCurrentItem(this.currentPage);
    }

    private BroadcastReceiver refreshEventListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            eventListFragment.refreshEventList();

            updateMissedCallsCounter();
        }
    };

    private BroadcastReceiver refreshContactListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            contactListFragment.refreshContactList();
        }
    };

    @Override
    protected void onResume() {
        log("OnResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        log("onPause");
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_mic, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        log("onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);
        return true;
    }

    public static class SectionsPageAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();
        public int missedCalls = 0;

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        public SectionsPageAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Log.d(this, "getPageTitle");
            if (mFragmentList.get(position) instanceof EventListFragment) {
                if (this.missedCalls > 0) {
                    return mFragmentTitleList.get(position) + " (" + missedCalls + ")";
                } else {
                    return mFragmentTitleList.get(position);
                }
            }
            return mFragmentTitleList.get(position);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
