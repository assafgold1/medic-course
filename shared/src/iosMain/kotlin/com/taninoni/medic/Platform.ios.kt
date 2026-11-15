package com.taninoni.medic

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create


import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970


actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual object Sound {
    actual fun beat(accent: Boolean) = AudioServicesPlaySystemSound(if (accent) 1103u else 1104u)
    actual fun breath() = AudioServicesPlaySystemSound(1057u)
    actual fun alarm() = AudioServicesPlaySystemSound(1005u)

    private val players = mutableMapOf<Int, platform.AVFAudio.AVAudioPlayer>()
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
    actual fun clip(wav: ByteArray) {
        val player = players.getOrPut(wav.contentHashCode()) {
            // Mix with other audio and respect the silent switch.
            runCatching { platform.AVFAudio.AVAudioSession.sharedInstance().setCategory(platform.AVFAudio.AVAudioSessionCategoryAmbient, null) }
            val data: NSData = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
            platform.AVFAudio.AVAudioPlayer(data = data, error = null).also { it.prepareToPlay() }
        }
        player.currentTime = 0.0
        player.play()
    }
}

actual fun pinnedHttpClient(config: io.ktor.client.HttpClientConfig<*>.() -> Unit): io.ktor.client.HttpClient =
    io.ktor.client.HttpClient(io.ktor.client.engine.darwin.Darwin) { config() }
