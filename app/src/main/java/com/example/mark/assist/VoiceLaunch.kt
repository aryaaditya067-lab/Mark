package com.example.mark.assist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Open voice mode now" requests from outside the UI (the assist gesture, the
 * launcher shortcut). Kept until the main screen exists to act on it, which
 * may be after the splash and sign-in screens.
 */
object VoiceLaunch {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() { _pending.value = true }

    /** @return true if there was a request to act on. */
    fun consume(): Boolean = _pending.compareAndSet(true, false)
}
