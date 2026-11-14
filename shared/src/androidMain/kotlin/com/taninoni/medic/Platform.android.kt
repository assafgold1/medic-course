package com.taninoni.medic

import android.media.AudioManager
import android.media.ToneGenerator

actual fun nowMillis(): Long = System.currentTimeMillis()

actual object Sound {
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    actual fun beat(accent: Boolean) { tone?.startTone(if (accent) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 60) }
    actual fun breath() { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 400) }
    actual fun alarm() { tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700) }
}

/** Platform HTTP client. HTTPS only (res/xml/network_security.xml). */
actual fun pinnedHttpClient(config: io.ktor.client.HttpClientConfig<*>.() -> Unit): io.ktor.client.HttpClient =
    io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) { config() }
