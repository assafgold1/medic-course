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
