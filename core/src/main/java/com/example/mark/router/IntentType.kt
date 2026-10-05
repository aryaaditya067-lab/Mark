package com.example.mark.router

enum class IntentType {
    GREETING,
    GET_STEPS,
    GET_SLEEP,
    GET_HEART_RATE,
    GET_WEATHER,
    ADD_TASK,
    GET_TASKS,
    COMPLETE_TASK,
    GET_CALENDAR,
    OPEN_APP,
    TOGGLE_FLASHLIGHT,
    SET_VOLUME,
    SET_DND,
    GET_BATTERY,
    RING_PHONE,
    SET_BRIGHTNESS,
    SET_SILENT,
    SET_ROTATE,
    MEDIA_CONTROL,
    TIME_MANAGER,
    SET_ALARM,
    READ_NOTIFICATIONS,
    READ_LAST_MESSAGE,
    CHECK_NEW_MESSAGES,
    UNREAD_COUNT,
    CALL_CONTACT,
    CALL_EXECUTE,
    SEND_SMS,
    SMS_EXECUTE,
    NAVIGATE_TO,
    GET_DISTANCE,
    FIND_NEARBY,
    TAKE_SCREENSHOT,
    END_SESSION,
    CONFIRMATION,
    EASTER_EGG,
    REPEAT,
    UNDO,
    TIMER_QUERY,
    WATCH_STATUS,
    HELP,
    GO_HOME,
    CONTEXT_FOLLOW_UP,
    LAPTOP_CONTROL,
    SET_REMINDER,
    LIST_REMINDERS,
    CANCEL_REMINDER,
    READ_CONVERSATION,
    REPLY_MESSAGE
}

enum class ReplyMode { SILENT_CONFIRM, SPEAK }

fun IntentType.replyMode(params: Map<String, String> = emptyMap()): ReplyMode = when (this) {
    IntentType.TOGGLE_FLASHLIGHT,
    IntentType.SET_BRIGHTNESS,
    IntentType.SET_VOLUME,
    IntentType.SET_DND,
    IntentType.SET_SILENT,
    IntentType.SET_ROTATE,
    IntentType.MEDIA_CONTROL,
    IntentType.SET_ALARM,
    IntentType.OPEN_APP,
    IntentType.TAKE_SCREENSHOT,
    IntentType.GO_HOME,
    IntentType.LAPTOP_CONTROL -> ReplyMode.SILENT_CONFIRM
    
    IntentType.TIME_MANAGER -> {
        if (params["action"] == "time" || params["action"] == "date") ReplyMode.SPEAK
        else ReplyMode.SILENT_CONFIRM
    }

    else -> ReplyMode.SPEAK
}
