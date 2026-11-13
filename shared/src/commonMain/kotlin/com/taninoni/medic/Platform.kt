package com.taninoni.medic

/**
 * The server address. Fixed in code; users can't change it in the app.
 * localhost works on the iOS simulator, and on Android after `adb reverse tcp:8787 tcp:8787`.
 * Change it here to the real server address before a release.
 */
const val SERVER_URL = "http://localhost:8787"

expect fun nowMillis(): Long

/** Short sounds for the CPR metronome. */
expect object Sound {
    fun beat(accent: Boolean)
    fun breath()
    fun alarm()
}
