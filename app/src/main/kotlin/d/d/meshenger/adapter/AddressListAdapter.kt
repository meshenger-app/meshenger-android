package d.d.meshenger.adapter

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.TextView
import d.d.meshenger.R
import d.d.meshenger.model.AddressEntry
import d.d.meshenger.utils.Utils

class AddressListAdapter(private val context: Activity, private val markColor: Int, private var addressEntries: ArrayList<AddressEntry>)
    : BaseAdapter() {

    private var addressEntriesMarked: ArrayList<AddressEntry>

    override fun isEmpty(): Boolean {
        return addressEntries.isEmpty()
    }

    fun update(addressEntries: ArrayList<AddressEntry>, addressEntriesMarked: ArrayList<AddressEntry>) {
        this.addressEntries = addressEntries
        this.addressEntriesMarked = addressEntriesMarked
    }

    override fun getCount(): Int {
        return if (isEmpty) {
            1
        } else addressEntries.size
    }

    override fun getItem(position: Int): AddressEntry? {
        return if (isEmpty) {
            null
        } else

            addressEntries[position]
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val x = convertView?: context.layoutInflater.inflate(R.layout.activity_address_item, parent, false)
        val label = x.findViewById<TextView>(R.id.label)
        //val label = super.getView(position, convertView, parent) as TextView
        label?.let {
            if (isEmpty) {
                label.setText(context.resources.getString(R.string.empty_list_item))
                label.setTextColor(Color.BLACK)
            } else {
                val ae = addressEntries[position]
                val info = ArrayList<String>()
                if (ae.device.isNotEmpty()) {
                    info.add(ae.device)
                }
                if (ae.multicast) {
                    info.add("multicast")
                }
                label.text = ae.address + if (info.isEmpty()) "" else " (" + Utils.join(info) + ")"
                if (AddressEntry.listIndexOf(addressEntriesMarked, ae) < 0) {
                    label.setTextColor(Color.BLACK)
                } else {
                    label.setTextColor(markColor)
                }
            }

        }
        return x!!
    }

    init {
        addressEntries = java.util.ArrayList()
        addressEntriesMarked = java.util.ArrayList()
    }
}