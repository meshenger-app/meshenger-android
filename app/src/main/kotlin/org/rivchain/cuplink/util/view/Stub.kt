package org.rivchain.cuplink.util.view

import android.view.View
import android.view.ViewStub


class Stub<T : View?>(viewStub: ViewStub) {
    private var viewStub: ViewStub?
    private var view: T? = null

    init {
        this.viewStub = viewStub
    }

    val id: Int
        get() = if ((viewStub != null)) viewStub!!.id else view!!.id

    fun get(): T? {
        if (view == null) {
            view = viewStub!!.inflate() as T
            viewStub = null
        }
        return view
    }

    fun resolved(): Boolean {
        return view != null
    }

    var visibility: Int
        get() = if (resolved()) {
            get()!!.visibility
        } else {
            View.GONE
        }
        set(visibility) {
            if (resolved() || visibility == View.VISIBLE) {
                get()!!.visibility = visibility
            }
        }
    val isVisible: Boolean
        get() = visibility == View.VISIBLE
}