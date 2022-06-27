package d.d.meshenger.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import d.d.meshenger.R
import d.d.meshenger.model.AddressEntry
import d.d.meshenger.utils.Log
import d.d.meshenger.utils.Utils


class AddressListAdapterFix(context: Context,
                            @LayoutRes private val layoutResource: Int,
                            @IdRes private val textViewResourceId: Int = 0,
                            private val markColor: Int,
                            private var addressEntries: ArrayList<AddressEntry>) : ArrayAdapter<AddressEntry>(context, layoutResource, addressEntries) {

    private var addressEntriesMarked = ArrayList<AddressEntry>()


    override fun isEmpty(): Boolean {
        return addressEntries.isEmpty()
    }

    override fun getCount(): Int {
        return if (isEmpty) {
            1
        } else addressEntries.size
    }

    override fun getItemId(position: Int): Long = position.toLong()


    fun update(addressEntries: ArrayList<AddressEntry>, addressEntriesMarked: ArrayList<AddressEntry>) {
        this.addressEntries = addressEntries
        this.addressEntriesMarked = addressEntriesMarked
    }


    override fun getItem(position: Int): AddressEntry? =
        if (!isEmpty) addressEntries[position] else null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = createViewFromResource(convertView, parent, layoutResource)

        return bindData(getItem(position), view)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = createViewFromResource(convertView, parent, android.R.layout.simple_spinner_dropdown_item)

        return bindData(getItem(position), view)
    }

    private fun createViewFromResource(convertView: View?, parent: ViewGroup, layoutResource: Int): TextView {
        val context = parent.context
        val view = convertView ?: LayoutInflater.from(context).inflate(layoutResource, parent, false)
        return try {
            if (textViewResourceId == 0) view as TextView
            else {
                view.findViewById(textViewResourceId) ?:
                throw RuntimeException("Failed to find view with ID " +
                        "${context.resources.getResourceName(textViewResourceId)} in item layout")
            }
        } catch (ex: ClassCastException){
            Log.e("AddressListAdapter", "You must supply a resource ID for a TextView")
            throw IllegalStateException(
                "ArrayAdapter requires the resource ID to be a TextView", ex)
        }
    }

    private fun bindData(value: AddressEntry?, view: TextView): TextView {
        view.let {
            if (isEmpty) {
                it.setText(context.resources.getString(R.string.empty_list_item))
                it.setTextColor(Color.BLACK)
            } else {
                val ae = value!!
                val info = ArrayList<String>()
                if (ae.device.isNotEmpty()) {
                    info.add(ae.device)
                }
                if (ae.multicast) {
                    info.add("multicast")
                }
                it.text = ae.address + if (info.isEmpty()) "" else " (" + Utils.join(info) + ")"
                if (AddressEntry.listIndexOf(addressEntriesMarked, ae) < 0) {
                    it.setTextColor(Color.BLACK)
                } else {
                    it.setTextColor(markColor)
                }
            }

        }
        return view
    }
}
