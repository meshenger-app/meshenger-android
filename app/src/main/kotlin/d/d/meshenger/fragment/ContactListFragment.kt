package d.d.meshenger.fragment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import d.d.meshenger.*
import d.d.meshenger.utils.Log.d
import d.d.meshenger.activity.MainActivity
import d.d.meshenger.activity.QRScanActivity
import d.d.meshenger.adapter.ContactListAdapter
import d.d.meshenger.activity.CallActivity
import d.d.meshenger.call.DirectRTCClient
import d.d.meshenger.dialog.QRShowDialog
import d.d.meshenger.model.Contact
import d.d.meshenger.service.MainService

class ContactListFragment: Fragment(), AdapterView.OnItemClickListener {

    companion object {
        private const val TAG = "ContactListFragment"

    }

    private var contactListView: RecyclerView? = null
    private var fabExpanded = false
    private lateinit var fabScan: FloatingActionButton
    private lateinit var fabGen: FloatingActionButton
    private lateinit var fab: FloatingActionButton
    private lateinit var mainActivity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View?  = inflater.inflate(R.layout.fragment_contact_list, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        mainActivity = (activity as MainActivity?)!!
        fab = view.findViewById(R.id.fab)
        fabScan = view.findViewById(R.id.fabScan)
        fabGen = view.findViewById(R.id.fabGenerate)
        contactListView = view.findViewById(R.id.contactList)

        fabScan.setOnClickListener { v: View? ->
            startActivity(
                Intent(
                    mainActivity,
                    QRScanActivity::class.java
                )
            )
        }
        fabGen.setOnClickListener { v: View? ->
//            startActivity(
//                Intent(
//                    mainActivity,
//                    QRShowActivity::class.java
//                )
//            )
            QRShowDialog(requireContext(), activity?.intent!!)
                .show(requireActivity().supportFragmentManager, "")
        }
        fab.setOnClickListener { fab: View ->
            this.runFabAnimation(
                fab
            )
        }

        refreshContactList()

    }


    private fun runFabAnimation(fab: View) {
        d(TAG, "runFabAnimation")
        val scanSet = AnimationSet(mainActivity, null)
        val generateSet = AnimationSet(mainActivity, null)
        val distance = 200
        val duration = 300
        val scanAnimation: TranslateAnimation
        val generateAnimnation: TranslateAnimation
        val alphaAnimation: AlphaAnimation
        if (fabExpanded) {
            scanAnimation = TranslateAnimation(0F, 0F, (-distance).toFloat(), 0F)
            generateAnimnation = TranslateAnimation(0F, 0F, (-distance * 2).toFloat(), 0F)
            alphaAnimation = AlphaAnimation(1.0f, 0.0f)
            (fab as FloatingActionButton).setImageResource(R.drawable.qr_glass)
            fabScan.y = fabScan.y + distance
            fabGen.y = fabGen.y + distance * 2
        } else {
            scanAnimation = TranslateAnimation(0F, 0F, distance.toFloat(), 0F)
            generateAnimnation = TranslateAnimation(0F, 0F, (distance * 2).toFloat(), 0F)
            alphaAnimation = AlphaAnimation(0.0f, 1.0f)
            (fab as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            fabScan.y = fabScan.y - distance
            fabGen.y = fabGen.y - distance * 2
        }
        scanSet.addAnimation(scanAnimation)
        scanSet.addAnimation(alphaAnimation)
        scanSet.fillAfter = true
        generateSet.addAnimation(generateAnimnation)
        generateSet.addAnimation(alphaAnimation)
        generateSet.fillAfter = true
        scanSet.duration = duration.toLong()
        generateSet.duration = duration.toLong()
        fabScan.visibility = View.VISIBLE
        fabGen.visibility = View.VISIBLE
        fabScan.startAnimation(scanSet)
        fabGen.startAnimation(generateSet)
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
        d(TAG, "refreshContactList")
        Handler(Looper.getMainLooper()).post {
            val contacts =
                MainService.instance!!.getContacts()!!.getContactListCopy()
            contactListView?.let {
                it.adapter = ContactListAdapter(
                    mainActivity,
                    contacts
                )
                it.layoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)

            }
        }
    }


    override fun onPause() {
        super.onPause()
        collapseFab()
    }

    override fun onItemClick(adapterView: AdapterView<*>, view: View?, i: Int, l: Long) {
        d(TAG, "onItemClick")
        val contact = adapterView.adapter.getItem(i) as Contact
        if (DirectRTCClient.createOutgoingCall(contact)) {
            val intent = Intent(context, CallActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

}