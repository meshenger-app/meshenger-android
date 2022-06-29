package d.d.meshenger.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import d.d.meshenger.R
import d.d.meshenger.adapter.EventListAdapter
import d.d.meshenger.model.Contact
import d.d.meshenger.model.Event
import d.d.meshenger.service.MainService
import d.d.meshenger.utils.AddressUtils.getEUI64MAC
import d.d.meshenger.utils.Log.d
import d.d.meshenger.utils.Utils.bytesToMacAddress
import java.net.Inet6Address
import java.net.InetSocketAddress

class EventListFragment: Fragment() {

    companion object {
        const val TAG = "EventListFragment"


        /*
     * When adding an unknown contact, try to
     * extract a MAC address from the IPv6 address.
     */
        fun getGeneralizedAddress(address: String): String {
            val addr = InetSocketAddress.createUnresolved(address, 0).address
            if (addr is Inet6Address) {
                // if the IPv6 address contains a MAC address, take that.
                val mac = getEUI64MAC(addr)
                if (mac != null) {
                    return bytesToMacAddress(mac)
                }
            }
            return address
        }

    }

    private var eventListView: RecyclerView? = null
    private var eventListAdapter: EventListAdapter? = null
    private lateinit var fabDelete: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_event_list, container, false);

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eventListView = view.findViewById(R.id.eventList)
        fabDelete = view.findViewById(R.id.fabDelete)
        fabDelete.setOnClickListener { v: View? ->
            MainService.instance!!.getEvents()!!.clear()
            refreshEventList()
        }

        eventListAdapter =
            EventListAdapter(requireActivity(), ArrayList<Event>(), this, ArrayList<Contact>())

        eventListView?.apply {
            adapter = eventListAdapter
            layoutManager = LinearLayoutManager(requireContext())
            refreshEventList()
        }
    }


    fun refreshEventList() {
        d(TAG, "refreshEventList")
        Handler(Looper.getMainLooper()).post {
            val events =
                MainService.instance!!.getEvents()!!.getEventListCopy()
            val contacts =
                MainService.instance!!.getContacts()!!.getContactListCopy()
            d(
                TAG,
                "refreshEventList update: " + events.size
            )
            eventListAdapter?.let {
                eventListView?.adapter = it
                eventListView?.layoutManager = LinearLayoutManager(requireContext())
                it.update(events, contacts)
                //it.notifyDataSetChanged() //Needed?
            }

        }
    }



}