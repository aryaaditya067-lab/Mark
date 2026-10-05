package com.example.mark.utils

import com.example.mark.core.BuildConfig

object Constants {
    /** Set MIMO_API_KEY in local.properties; see README. */
    val MIMO_API_KEY: String = BuildConfig.MIMO_API_KEY

    /** Xiaomi MiMo, OpenAI-compatible endpoint. */
    const val MIMO_BASE_URL = "https://api.xiaomimimo.com/v1/"

    /** Set WEATHER_API_KEY in local.properties; see README. */
    val WEATHER_API_KEY: String = BuildConfig.WEATHER_API_KEY
    const val WEATHER_BASE_URL = "https://api.openweathermap.org/"

    const val SYSTEM_PROMPT = "You are Mark, a personal AI assistant. " +
            "You can manage the user's tasks and set alarms using the provided tools. " +
            "When the user asks you to remember something or add a to-do, use add_task. " +
            "When they ask what's pending, use get_tasks. " +
            "When they say something is done, use complete_task. " +
            "When they ask to be woken or alerted at a time, use set_alarm with 24-hour time. " +
            "When the user asks about the weather, use get_weather. " +
            "When the user explicitly asks how they are doing, how things are going, or " +
            "for a check-in — a greeting alone is not a check-in — call several tools at " +
            "once and weave the results into one short, natural answer. " +
            "Be concise. Always reply in English."
    const val MAX_HISTORY_MESSAGES = 8

    /** Override with MIMO_MODEL in local.properties. */
    val MIMO_MODEL: String = BuildConfig.MIMO_MODEL

    const val WEB_CLIENT_ID = "541775760070-6gei0p5rdi93sjvja380vup59p0fvc34.apps.googleusercontent.com"
}