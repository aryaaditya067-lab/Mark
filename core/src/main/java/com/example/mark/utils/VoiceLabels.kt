package com.example.mark.utils

/**
 * Android exposes voices as opaque ids like "en-in-x-ena-local". The middle
 * token is a per-engine timbre code, not a documented gender field — these
 * labels are best-effort, verified by listening rather than by spec.
 */
object VoiceLabels {

    private val known = mapOf(
        "en-gb-x-gbb" to "British, male",
        "en-gb-x-gbd" to "British, male (deeper)",
        "en-gb-x-rjs" to "British, male (regional)",
        "en-gb-x-gba" to "British, female",
        "en-gb-x-gbc" to "British, female (soft)",
        "en-gb-x-gbg" to "British, female",
        "en-in-x-ena" to "Indian, female",
        "en-in-x-end" to "Indian, male",
        "en-us-x-iog" to "American, male",
        "en-us-x-iol" to "American, male (deep)",
        "en-us-x-tpc" to "American, male (warm)",
        "en-us-x-tpd" to "American, male",
        "en-us-x-tpf" to "American, female"
    )

    private val accents = mapOf(
        "en-gb" to "British", "en-us" to "American", "en-in" to "Indian",
        "en-au" to "Australian", "en-ng" to "Nigerian", "en-ca" to "Canadian"
    )

    fun label(voiceName: String): String {
        val online = voiceName.endsWith("-network")
        val base = voiceName.removeSuffix("-local").removeSuffix("-network")
        val name = known[base]
            ?: accents[base.take(5)]?.let { "$it, voice ${base.substringAfterLast('-')}" }
            ?: return voiceName
        return if (online) "$name (online, more natural)" else name
    }
}
