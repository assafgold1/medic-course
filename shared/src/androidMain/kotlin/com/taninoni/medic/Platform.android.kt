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
object AndroidContext { var app: android.content.Context? = null }

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
