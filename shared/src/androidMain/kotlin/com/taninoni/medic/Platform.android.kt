package com.taninoni.medic

import android.media.AudioManager
import android.media.ToneGenerator

actual fun nowMillis(): Long = System.currentTimeMillis()

actual object Sound {
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    actual fun beat(accent: Boolean) { tone?.startTone(if (accent) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 60) }
    actual fun breath() { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 400) }
    actual fun alarm() { tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700) }

    private val tracks = mutableMapOf<Int, android.media.AudioTrack>()
    actual fun clip(wav: ByteArray) {
        runCatching {
            val track = tracks.getOrPut(wav.contentHashCode()) {
                val pcm = wav.copyOfRange(44, wav.size)
                val rate = (wav[24].toInt() and 0xff) or ((wav[25].toInt() and 0xff) shl 8) or ((wav[26].toInt() and 0xff) shl 16)
                android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_GAME).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size).build()
                    .also { it.write(pcm, 0, pcm.size) }
            }
            track.stop(); track.reloadStaticData(); track.play()
        }
    }
}

/** Platform HTTP client. HTTPS only (res/xml/network_security.xml). */
actual fun pinnedHttpClient(config: io.ktor.client.HttpClientConfig<*>.() -> Unit): io.ktor.client.HttpClient =
    io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) { config() }

/** Set by MainActivity so platform services that need a Context (text-to-speech) can reach it. */
object AndroidContext {
    var app: android.content.Context? = null
    /** The running activity, for permission prompts. Cleared when it is destroyed. */
    var activity: android.app.Activity? = null
    /** Waiting for the notification permission answer (see MainActivity.onRequestPermissionsResult). */
    var permissionCallback: ((Boolean) -> Unit)? = null
    const val NOTIFY_PERMISSION_REQUEST = 7301
}

actual object Speech {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private fun engine(): android.speech.tts.TextToSpeech? {
        val ctx = AndroidContext.app ?: return null
        return tts ?: android.speech.tts.TextToSpeech(ctx) { status ->
            ready = status == android.speech.tts.TextToSpeech.SUCCESS
            if (ready) { tts?.language = java.util.Locale("he", "IL"); pending?.let { say(it) }; pending = null }
        }.also { tts = it }
    }
    actual fun say(text: String) {
        val e = engine() ?: return
        if (!ready) { pending = text; return }
        e.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "aed")
    }
    actual fun stop() { tts?.stop() }
}

actual fun utcOffsetMinutes(): Int = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

actual object Reminders {
    private val ids = (100..106).toList() + 200
    private const val NOTIFY = "android.permission.POST_NOTIFICATIONS"

    actual fun requestPermission(onResult: (Boolean) -> Unit) {
        val ctx = AndroidContext.app ?: return onResult(false)
        if (android.os.Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(NOTIFY) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return onResult(true)
        val activity = AndroidContext.activity ?: return onResult(false)
        AndroidContext.permissionCallback = onResult
        activity.requestPermissions(arrayOf(NOTIFY), AndroidContext.NOTIFY_PERMISSION_REQUEST)
    }

    actual fun schedule(items: List<ReminderItem>) {
        val ctx = AndroidContext.app ?: return
        val alarms = ctx.getSystemService(android.app.AlarmManager::class.java) ?: return
        ids.forEach { alarms.cancel(pending(ctx, it, null)) }
        items.forEach { alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, it.atMillis, pending(ctx, it.id, it)) }
    }

    private fun pending(ctx: android.content.Context, id: Int, item: ReminderItem?): android.app.PendingIntent {
        val intent = android.content.Intent(ctx, ReminderReceiver::class.java)
        if (item != null) intent.putExtra("id", item.id).putExtra("title", item.title).putExtra("body", item.body)
        return android.app.PendingIntent.getBroadcast(
            ctx, id, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Posts a practice reminder planned by [Reminders]. Declared in the app manifest. */
class ReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            android.app.NotificationChannel("streak", "תזכורת תרגול יומית", android.app.NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val tap = open?.let { android.app.PendingIntent.getActivity(ctx, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE) }
        val body = intent.getStringExtra("body").orEmpty()
        val n = android.app.Notification.Builder(ctx, "streak")
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle(intent.getStringExtra("title").orEmpty())
            .setContentText(body)
            .setStyle(android.app.Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .apply { if (tap != null) setContentIntent(tap) }
            .build()
        nm.notify(intent.getIntExtra("id", 100), n)
    }
}
