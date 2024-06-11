package org.rivchain.cuplink

import android.content.Intent
import android.os.Bundle

class DeepLinkEntryActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent: Intent = MainActivity.clearTop(this)
        val data = getIntent().data
        intent.setData(data)
        startActivity(intent)
    }
}