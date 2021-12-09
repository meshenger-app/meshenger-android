package org.rivchain.cuplink;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class AddressActivity extends CupLinkActivity implements ServiceConnection {
    Spinner storedAddressSpinner;
    Spinner systemAddressSpinner;
    Button pickStoredAddressButton;
    Button pickSystemAddressButton;
    EditText addressEditText;
    Button addButton;
    Button removeButton;
    Button saveButton;
    Button abortButton;
    List<AddressEntry> systemAddressList;
    List<AddressEntry> storedAddressList;
    AddressListAdapter storedAddressListAdapter;
    AddressListAdapter systemAddressListAdapter;
    private MainService.MainBinder binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_address);
        setTitle(R.string.address_management);

        storedAddressSpinner = findViewById(R.id.StoredAddressSpinner);
        systemAddressSpinner = findViewById(R.id.SystemAddressSpinner);
        pickStoredAddressButton = findViewById(R.id.PickStoredAddressButton);
        pickSystemAddressButton = findViewById(R.id.PickSystemAddressButton);
        addressEditText = findViewById(R.id.AddressEditText);
        addButton = findViewById(R.id.AddButton);
        removeButton = findViewById(R.id.RemoveButton);
        saveButton = findViewById(R.id.SaveButton);
        abortButton = findViewById(R.id.AbortButton);

        systemAddressList = Utils.collectAddresses();
        storedAddressList = new ArrayList<>();
        storedAddressListAdapter = new AddressListAdapter(this, Color.parseColor("#39b300")); //dark green
        systemAddressListAdapter = new AddressListAdapter(this, Color.parseColor("#b3b7b2")); //light grey

        storedAddressSpinner.setAdapter(storedAddressListAdapter);
        systemAddressSpinner.setAdapter(systemAddressListAdapter);

        addressEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateAddressEditTextButtons();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // nothing to do
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAddressEditTextButtons();
            }
        });

        addButton.setOnClickListener((View v) -> {
            String address = addressEditText.getText().toString();
            if (address.isEmpty()) {
                return;
            }

            AddressEntry entry = parseAddress(address);

            if (entry.multicast) {
                Toast.makeText(this, "Multicast addresses are not supported.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (Utils.isIP(address) && !systemAddressList.contains(entry)) {
                Toast.makeText(this, "You can only choose a IP address that is used by the system.", Toast.LENGTH_LONG).show();
                return;
            }

            storedAddressList.add(entry);
            updateSpinners();

            // select the added element
            int idx = AddressEntry.listIndexOf(storedAddressList, entry);
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

        pickSystemAddressButton.setOnClickListener((View v) -> {
            int pos = systemAddressSpinner.getSelectedItemPosition();
            if (pos > -1 && !systemAddressListAdapter.isEmpty()) {
                addressEditText.setText(systemAddressList.get(pos).address);
            }
        });

        pickStoredAddressButton.setOnClickListener((View v) -> {
            int pos = storedAddressSpinner.getSelectedItemPosition();
            if (pos > -1 && !storedAddressListAdapter.isEmpty()) {
                addressEditText.setText(storedAddressList.get(pos).address);
            }
        });

        saveButton.setOnClickListener((View v) -> {
            ArrayList<String> addresses = new ArrayList<>();
            for (AddressEntry ae : this.storedAddressList) {
                addresses.add(ae.address);
            }
            this.binder.getSettings().setAddresses(addresses);
            initKeyPair();
            this.binder.saveDatabase();
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        });

        abortButton.setOnClickListener((View v) -> {
            finish();
        });

        bindService();
    }

    private void initKeyPair() {
        AddressEntry ae = this.storedAddressList.get(this.storedAddressList.size() - 1);
        // create secret/public key pair
        final byte[] publicKey = Utils.parseInetSocketAddress(ae.address, 0).getAddress().getAddress();
        final byte[] secretKey = null;

        Settings settings = this.binder.getSettings();
        settings.setPublicKey(publicKey);
        settings.setSecretKey(secretKey);
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

    private void updateAddressEditTextButtons() {
        String address = addressEditText.getText().toString();

        boolean exists = (AddressEntry.findAddressEntry(storedAddressList, address) != null);
        if (exists) {
            addButton.setEnabled(false);
            removeButton.setEnabled(true);
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

    private void updateSpinners() {
        // compare by device first, address second
        Comparator<AddressEntry> compareAddressEntries = (AddressEntry o1, AddressEntry o2) -> {
            int dd = o1.device.compareTo(o2.device);
            if (dd == 0) {
                return o1.address.compareTo(o2.address);
            } else {
                return dd;
            }
        };

        Collections.sort(storedAddressList, compareAddressEntries);
        Collections.sort(systemAddressList, compareAddressEntries);

        storedAddressListAdapter.update(storedAddressList, systemAddressList);
        storedAddressListAdapter.notifyDataSetChanged();

        systemAddressListAdapter.update(systemAddressList, storedAddressList);
        systemAddressListAdapter.notifyDataSetChanged();

        systemAddressSpinner.setAdapter(storedAddressListAdapter);
        systemAddressSpinner.setAdapter(systemAddressListAdapter);

        pickStoredAddressButton.setEnabled(!storedAddressListAdapter.isEmpty());
        pickSystemAddressButton.setEnabled(!systemAddressListAdapter.isEmpty());

        updateAddressEditTextButtons();
    }

    /*
     * Create AddressEntry from address string.
     * Do not perform any domain lookup
     */
    AddressEntry parseAddress(String address) {
        // instead of parsing, lookup in known addresses first
        AddressEntry ae = AddressEntry.findAddressEntry(systemAddressList, address);
        if (ae != null) {
            // known address
            return new AddressEntry(address, ae.device, ae.multicast);
        } else if (Utils.isMAC(address)) {
            // MAC address
            boolean mc = Utils.isMulticastMAC(Utils.macAddressToBytes(address));
            return new AddressEntry(address, "", mc);
        } else if (Utils.isIP(address)) {
            // IP address
            boolean mc = false;
            try {
                mc = Utils.parseInetSocketAddress(address, 0).getAddress().isMulticastAddress();
            } catch (Exception e) {
                // ignore
            }
            return new AddressEntry(address, "", mc);
        } else {
            // domain
            return new AddressEntry(address, "", false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void log(String s) {
        Log.d(this, s);
    }

    public class AddressListAdapter extends BaseAdapter {
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

        public void update(List<AddressEntry> addressEntries, List<AddressEntry> addressEntriesMarked) {
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
            boolean nightMode = binder.getSettings().getNightMode();
            if (isEmpty()) {
                label.setText(getResources().getString(R.string.empty_list_item));
                if(nightMode){
                    label.setTextColor(Color.WHITE);
                } else {
                    label.setTextColor(Color.BLACK);
                }
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
                    if(nightMode){
                        label.setTextColor(Color.WHITE);
                    } else {
                        label.setTextColor(Color.BLACK);
                    }
                } else {
                    label.setTextColor(this.markColor);
                }
            }

            return view;
        }
    }
}
