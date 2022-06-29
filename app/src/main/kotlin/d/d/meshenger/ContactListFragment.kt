package d.d.meshenger

import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Looper.getMainLooper
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.*
import android.widget.AdapterView.OnItemLongClickListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import d.d.meshenger.Utils.isValidContactName
import d.d.meshenger.call.CallActivity
import d.d.meshenger.call.DirectRTCClient
import d.d.meshenger.mock.MockContacts
import org.json.JSONException

//TODO: Multiple OnClick Listeners
class ContactListFragment: Fragment(), AdapterView.OnItemClickListener {

    private var contactListView: RecyclerView? = null
    private var fabExpanded = false
    private lateinit var fabScan: FloatingActionButton
    private lateinit var fabGen: FloatingActionButton
    private lateinit var fab: FloatingActionButton
    private lateinit var mainActivity: MainActivity

    companion object {
        const val TAG = "ContactListFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        return inflater.inflate(R.layout.fragment_contact_list, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity = (activity as MainActivity)
        fab = view.findViewById(R.id.fab)
        fabScan = view.findViewById(R.id.fabScan)
        fabGen = view.findViewById(R.id.fabGenerate)
        contactListView = view.findViewById(R.id.contactList)
        //TODO: Adapter is set as non-null
        contactListView?.apply{
            adapter = ContactListAdapter(
                mainActivity, MainService.instance?.getContacts()?.getContactList()!!
            )
            layoutManager = LinearLayoutManager(requireActivity())
        }
        fabScan.setOnClickListener {
            startActivity(
                Intent(
                    mainActivity,
                    QRScanActivity::class.java
                )
            )
        }
        fabGen.setOnClickListener {
            startActivity(
                Intent(
                    mainActivity,
                    QRShowActivity::class.java
                )
            )
        }
        fab.setOnClickListener { fab: View ->
            runFabAnimation(
                fab
            )
        }
        refreshContactList()
    }

    private fun runFabAnimation(fab: View) {
        Log.d(TAG, "runFabAnimation")
        val scanSet = AnimationSet(mainActivity, null)
        val generateSet = AnimationSet(mainActivity, null)
        val distance = 200
        val duration = 300
        val scanAnimation: TranslateAnimation
        val generateAnimation: TranslateAnimation
        val alphaAnimation: AlphaAnimation
        if (fabExpanded) {
            scanAnimation = TranslateAnimation(0F, 0F, (-distance).toFloat(), 0F)
            generateAnimation = TranslateAnimation(0F, 0F, (-distance * 2).toFloat(), 0F)
            alphaAnimation = AlphaAnimation(1.0f, 0.0f)
            (fab as FloatingActionButton).setImageResource(R.drawable.qr_glass)
            fabScan.y = fabScan.y + distance
            fabGen.y = fabGen.y + distance * 2
        } else {
            scanAnimation = TranslateAnimation(0F, 0F, distance.toFloat(), 0F)
            generateAnimation = TranslateAnimation(0F, 0F, (distance * 2).toFloat(), 0F)
            alphaAnimation = AlphaAnimation(0.0f, 1.0f)
            (fab as FloatingActionButton).setImageResource(R.drawable.ic_menu_close_clear_cancel) //TODO: Fixed this missing variable.
            fabScan.y = fabScan.y - distance
            fabGen.y = fabGen.y - distance * 2
        }
        scanSet.apply{
            this.addAnimation(scanAnimation)
            this.addAnimation(alphaAnimation)
            this.fillAfter = true
            this.duration = duration.toLong()
        }
        generateSet.apply{
            this.addAnimation(generateAnimation)
            this.addAnimation(alphaAnimation)
            this.fillAfter = true
            this.duration = duration.toLong()
        }
        fabScan.apply{
            this.visibility = View.VISIBLE
            this.startAnimation(scanSet)
        }
        fabGen.apply{
            this.visibility = View.VISIBLE
            this.startAnimation(generateSet)
        }
        fabExpanded = !fabExpanded
    }

    private fun collapseFab() {
        if (fabExpanded) {
            fab.setImageResource(R.drawable.qr_glass)
            fabScan.clearAnimation()
            fabGen.clearAnimation()
            fabScan.y = fabScan.y + 200
            fabGen.y = fabGen.y + 200 * 2
            fabExpanded = false
        }
    }


    fun refreshContactList() {
        Log.d(TAG, "refreshContactList")
        Handler(getMainLooper()).post {
            val contacts = MainService.instance?.getContacts()?.getContactListCopy()?: ArrayList<Contact?>()
            contactListView?.let { it ->
                it.adapter = ContactListAdapter(
                    mainActivity,
                    contacts
                )

            }

        }

    }


    override fun onPause() {
        super.onPause()
        collapseFab()
    }

    override fun onItemClick(adapterView: AdapterView<*>, view: View?, i: Int, l: Long) {
        Log.d(TAG, "onItemClick")
        val contact = adapterView.adapter.getItem(i) as Contact
        if (DirectRTCClient.createOutgoingCall(contact)) {
            val intent = Intent(context, CallActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

}