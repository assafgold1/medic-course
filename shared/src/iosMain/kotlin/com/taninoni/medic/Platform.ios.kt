package com.taninoni.medic


import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970


actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual object Sound {
    actual fun beat(accent: Boolean) = AudioServicesPlaySystemSound(if (accent) 1103u else 1104u)
    actual fun breath() = AudioServicesPlaySystemSound(1057u)
    actual fun alarm() = AudioServicesPlaySystemSound(1005u)
}

actual fun pinnedHttpClient(config: io.ktor.client.HttpClientConfig<*>.() -> Unit): io.ktor.client.HttpClient =
    io.ktor.client.HttpClient(io.ktor.client.engine.darwin.Darwin) { config() }
