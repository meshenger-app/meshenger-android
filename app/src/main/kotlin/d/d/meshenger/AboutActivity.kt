package d.d.meshenger

import android.content.Intent
import android.os.Bundle
import android.widget.TextView

class AboutActivity: d.d.meshenger.MeshengerActivity() {
    private val TAG = "AboutActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        title = resources.getString(R.string.menu_about)

        findViewById<TextView>(R.id.versionTv).text =
            Utils.getApplicationVersion(this)


        findViewById<TextView>(R.id.licenseTV).setOnClickListener{
            startActivity(Intent(this, LicenseActivity::class.java))
        }
    }
}