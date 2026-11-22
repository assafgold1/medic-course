package com.taninoni.medic

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create


import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT
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

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object Speech {
    private val synth by lazy { platform.AVFAudio.AVSpeechSynthesizer() }
    actual fun say(text: String) {
        runCatching { platform.AVFAudio.AVAudioSession.sharedInstance().setCategory(platform.AVFAudio.AVAudioSessionCategoryPlayback, null) }
        synth.stopSpeakingAtBoundary(platform.AVFAudio.AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val u = platform.AVFAudio.AVSpeechUtterance.speechUtteranceWithString(text)
        u.voice = platform.AVFAudio.AVSpeechSynthesisVoice.voiceWithLanguage("he-IL")
        u.rate = 0.5f
        synth.speakUtterance(u)
    }
    actual fun stop() { synth.stopSpeakingAtBoundary(platform.AVFAudio.AVSpeechBoundary.AVSpeechBoundaryImmediate) }
}

actual fun utcOffsetMinutes(): Int = (NSTimeZone.localTimeZone.secondsFromGMT / 60).toInt()

actual object Reminders {
    private val ids = (100..106).map { "streak-$it" } + "streak-200"
    private val center get() = platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter()

    actual fun requestPermission(onResult: (Boolean) -> Unit) {
        val options = platform.UserNotifications.UNAuthorizationOptionAlert or
            platform.UserNotifications.UNAuthorizationOptionSound or platform.UserNotifications.UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ -> onResult(granted) }
    }

    actual fun schedule(items: List<ReminderItem>) {
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        val now = nowMillis()
        for (r in items) {
            val seconds = (r.atMillis - now) / 1000.0
            if (seconds < 1) continue
            val content = platform.UserNotifications.UNMutableNotificationContent().apply {
                setTitle(r.title)
                setBody(r.body)
                setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
            }
            val trigger = platform.UserNotifications.UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            val request = platform.UserNotifications.UNNotificationRequest.requestWithIdentifier("streak-${r.id}", content, trigger)
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }
}
