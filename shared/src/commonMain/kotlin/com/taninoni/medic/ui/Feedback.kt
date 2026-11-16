package com.taninoni.medic.ui

import androidx.compose.runtime.compositionLocalOf
import com.taninoni.medic.Sound
import com.taninoni.medic.res.Res

val LocalSoundOn = compositionLocalOf { true }

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

    fun play(sfx: Sfx) {
        cache[sfx]?.let { runCatching { Sound.clip(it) } }
    }
}
