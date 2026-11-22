package com.taninoni.medic

/**
 * The server address. Fixed in code; users can't change it in the app.
 * uh.taninoni.com has a Let's Encrypt certificate (issued on kali via Cloudflare DNS, renews automatically).
 */
const val SERVER_URL = "https://uh.taninoni.com"

/** Platform HTTP client (OkHttp on Android, Darwin on iOS). */
expect fun pinnedHttpClient(config: io.ktor.client.HttpClientConfig<*>.() -> Unit): io.ktor.client.HttpClient

expect fun nowMillis(): Long

/** Short sounds for the CPR metronome. */
expect object Sound {
    fun beat(accent: Boolean)
    fun breath()
    fun alarm()
    /** Plays a short 16-bit mono PCM WAV clip (answer feedback). */
    fun clip(wav: ByteArray)
}

/** Spoken device prompts (Hebrew text-to-speech) for the defibrillator simulator. */
expect object Speech {
    fun say(text: String)
    fun stop()
}

/** The device time zone's current offset from UTC, in minutes (Israel: 120 or 180). */
expect fun utcOffsetMinutes(): Int

/** One local notification: [atMillis] is the epoch time it fires. */
data class ReminderItem(val id: Int, val atMillis: Long, val title: String, val body: String)

/** Daily practice reminders as local notifications. */
expect object Reminders {
    /** Asks for notification permission (once; later calls report the saved answer). May call back off the main thread. */
    fun requestPermission(onResult: (Boolean) -> Unit)
    /** Replaces every reminder this app has pending with [items]. */
    fun schedule(items: List<ReminderItem>)
}
