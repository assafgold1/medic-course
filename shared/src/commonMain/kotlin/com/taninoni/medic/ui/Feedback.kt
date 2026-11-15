package com.taninoni.medic.ui

import com.taninoni.medic.Sound
import com.taninoni.medic.res.Res

/** Success / error sounds after answering a question (loaded once from compose resources). */
object AnswerSound {
    private var correct: ByteArray? = null
    private var wrong: ByteArray? = null

    suspend fun preload() {
        if (correct == null) correct = runCatching { Res.readBytes("files/sounds/correct.wav") }.getOrNull()
        if (wrong == null) wrong = runCatching { Res.readBytes("files/sounds/wrong.wav") }.getOrNull()
    }

    fun play(ok: Boolean) { (if (ok) correct else wrong)?.let { runCatching { Sound.clip(it) } } }
}
