package org.rivchain.cuplink.rivmesh


import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.PopupWindow
import android.widget.TextView
import org.rivchain.cuplink.R

class DropDownAdapter(
    context: Context,
    textViewResourceId: Int,
    objects: Array<String>,
    popup: PopupWindow,
    editText: TextView
) :
    ArrayAdapter<String?>(context, textViewResourceId, objects), OnItemClickListener {

    private val objects: Array<String> = objects
    private val popup: PopupWindow = popup
    private val editText: TextView = editText

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        return getCustomView(position, convertView, parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getCustomView(position, convertView, parent)
    }

    fun getCustomView(position: Int, view: View?, parent: ViewGroup?): View {
        var convertView: View? = view
        if (convertView == null) {
            convertView =
                LayoutInflater.from(context).inflate(R.layout.dropdown_item, parent, false)
        }
        val sub = convertView?.findViewById(R.id.sub) as TextView
        val address = objects[position]
        sub.text = address
        return convertView
    }

    override fun onItemClick(arg0: AdapterView<*>?, v: View, arg2: Int, arg3: Long) {

        // get the context and main activity to access variables
        // add some animation when a list item was clicked
        val fadeInAnimation: Animation =
            AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
        fadeInAnimation.duration = 10
        v.startAnimation(fadeInAnimation)
        val text: View = v.findViewById(R.id.sub) ?: return
        val address = (text as TextView).text.toString()
        // dismiss the pop up
        popup.dismiss()
        editText.text = address
    }

}