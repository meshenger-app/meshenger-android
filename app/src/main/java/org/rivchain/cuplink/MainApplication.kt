package org.rivchain.cuplink

import android.R
import android.app.Application
import android.content.Context
import org.acra.ACRA.init
import org.acra.BuildConfig
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.DialogConfigurationBuilder
import org.acra.config.HttpSenderConfigurationBuilder
import org.acra.data.StringFormat
import org.acra.sender.HttpSender

class MainApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val builder = CoreConfigurationBuilder(this)
        builder.withBuildConfigClass(BuildConfig::class.java)
                .withReportFormat(StringFormat.JSON)
        val plugin = builder.getPluginConfigurationBuilder(HttpSenderConfigurationBuilder::class.java)
                .withUri("http://acrarium.rivchain.org/acrarium-1.4.6/report")
                .withEnabled(true)
        plugin.basicAuthLogin = "KOF7CEnt5tfTqIhj"
        plugin.basicAuthPassword = "F4cCIqo9EjpihcPt"
        plugin.httpMethod = HttpSender.Method.POST
        val dialog = DialogConfigurationBuilder(this)
        dialog.withText("Sorry, the application crashed.")
        dialog.withTitle("CupLink")
        dialog.withPositiveButtonText(base.getText(R.string.ok).toString())
        dialog.withNegativeButtonText(base.getText(R.string.cancel).toString())
        dialog.commentPrompt = "Please describe what were you doing when the app crashed:"
        dialog.withResIcon(R.drawable.ic_dialog_alert)
        init(this, builder)
    }
}