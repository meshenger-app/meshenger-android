package d.d.meshenger

import d.d.meshenger.MeshengerActivity
import android.os.Bundle
import d.d.meshenger.R
import android.widget.TextView
import android.content.Intent
import android.view.View
import androidx.appcompat.widget.Toolbar
import d.d.meshenger.LicenseActivity

class AboutActivity : MeshengerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        title = resources.getString(R.string.menu_about)
        val toolbar = findViewById<Toolbar>(R.id.about_toolbar)
        toolbar.apply {
            setNavigationOnClickListener {
                finish()
            }
        }
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        (findViewById<View>(R.id.versionTv) as TextView).text = Utils.getApplicationVersion(this)
        findViewById<View>(R.id.licenseTV).setOnClickListener { v: View? ->
            val intent = Intent(this, LicenseActivity::class.java)
            startActivity(intent)
        }
    }
}