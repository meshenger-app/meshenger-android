package com.androidbuts.multispinnerfilter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MultiSpinnerSearch extends AppCompatSpinner implements OnCancelListener {

    public static AlertDialog.Builder builder;

    public static AlertDialog ad;

    private boolean highlightSelected = false;

    private int highlightColor = ContextCompat.getColor(getContext(), R.color.list_selected);

    private int textColor = Color.GRAY;

    private int limit = -1;

    private int selected = 0;

    private String defaultText = "";

    private String spinnerTitle = "";

    private String emptyTitle = "Not Found!";

    private String searchHint = "Type to search";

    private String clearText = "Clear All";

    private boolean colorSeparation = false;

    private boolean isShowSelectAllButton = false;

    private MultiSpinnerListener listener;

    private LimitExceedListener limitListener;

    private MyAdapter adapter;

    private List<KeyPairBoolData> items;

    private boolean isSearchEnabled = true;

    public MultiSpinnerSearch(Context context) {
        super(context);
    }

    public MultiSpinnerSearch(Context arg0, AttributeSet arg1) {
        super(arg0, arg1);
        TypedArray a = arg0.obtainStyledAttributes(arg1, R.styleable.MultiSpinnerSearch);
        for (int i = 0; i < a.getIndexCount(); ++i) {
            int attr = a.getIndex(i);
            if (attr == R.styleable.MultiSpinnerSearch_hintText) {
                this.setHintText(a.getString(attr));
                spinnerTitle = this.getHintText();
                defaultText = spinnerTitle;
                break;
            } else if (attr == R.styleable.MultiSpinnerSearch_highlightSelected) {
                highlightSelected = a.getBoolean(attr, false);
            } else if (attr == R.styleable.MultiSpinnerSearch_highlightColor) {
                highlightColor = a.getColor(attr, ContextCompat.getColor(getContext(), R.color.list_selected));
            } else if (attr == R.styleable.MultiSpinnerSearch_textColor) {
                textColor = a.getColor(attr, Color.GRAY);
            } else if (attr == R.styleable.MultiSpinnerSearch_clearText) {
                this.setClearText(a.getString(attr));
            }
        }
        //Log.i(TAG, "spinnerTitle: " + spinnerTitle);
        a.recycle();
    }

    public MultiSpinnerSearch(Context arg0, AttributeSet arg1, int arg2) {
        super(arg0, arg1, arg2);
    }

    public boolean isSearchEnabled() {
        return isSearchEnabled;
    }

    public void setSearchEnabled(boolean searchEnabled) {
        isSearchEnabled = searchEnabled;
    }

    public boolean isColorSeparation() {
        return colorSeparation;
    }

    public void setColorSeparation(boolean colorSeparation) {
        this.colorSeparation = colorSeparation;
    }

    public String getHintText() {
        return this.spinnerTitle;
    }

    public void setHintText(String hintText) {
        this.spinnerTitle = hintText;
        defaultText = spinnerTitle;
    }

    public void setClearText(String clearText) {
        this.clearText = clearText;
    }

    public void setLimit(int limit, LimitExceedListener listener) {
        this.limit = limit;
        this.limitListener = listener;
        isShowSelectAllButton = false; // if its limited, select all default false.
    }

    public List<KeyPairBoolData> getSelectedItems() {
        List<KeyPairBoolData> selectedItems = new ArrayList<>();
        for (KeyPairBoolData item : items) {
            if (item.isSelected()) {
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }

    public List<Long> getSelectedIds() {
        List<Long> selectedItemsIds = new ArrayList<>();
        for (KeyPairBoolData item : items) {
            if (item.isSelected()) {
                selectedItemsIds.add(item.getId());
            }
        }
        return selectedItemsIds;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        // refresh text on spinner
        StringBuilder spinnerBuffer = new StringBuilder();
        ArrayList<KeyPairBoolData> selectedData = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            KeyPairBoolData currentData = items.get(i);
            if (currentData.isSelected()) {
                selectedData.add(currentData);
                spinnerBuffer.append(currentData.getName());
                spinnerBuffer.append(", ");
            }
        }
        String spinnerText = spinnerBuffer.toString();
        if (spinnerText.length() > 2)
            spinnerText = spinnerText.substring(0, spinnerText.length() - 2);
        else
            spinnerText = this.getHintText();
        ArrayAdapter<String> adapterSpinner = new ArrayAdapter<>(getContext(), R.layout.textview_for_spinner, new String[]{spinnerText});
        setAdapter(adapterSpinner);
        if (adapter != null)
            adapter.notifyDataSetChanged();
        listener.onItemsSelected(selectedData);

        /*
         * To hide dropdown which is already opened at the time of performClick...
         * This code will hide automatically and no need to tap by user.
         */
        new Thread(() -> {
            Instrumentation inst = new Instrumentation();
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        }).start();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        builder = new AlertDialog.Builder(getContext());
        builder.setTitle(spinnerTitle);
        final LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater.inflate(R.layout.alert_dialog_listview_search, null);
        builder.setView(view);
        final ListView listView = view.findViewById(R.id.alertSearchListView);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setFastScrollEnabled(false);
        adapter = new MyAdapter(getContext(), items);
        listView.setAdapter(adapter);
        final TextView emptyText = view.findViewById(R.id.empty);
        emptyText.setText(emptyTitle);
        listView.setEmptyView(emptyText);
        final EditText editText = view.findViewById(R.id.alertSearchEditText);
        if (isSearchEnabled) {
            editText.setVisibility(VISIBLE);
            editText.setHint(searchHint);
            editText.addTextChangedListener(new TextWatcher() {

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.getFilter().filter(s.toString());
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        } else {
            editText.setVisibility(GONE);
        }

        /*
         * For selected items
         */
        selected = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isSelected())
                selected++;
        }

        /*
        Added Select all Dialog Button.
         */
        if (isShowSelectAllButton && limit == -1) {
            builder.setNeutralButton(android.R.string.selectAll, (dialog, which) -> {
                for (int i = 0; i < adapter.arrayList.size(); i++) {
                    adapter.arrayList.get(i).setSelected(true);
                }
                adapter.notifyDataSetChanged();
                // To call onCancel listner and set title of selected items.
                dialog.cancel();
            });
        }
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            //Log.i(TAG, " ITEMS : " + items.size());
            dialog.cancel();
        });
        builder.setNegativeButton(clearText, (dialog, which) -> {
            //Log.i(TAG, " ITEMS : " + items.size());
            for (int i = 0; i < adapter.arrayList.size(); i++) {
                adapter.arrayList.get(i).setSelected(false);
            }
            adapter.notifyDataSetChanged();
            dialog.cancel();
        });
        builder.setOnCancelListener(this);
        ad = builder.show();
        Objects.requireNonNull(ad.getWindow()).setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        return true;
    }

    public void setItems(List<KeyPairBoolData> items, MultiSpinnerListener listener) {
        this.items = items;
        this.listener = listener;
        StringBuilder spinnerBuffer = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isSelected()) {
                spinnerBuffer.append(items.get(i).getName());
                spinnerBuffer.append(", ");
            }
        }
        if (spinnerBuffer.length() > 2)
            defaultText = spinnerBuffer.toString().substring(0, spinnerBuffer.toString().length() - 2);
        ArrayAdapter<String> adapterSpinner = new ArrayAdapter<>(getContext(), R.layout.textview_for_spinner, new String[]{defaultText});
        setAdapter(adapterSpinner);
    }

    public void setEmptyTitle(String emptyTitle) {
        this.emptyTitle = emptyTitle;
    }

    public void setSearchHint(String searchHint) {
        this.searchHint = searchHint;
    }

    public boolean isShowSelectAllButton() {
        return isShowSelectAllButton;
    }

    public void setShowSelectAllButton(boolean showSelectAllButton) {
        isShowSelectAllButton = showSelectAllButton;
    }

    public interface LimitExceedListener {
        void onLimitListener(KeyPairBoolData data);
    }

    //Adapter Class
    public class MyAdapter extends BaseAdapter implements Filterable {

        final List<KeyPairBoolData> mOriginalValues; // Original Values

        final LayoutInflater inflater;

        List<KeyPairBoolData> arrayList;

        MyAdapter(Context context, List<KeyPairBoolData> arrayList) {
            this.arrayList = arrayList;
            this.mOriginalValues = arrayList;
            inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return arrayList.size();
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            //            //Log.i(TAG, "getView() enter");
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = inflater.inflate(R.layout.item_listview_multiple, parent, false);
                holder.textView = convertView.findViewById(R.id.alertTextView);
                holder.checkBox = convertView.findViewById(R.id.alertCheckbox);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder)convertView.getTag();
            }
            int background = R.color.white;
            if (colorSeparation) {
                final int backgroundColor = (position % 2 == 0) ? R.color.list_even : R.color.list_odd;
                background = backgroundColor;
                convertView.setBackgroundColor(ContextCompat.getColor(getContext(), backgroundColor));
            }
            final KeyPairBoolData data = arrayList.get(position);
            holder.textView.setText(data.getName());
            holder.checkBox.setChecked(data.isSelected());
            convertView.setOnClickListener(v -> {
                if (data.isSelected()) { // deselect
                    selected--;
                } else { // selected
                    selected++;
                    if (selected > limit && limit > 0) {
                        --selected;// select with limit
                        if (limitListener != null)
                            limitListener.onLimitListener(data);
                        return;
                    }
                }
                final ViewHolder temp = (ViewHolder)v.getTag();
                temp.checkBox.setChecked(!temp.checkBox.isChecked());
                data.setSelected(!data.isSelected());
                holder.textView.setTextColor(ContextCompat.getColor(getContext(), R.color.gray));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    holder.checkBox.setButtonTintList(ContextCompat.getColorStateList(getContext(), R.color.gray));
                }

                //Log.i(TAG, "On Click Selected Item : " + data.getName() + " : " + data.isSelected());
                notifyDataSetChanged();
            });
            if (data.isSelected()) {
//                holder.textView.setTextColor(textColor);
                holder.textView.setTextColor(ContextCompat.getColor(getContext(), R.color.call_green));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    holder.checkBox.setButtonTintList(ContextCompat.getColorStateList(getContext(), R.color.call_green));
                }

                if (highlightSelected) {
                    holder.textView.setTypeface(null, Typeface.BOLD);
                    convertView.setBackgroundColor(highlightColor);
                } else {
                    convertView.setBackgroundColor(Color.WHITE);
                }
            } else {
                holder.textView.setTypeface(null, Typeface.NORMAL);
                convertView.setBackgroundColor(ContextCompat.getColor(getContext(), background));

                holder.textView.setTextColor(ContextCompat.getColor(getContext(), R.color.gray));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    holder.checkBox.setButtonTintList(ContextCompat.getColorStateList(getContext(), R.color.gray));
                }

            }
            holder.checkBox.setTag(holder);
            return convertView;
        }

        @SuppressLint("DefaultLocale")
        @Override
        public Filter getFilter() {
            return new Filter() {

                @SuppressWarnings("unchecked")
                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    arrayList = (List<KeyPairBoolData>)results.values; // has the filtered values
                    notifyDataSetChanged();  // notifies the data with new filtered values
                }

                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();        // Holds the results of a filtering operation in values
                    List<KeyPairBoolData> FilteredArrList = new ArrayList<>();


                    /*
                     *
                     *  If constraint(CharSequence that is received) is null returns the mOriginalValues(Original) values
                     *  else does the Filtering and returns FilteredArrList(Filtered)
                     *
                     **/
                    if (constraint == null || constraint.length() == 0) {
                        // set the Original result to return
                        results.count = mOriginalValues.size();
                        results.values = mOriginalValues;
                    } else {
                        constraint = constraint.toString().toLowerCase();
                        for (int i = 0; i < mOriginalValues.size(); i++) {
                            //Log.i(TAG, "Filter : " + mOriginalValues.get(i).getName() + " -> " + mOriginalValues.get(i).isSelected());
                            String data = mOriginalValues.get(i).getName();
                            if (data.toLowerCase().contains(constraint.toString())) {
                                FilteredArrList.add(mOriginalValues.get(i));
                            }
                        }
                        // set the Filtered result to return
                        results.count = FilteredArrList.size();
                        results.values = FilteredArrList;
                    }
                    return results;
                }
            };
        }

        private class ViewHolder {
            TextView textView;

            CheckBox checkBox;
        }
    }
}
