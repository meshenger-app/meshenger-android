package org.rivchain.cuplink.rivmesh


import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.rivchain.cuplink.MainActivity

class TileServiceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, MainActivity::class.java)
        startService(intent)
        finish()
    }
}