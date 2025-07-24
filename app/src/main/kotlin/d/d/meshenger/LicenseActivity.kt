/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class LicenseActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        title = getString(R.string.menu_license)

        val toolbar = findViewById<Toolbar>(R.id.license_toolbar)
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

        // reading the license file can be slow => use a thread
        Thread {
            try {
                val buffer = StringBuffer()
                val reader = BufferedReader(InputStreamReader(assets.open("license.txt")))
                while (true) {
                    val line = reader.readLine()
                    if (line != null) {
                        if (line.trim().isEmpty()){
                            buffer.append("\n")
                        } else {
                            buffer.append(line + "\n")
                        }
                    } else {
                        break
                    }
                }
                reader.close()
                runOnUiThread {
                    findViewById<ProgressBar>(R.id.licenseLoadingBar).visibility = View.GONE
                    findViewById<TextView>(R.id.licenceText).text = buffer.toString()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }
}