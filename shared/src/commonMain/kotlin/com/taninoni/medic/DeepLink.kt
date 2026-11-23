package com.taninoni.medic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Links into the app from outside it (the home screen widget). The app handles [pending] once the user
 * is signed in and content is loaded, then clears it.
 *
 *  medic://quick   five quick questions
 *  medic://review  today's spaced review
 */
object DeepLink {
    var pending by mutableStateOf<String?>(null)
    fun open(url: String) { pending = url.substringAfter("://").substringBefore('?').trimEnd('/') }
}
