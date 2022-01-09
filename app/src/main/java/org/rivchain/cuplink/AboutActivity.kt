package org.rivchain.cuplink

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

class AboutActivity : CupLinkActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        title = resources.getString(R.string.menu_about)
        (findViewById<View>(R.id.versionTv) as TextView).text = Utils.getApplicationVersion(this)
        findViewById<View>(R.id.licenseTV).setOnClickListener { v: View? ->
            val intent = Intent(this, LicenseActivity::class.java)
            startActivity(intent)
        }
    }
}