package d.d.meshenger.activity

import android.os.Bundle
import android.view.View
import android.widget.TextView
import d.d.meshenger.base.MeshengerActivity
import d.d.meshenger.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class LicenseActivity: MeshengerActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        title = resources.getString(R.string.menu_license)

        // reading the license file can be slow => use a thread
        Thread {
            try {
                val buffer = StringBuffer()
                val reader =
                    BufferedReader(InputStreamReader(assets.open("license.txt")))
                var line: String
                while (reader.readLine().also { line = it } != null) {
                    buffer.append(
                        """
                        ${line.trim { it <= ' ' }}
                        
                        """.trimIndent()
                    )
                }
                reader.close()
                runOnUiThread {
                    findViewById<View>(R.id.licenseLoadingBar).visibility =
                        View.GONE
                    (findViewById<View>(R.id.licenceText) as TextView).text =
                        buffer.toString()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

}