package com.example.mark.utils

/**
 * Android exposes voices as opaque ids like "en-in-x-ena-local". The middle
 * token is a per-engine timbre code, not a documented gender field — these
 * labels are best-effort, verified by listening rather than by spec.
 */
object VoiceLabels {

    private val known = mapOf(
        "en-in-x-ena-local" to "Indian, female",
        "en-in-x-end-local" to "Indian, male",
        "en-us-x-iog-local" to "American, male",
        "en-us-x-iol-local" to "American, male (deep)",
        "en-us-x-tpc-local" to "American, male (warm)",
        "en-us-x-tpd-local" to "American, male",
        "en-us-x-tpf-local" to "American, female"
    )

    fun label(voiceName: String): String = known[voiceName] ?: voiceName
}
