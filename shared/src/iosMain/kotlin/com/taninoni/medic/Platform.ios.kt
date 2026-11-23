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

    private var voicePlayer: platform.AVFAudio.AVAudioPlayer? = null
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
    actual fun voice(wav: ByteArray) {
        voicePlayer?.stop()
        // A device prompt must be heard: play even with the silent switch on, like the spoken prompts did.
        runCatching { platform.AVFAudio.AVAudioSession.sharedInstance().setCategory(platform.AVFAudio.AVAudioSessionCategoryPlayback, null) }
        val data: NSData = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
        voicePlayer = platform.AVFAudio.AVAudioPlayer(data = data, error = null).also { it.prepareToPlay(); it.play() }
    }
    actual fun stopVoice() { voicePlayer?.stop(); voicePlayer = null }
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

actual object Haptics {
    actual fun light() = platform.UIKit.UIImpactFeedbackGenerator(platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
    actual fun heavy() = platform.UIKit.UIImpactFeedbackGenerator(platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred()
    private fun notify(type: platform.UIKit.UINotificationFeedbackType) = platform.UIKit.UINotificationFeedbackGenerator().notificationOccurred(type)
    actual fun success() = notify(platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    actual fun warning() = notify(platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
    actual fun error() = notify(platform.UIKit.UINotificationFeedbackType.UINotificationFeedbackTypeError)
}

/** Writes the widget's numbers to the App Group the widget extension reads. The app reloads the widget when it goes to the background. */
actual object WidgetBridge {
    private val store by lazy { platform.Foundation.NSUserDefaults(suiteName = "group.com.taninoni.medic") }
    private var last: WidgetData? = null
    actual fun publish(data: WidgetData) {
        if (data == last) return
        last = data
        store.setInteger(data.streak.toLong(), "streak")
        store.setBool(data.doneToday, "doneToday")
        store.setInteger(data.todayCount.toLong(), "todayCount")
        store.setInteger(data.goal.toLong(), "goal")
        store.setInteger(data.reviewDue.toLong(), "reviewDue")
        store.setInteger(data.day, "day")
        store.setBool(true, "ready")
    }
}

actual fun shareText(text: String) {
    val root = platform.UIKit.UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    var top = root
    while (top.presentedViewController != null) top = top.presentedViewController!!
    val sheet = platform.UIKit.UIActivityViewController(listOf(text), null)
    top.presentViewController(sheet, animated = true, completion = null)
}
