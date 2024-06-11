package org.rivchain.cuplink

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.appcompat.content.res.AppCompatResources
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.toBitmap
import org.rivchain.cuplink.call.RTCPeerConnection
import org.rivchain.cuplink.model.Contact
import org.rivchain.cuplink.model.Event
import org.rivchain.cuplink.util.Log
import org.rivchain.cuplink.util.RlpUtils
import java.util.Date

class CallService : Service() {

    private var callServiceReceiver: CallServiceReceiver? = null
    private var screenReceiver: ScreenReceiver? = null

    private lateinit var vibrator: Vibrator
    private lateinit var ringtone: Ringtone

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bldr =
                Notification.Builder(this, OTHER_NOTIFICATIONS_CHANNEL.toString())
                    .setContentTitle(
                        getString(
                            R.string.is_calling
                        )
                    )
                    .setShowWhen(false)
            bldr.setSmallIcon(R.drawable.ic_audio_device_phone)

            val channel = NotificationChannel(
                OTHER_NOTIFICATIONS_CHANNEL.toString(),
                "CupLink",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "CupLink calls channel for foreground service notification"
            val n = bldr.build()
            val notificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            startForeground(ID_ONGOING_CALL_NOTIFICATION, n)
        }
        initRinging()
    }

    private fun initRinging() {
        Log.d(this, "initRinging")

        // init ringtone
        ringtone = RingtoneManager.getRingtone(
            this,
            RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext,
                RingtoneManager.TYPE_RINGTONE
            )
        )

        // init vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun startRinging() {
        Log.d(this, "startRinging()")
        val ringerMode = (getSystemService(AUDIO_SERVICE) as AudioManager).ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }

        val pattern = longArrayOf(1500, 800, 800, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibe = VibrationEffect.createWaveform(pattern, 1)
            vibrator.vibrate(vibe)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 1)
        }

        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            return
        }
        ringtone.play()
    }

    private fun stopRinging() {
        Log.d(this, "stopRinging()")
        vibrator.cancel()
        ringtone.stop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent == null) {
            return super.onStartCommand(intent, flags, startId)
        }
        val contact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(SERVICE_CONTACT_KEY, Contact::class.java)
        } else {
            intent.getSerializableExtra(SERVICE_CONTACT_KEY)
        } as Contact
        callServiceReceiver = CallServiceReceiver(contact)
        val intentFilter = IntentFilter()
        intentFilter.addAction(START_CALL_ACTION)
        intentFilter.addAction(STOP_CALL_ACTION)
        intentFilter.addAction(DECLINE_CALL_ACTION)

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)

        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_USER_PRESENT)
        screenReceiver = ScreenReceiver(contact as Contact)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(callServiceReceiver, intentFilter, RECEIVER_EXPORTED)
            registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(callServiceReceiver, intentFilter)
            registerReceiver(screenReceiver, filter)
        }

        // check screen lock status and run showIncomingNotification when screen is unlocked. check an existing notification.
        showIncomingNotification(intent, contact, this@CallService)
        Thread {
            RTCPeerConnection.incomingRTCCall?.continueOnIncomingSocket()
        }.start()
        startRinging()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun showIncomingNotification(
        intent: Intent,
        contact: Contact,
        service: CallService,
    ) {

        val builder = NotificationCompat.Builder(service)
            .setContentTitle(
                service.getString(R.string.is_calling)
            )
            .setSmallIcon(R.drawable.ic_call_accept)
            .setContentIntent(
                PendingIntent.getActivity(
                    service,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nprefs: SharedPreferences = service.application.getSharedPreferences("Notifications", Activity.MODE_PRIVATE)
            var chanIndex = nprefs.getInt("calls_notification_channel", 0)
            val nm = service.getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
            var oldChannel = nm!!.getNotificationChannel("incoming_calls2$chanIndex")
            if (oldChannel != null) {
                nm.deleteNotificationChannel(oldChannel.id)
            }
            oldChannel = nm.getNotificationChannel("incoming_calls3$chanIndex")
            if (oldChannel != null) {
                nm.deleteNotificationChannel(oldChannel.id)
            }
            val existingChannel = nm.getNotificationChannel("incoming_calls4$chanIndex")
            var needCreate = true
            if (existingChannel != null) {
                if (existingChannel.importance < NotificationManager.IMPORTANCE_HIGH || existingChannel.sound != null) {
                    Log.d(this, "User messed up the notification channel; deleting it and creating a proper one")
                    nm.deleteNotificationChannel("incoming_calls4$chanIndex")
                    chanIndex++
                    nprefs.edit().putInt("calls_notification_channel", chanIndex).apply()
                } else {
                    needCreate = false
                }
            }
            if (needCreate) {
                val attrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setLegacyStreamType(AudioManager.STREAM_RING)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
                val chan = NotificationChannel(
                    "incoming_calls4$chanIndex",
                    service.getString(
                        R.string.call_ringing
                    ),
                    NotificationManager.IMPORTANCE_HIGH
                )
                try {
                    chan.setSound(null, attrs)
                } catch (e: java.lang.Exception) {
                    Log.e(this, e.toString())
                }
                chan.description = service.getString(
                    R.string.call_ringing
                )
                chan.enableVibration(false)
                chan.enableLights(false)
                chan.setBypassDnd(true)
                try {
                    nm.createNotificationChannel(chan)
                } catch (e: java.lang.Exception) {
                    Log.e(this, e.toString())
                    this.stopSelf()
                    return
                }
            }
            builder.setChannelId("incoming_calls4$chanIndex")
        } else {
            builder.setSound(null)
        }
        var endTitle: CharSequence =
            service.getString(R.string.call_denied)
        var answerTitle: CharSequence =
            service.getString(R.string.call_connected)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            endTitle = SpannableString(endTitle)
            endTitle.setSpan(ForegroundColorSpan(-0xbbcca), 0, endTitle.length, 0)
            answerTitle = SpannableString(answerTitle)
            answerTitle.setSpan(ForegroundColorSpan(-0xff5600), 0, answerTitle.length, 0)
        }

        val flag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        val endPendingIntent = PendingIntent.getBroadcast(
            service,
            0,
            Intent().apply {
                action = CallService.STOP_CALL_ACTION
            },
            flag
        )

        val answerPendingIntent = PendingIntent.getActivity(
            this@CallService,
            0,
            Intent(
                this@CallService,
                CallActivity::class.java
            ).setAction("ANSWER_INCOMING_CALL").putExtra("EXTRA_CONTACT", contact),
            PendingIntent.FLAG_IMMUTABLE
        )

        builder.setPriority(NotificationCompat.PRIORITY_MAX)
        //.setWhen(0)
        .setOngoing(true)
        //.setShowWhen(false)
        .setColor(-0xd35a20)
        .setVibrate(LongArray(0))
        .setCategory(Notification.CATEGORY_CALL)
        .setFullScreenIntent(
            PendingIntent.getActivity(
                service,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            ), true
        )
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val incomingNotification: Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val avatar: Bitmap? = AppCompatResources.getDrawable(service, R.drawable.ic_contacts)?.toBitmap()
            var personName: String = contact.name
            if (TextUtils.isEmpty(personName)) {
                //java.lang.IllegalArgumentException: person must have a non-empty a name
                personName = "Unknown contact"
            }
            val person: Person.Builder = Person.Builder()
                .setImportant(true)
                .setName(personName)
            //.setIcon(Icon.createWithAdaptiveBitmap(avatar)).build()
            val notificationStyle =
                NotificationCompat.CallStyle.forIncomingCall(person.build(), endPendingIntent, answerPendingIntent)

            builder.setStyle(notificationStyle)
            incomingNotification = builder.build()
        } else {
            builder.addAction(R.drawable.ic_close, endTitle, endPendingIntent)
            builder.addAction(R.drawable.ic_audio_device_phone, answerTitle, answerPendingIntent)
            builder.setContentText(contact.name)

            val customView = RemoteViews(
                service.packageName,
                R.layout.notification_call_rtl
            )
            customView.setTextViewText(R.id.name, contact.name)
            customView.setViewVisibility(R.id.subtitle, View.GONE)
            customView.setTextViewText(
                R.id.title,
                contact.name,
            )

            val avatar: Bitmap? = AppCompatResources.getDrawable(service, R.drawable.ic_contacts)?.toBitmap()
            customView.setTextViewText(
                R.id.answer_text,
                service.getString(R.string.call_connected)
            )
            customView.setTextViewText(
                R.id.decline_text,
                service.getString(R.string.button_abort)
            )
            //customView.setImageViewBitmap(R.id.photo, avatar)
            customView.setOnClickPendingIntent(R.id.answer_btn, answerPendingIntent)
            customView.setOnClickPendingIntent(R.id.decline_btn, endPendingIntent)
            //builder.setLargeIcon(avatar)
            incomingNotification = builder.build()
            incomingNotification.bigContentView = customView
            incomingNotification.headsUpContentView = incomingNotification.bigContentView
        }
        incomingNotification.flags = incomingNotification.flags or (Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT)
        if(contact.name.isEmpty()){
            contact.name = "Unknown caller"
            contact.addresses = arrayListOf(contact.lastWorkingAddress!!.address.toString())
        }
        val answerCarPendingIntent = CarPendingIntent.getCarApp(
            applicationContext,
            System.currentTimeMillis().toInt(),
            Intent(Intent.ACTION_ANSWER)
                .setComponent(ComponentName(this, CarService::class.java))
                .setData(Uri.parse(RlpUtils.generateLink(contact))),
            PendingIntent.FLAG_IMMUTABLE
        )

        builder.extend(CarAppExtender.Builder()
            .setLargeIcon(AppCompatResources.getDrawable(service, R.drawable.cup_link)!!.toBitmap())
            .setImportance(NotificationManager.IMPORTANCE_HIGH)
            .setSmallIcon(R.drawable.dialog_rounded_corner)
            .addAction(R.drawable.ic_audio_device_phone, answerTitle, answerCarPendingIntent)
            .addAction(R.drawable.ic_close, endTitle, endPendingIntent)
            .build())

        service.startForeground(ID_ONGOING_CALL_NOTIFICATION, incomingNotification)
        CarNotificationManager.from(service).notify(ID_ONGOING_CALL_NOTIFICATION, builder)
    }

    override fun onDestroy() {
        this.unregisterReceiver(callServiceReceiver)
        this.unregisterReceiver(screenReceiver)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager?)?.cancel(ID_ONGOING_CALL_NOTIFICATION)
        super.onDestroy()
    }

    override fun onBind(arg0: Intent?): IBinder? {
        return null
    }

    inner class CallServiceReceiver(private val contact: Contact) : BroadcastReceiver() {
        override fun onReceive(arg0: Context?, intent: Intent) {
            Log.d(this, "onReceive() action=$intent.action")
            when (intent.action) {
                START_CALL_ACTION -> {
                    // Do nothing
                }
                STOP_CALL_ACTION -> RTCPeerConnection.incomingRTCCall?.decline()
                DECLINE_CALL_ACTION -> {
                    // Notify missed call
                    val event = Event(contact.publicKey, contact.lastWorkingAddress, Event.Type.INCOMING_MISSED, Date())
                    RTCPeerConnection.incomingRTCCall?.service!!.addEvent(event)
                }
                else -> {
                    // For all other actions, do nothing
                }
            }
            stopRinging()
            stopSelf()
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager?)?.cancel(ID_ONGOING_CALL_NOTIFICATION)
        }
    }

    inner class ScreenReceiver(private var contact: Contact) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(this, "In Method: ACTION_SCREEN_OFF")
                    // onPause() will be called.
                    // Stop ringing when screen switched off
                    stopRinging()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(this, "In Method: ACTION_SCREEN_ON")
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(this, "In Method: ACTION_USER_PRESENT")
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager?)?.cancelAll()
                    showIncomingNotification(intent, contact, this@CallService)
                }
            }
        }
    }

    companion object {
        const val START_CALL_ACTION: String = "StartCall"
        const val STOP_CALL_ACTION: String = "StopCall"
        const val DECLINE_CALL_ACTION: String = "DeclineCall"
        const val SERVICE_CONTACT_KEY: String = "ServiceContactKey"
        private const val ID_ONGOING_CALL_NOTIFICATION = 201

        var OTHER_NOTIFICATIONS_CHANNEL = 99
    }
}