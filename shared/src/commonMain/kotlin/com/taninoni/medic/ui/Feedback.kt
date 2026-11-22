package com.taninoni.medic.ui

import androidx.compose.runtime.compositionLocalOf
import com.taninoni.medic.Haptics
import com.taninoni.medic.Sound
import com.taninoni.medic.res.Res

val LocalSoundOn = compositionLocalOf { true }

/** The user's sound and vibration choices, set by the app from the settings. */
object FeedbackPrefs {
    var sound = true
    var haptics = true
}

/** A light tap of feedback for selections: tabs, segmented controls, switches. */
fun tick() { if (FeedbackPrefs.haptics) runCatching { Haptics.light() } }

enum class Sfx(val file: String) {
    CORRECT("correct"),
    WRONG("wrong"),
    STREAK("streak"),
    FLIP("flip"),
    PASS("pass"),
    FAIL("fail"),
    TAP("tap"),
    TICK("tick"),
}

/** Sound effects loaded once from compose resources and played via [Sound.clip]. */
object AnswerSound {
    private val cache = mutableMapOf<Sfx, ByteArray>()

    suspend fun preload() {
        for (sfx in Sfx.entries) {
            if (sfx !in cache) {
                runCatching { Res.readBytes("files/sounds/${sfx.file}.wav") }.getOrNull()?.let { cache[sfx] = it }
            }
        }
    }

    fun play(ok: Boolean) = play(if (ok) Sfx.CORRECT else Sfx.WRONG)

    /** Plays [sfx] if sound is on, with a matching vibration if vibration is on. */
    fun play(sfx: Sfx) {
        if (FeedbackPrefs.sound) cache[sfx]?.let { runCatching { Sound.clip(it) } }
        if (FeedbackPrefs.haptics) runCatching {
            when (sfx) {
                Sfx.CORRECT, Sfx.PASS -> Haptics.success()
                Sfx.WRONG -> Haptics.error()
                Sfx.FAIL -> Haptics.warning()
                Sfx.STREAK -> { Haptics.success(); Haptics.heavy() }
                Sfx.FLIP, Sfx.TAP, Sfx.TICK -> Haptics.light()
            }
        }
    }
}
