package d.d.meshenger;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class AddressActivity extends MeshengerActivity implements ServiceConnection {
    private MainService.MainBinder binder;
    Spinner storedAddressSpinner;
    EditText addressEditText;
    Button addButton;
    Button removeButton;
    Spinner systemAddressSpinner;
    Button takeButton;
    Button saveButton;
    Button abortButton;

    ArrayList<AddressEntry> systemAddressList;
    ArrayList<AddressEntry> storedAddressList;
    AddressListAdapter storedAddressListAdapter;
    AddressListAdapter systemAddressListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_address);
        setTitle(R.string.address_management);

        storedAddressSpinner = findViewById(R.id.StoredAddressSpinner);
        addressEditText = findViewById(R.id.AddressEditText);
        addButton = findViewById(R.id.AddButton);
        removeButton = findViewById(R.id.RemoveButton);
        systemAddressSpinner = findViewById(R.id.SystemAddressSpinner);
        takeButton = findViewById(R.id.TakeButton);
        saveButton = findViewById(R.id.SaveButton);
        abortButton = findViewById(R.id.AbortButton);

        systemAddressList = Utils.collectAddresses();
        storedAddressList = new ArrayList<>();
        storedAddressListAdapter = new AddressListAdapter(this, Color.parseColor("#39b300")); //dark green
        systemAddressListAdapter = new AddressListAdapter(this, Color.parseColor("#b3b7b2")); //light grey

        storedAddressSpinner.setAdapter(storedAddressListAdapter);
        storedAddressSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                log("storedAddressSpinner.onItemSelected");
                // set for editing
                if (!storedAddressListAdapter.isEmpty()) {
                    AddressEntry ae = storedAddressList.get(pos);
                    log("clicked: " + ae.address);
                    addressEditText.setText(ae.address);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // nothing to do
            }
        });

        addressEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String address = addressEditText.getText().toString();
                boolean exists = (AddressEntry.findAddressEntry(storedAddressList, address) != null);
                if (exists) {
                    removeButton.setEnabled(true);
                    addButton.setEnabled(false);
                } else {
                    removeButton.setEnabled(false);
                    boolean valid = (Utils.isMAC(address) || Utils.isDomain(address) || Utils.isIP(address));
                    if (valid) {
                        addButton.setEnabled(true);
                    } else {
                        addButton.setEnabled(false);
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // nothing to do
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // nothing to do
            }
        });

        addButton.setOnClickListener((View v) -> {
            String address = addressEditText.getText().toString();
            if (address.isEmpty()) {
                return;
            }

            AddressEntry ae = parseAddress(address);
            if (ae.multicast) {
                Toast.makeText(this, "Multicast addresses are not supported.", Toast.LENGTH_SHORT).show();
                return;
            }

            storedAddressList.add(ae);
            updateSpinners();

            // select the added element
            int idx = AddressEntry.listIndexOf(storedAddressList, ae);
            storedAddressSpinner.setSelection(idx);
        });

        removeButton.setOnClickListener((View v) -> {
            String address = addressEditText.getText().toString();
            if (address.isEmpty()) {
                return;
            }

            int idx = AddressEntry.listIndexOf(storedAddressList, new AddressEntry(address, "", false));
            if (idx > -1) {
                storedAddressList.remove(idx);
                updateSpinners();
            }
        });

        takeButton.setOnClickListener((View v) -> {
            //AddressEntry ae = (Utils.Address) systemAddressSpinner.getSelectedItem();
            int pos = systemAddressSpinner.getSelectedItemPosition();
            if (pos > -1) {
                addressEditText.setText(systemAddressList.get(pos).address);
            }
        });

        saveButton.setOnClickListener((View v) -> {
            ArrayList<String> addresses = new ArrayList<>();
            for (AddressEntry ae : this.storedAddressList) {
                addresses.add(ae.address);
            }
            this.binder.getSettings().setAddresses(addresses);
            this.binder.saveDatabase();
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        });

        abortButton.setOnClickListener((View v) -> {
            finish();
        });

        bindService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    private void bindService() {
        // ask MainService to get us the binder object
        Intent serviceIntent = new Intent(this, MainService.class);
        bindService(serviceIntent, this, Service.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.binder = (MainService.MainBinder) iBinder;

        // get from settings
        for (String address : this.binder.getSettings().getAddresses()) {
            this.storedAddressList.add(parseAddress(address));
        }

        updateSpinners();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.binder = null;
    }

    public static class AddressListAdapter extends BaseAdapter {
        private final Activity context;
        private final int markColor;
        private List<AddressEntry> addressEntries;
        private List<AddressEntry> addressEntriesMarked;

        public AddressListAdapter(Activity context, int markColor) {
            this.context = context;
            this.markColor = markColor;
            this.addressEntries = new ArrayList<>();
            this.addressEntriesMarked = new ArrayList<>();
        }

        public boolean isEmpty() {
            return this.addressEntries.isEmpty();
        }

        public void update(List<AddressEntry> addressEntries, ArrayList<AddressEntry> addressEntriesMarked) {
            this.addressEntries = addressEntries;
            this.addressEntriesMarked = addressEntriesMarked;
        }

        @Override
        public int getCount() {
            if (isEmpty()) {
                return 1;
            }
            return addressEntries.size();
        }

        @Override
        public Object getItem(int position) {
            if (isEmpty()) {
                return null;
            }
            return addressEntries.get(position);
        }
   
        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                LayoutInflater inflater = context.getLayoutInflater();
                view = inflater.inflate(R.layout.activity_address_item, parent, false);
            }

            TextView label = view.findViewById(R.id.label);

            if (isEmpty()) {
                label.setText("Empty");
                label.setTextColor(Color.BLACK);
            } else {
                AddressEntry ae = this.addressEntries.get(position);

                ArrayList<String> info = new ArrayList<>();
                if (ae.device.length() > 0) {
                    info.add(ae.device);
                }

                if (ae.multicast) {
                    info.add("multicast");
                }

                label.setText(ae.address + (info.isEmpty() ? "" : (" (" + Utils.join(info) + ")")));

                if (AddressEntry.listIndexOf(addressEntriesMarked, ae) < 0) {
                    label.setTextColor(Color.BLACK);
                } else {
                    label.setTextColor(this.markColor);
                }
            }

            return view;
        }
    }

    private void updateSpinners() {
        log("update spinners");
        Collections.sort(storedAddressList);
        Collections.sort(systemAddressList);

        storedAddressListAdapter.update(storedAddressList, systemAddressList);
        storedAddressListAdapter.notifyDataSetChanged();

        systemAddressListAdapter.update(systemAddressList, storedAddressList);
        systemAddressListAdapter.notifyDataSetChanged();

        systemAddressSpinner.setAdapter(storedAddressListAdapter);
        systemAddressSpinner.setAdapter(systemAddressListAdapter);
    }

    AddressEntry parseAddress(String address) {
        AddressEntry ae = AddressEntry.findAddressEntry(systemAddressList, address);
        if (ae != null) {
            return new AddressEntry(address, ae.device, ae.multicast);
        } else if (Utils.isMAC(address)) {
            boolean mc = !Utils.isUnicastMAC(Utils.macAddressToBytes(address));
            return new AddressEntry(address, "", mc);
        } else if (Utils.isIP(address)) {
            //TODO: fix
            boolean mc = false;
            try {
                mc = Utils.parseInetSocketAddress(address, 0).getAddress().isMulticastAddress();
            } catch (Exception e) {
                // ignore
            }
            return new AddressEntry(address, "", mc);
        } else {
            return new AddressEntry(address, "", false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void log(String s) {
        Log.d(AddressActivity.class.getSimpleName(), s);
    }
}
