package org.rivchain.cuplink;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.HttpSenderConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;

public class MainApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder(this);
        builder.withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON);
        HttpSenderConfigurationBuilder plugin = builder.getPluginConfigurationBuilder(HttpSenderConfigurationBuilder.class)
                .withUri("http://acrarium.rivchain.org/acrarium-1.4.6/report")
                .withEnabled(true);
        plugin.setBasicAuthLogin("KOF7CEnt5tfTqIhj");
        plugin.setBasicAuthPassword("F4cCIqo9EjpihcPt");
        plugin.setHttpMethod(HttpSender.Method.POST);
        DialogConfigurationBuilder dialog = new DialogConfigurationBuilder(this);
        dialog.withText("Sorry, the application crashed.");
        dialog.withTitle("CupLink");
        dialog.withPositiveButtonText(base.getText(android.R.string.ok).toString());
        dialog.withNegativeButtonText(base.getText(android.R.string.cancel).toString());
        dialog.setCommentPrompt("Please describe what were you doing when the app crashed:");
        dialog.withResIcon(android.R.drawable.ic_dialog_alert);
        ACRA.init(this, builder);
    }
}