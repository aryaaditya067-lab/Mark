package com.example.mark.assistant

import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType

/**
 * Schemas of the tools that only exist on the phone (notifications, calls,
 * messages, navigation...). One source of truth: the phone's tools use these
 * definitions, and the watch registers them as [RemoteTool]s so its LLM can
 * use them too; the controller then carries each call to the phone.
 */
object PhoneToolSchemas {

    val READ_NOTIFICATIONS = def("read_notifications", "Summarize and read the user's recent phone notifications.")
    val READ_LAST_MESSAGE = def("read_last_message", "Read the most recent message from a messaging app on the phone.")
    val CHECK_NEW_MESSAGES = def("check_new_messages", "Count unread messaging notifications on the phone.")
    val UNREAD_COUNT = def("unread_count", "Total count of unread notifications on the phone.")

    val NAVIGATE_TO = def(
        "navigate_to", "Start turn-by-turn navigation on the phone to a destination.",
        "destination" to "The destination address or place name, or 'home' / 'work'", required = listOf("destination")
    )
    val GET_DISTANCE = def(
        "get_distance", "Check the distance or travel time to a destination.",
        "destination" to "The destination address or place name, or 'home' / 'work'", required = listOf("destination")
    )
    val FIND_NEARBY = def(
        "find_nearby", "Find nearby places like ATMs, restaurants or petrol pumps on the phone's map.",
        "placeType" to "The type of place to find (e.g., ATM, restaurant)", required = listOf("placeType")
    )

    val TAKE_SCREENSHOT = def("take_screenshot", "Take a screenshot of the phone screen.")
    val GO_HOME = def("go_home", "Go to the phone's home screen (close the current app).")

    val CALL_CONTACT = def(
        "call_contact",
        "Look up a contact to call. Returns the matched name and number (or candidates when ambiguous). " +
            "Then call call_execute with that number; the user will be asked to confirm.",
        "contact" to "Name of the person to call", required = listOf("contact")
    )
    val CALL_EXECUTE = def(
        "call_execute", "Dial a phone number. Only after call_contact found it; needs the user's spoken confirmation.",
        "number" to "The phone number to dial", required = listOf("number")
    )
    val SEND_SMS = def(
        "send_sms",
        "Look up the recipient of a text message. Returns the matched name and number. " +
            "Then call sms_execute; the user will be asked to confirm.",
        "contact" to "Recipient name", "message_body" to "The message text", required = listOf("contact")
    )
    val SMS_EXECUTE = def(
        "sms_execute", "Send a text message. Only after send_sms found the number; needs the user's spoken confirmation.",
        "number" to "The recipient number", "body" to "The message text", "app" to "sms or whatsapp",
        required = listOf("number", "body")
    )

    /** Schema, offline intent, and whether a spoken yes is required. */
    data class Entry(val definition: FunctionDef, val intent: IntentType, val needsConfirmation: Boolean = false)

    val all: List<Entry> = listOf(
        Entry(READ_NOTIFICATIONS, IntentType.READ_NOTIFICATIONS),
        Entry(READ_LAST_MESSAGE, IntentType.READ_LAST_MESSAGE),
        Entry(CHECK_NEW_MESSAGES, IntentType.CHECK_NEW_MESSAGES),
        Entry(UNREAD_COUNT, IntentType.UNREAD_COUNT),
        Entry(NAVIGATE_TO, IntentType.NAVIGATE_TO),
        Entry(GET_DISTANCE, IntentType.GET_DISTANCE),
        Entry(FIND_NEARBY, IntentType.FIND_NEARBY),
        Entry(TAKE_SCREENSHOT, IntentType.TAKE_SCREENSHOT),
        Entry(GO_HOME, IntentType.GO_HOME),
        Entry(CALL_CONTACT, IntentType.CALL_CONTACT),
        Entry(CALL_EXECUTE, IntentType.CALL_EXECUTE, needsConfirmation = true),
        Entry(SEND_SMS, IntentType.SEND_SMS),
        Entry(SMS_EXECUTE, IntentType.SMS_EXECUTE, needsConfirmation = true),
    )

    /** Watch side: proxies for every phone tool not already registered locally. */
    fun remoteTools(alreadyRegistered: Set<String>): List<Tool> =
        all.filter { it.definition.name !in alreadyRegistered }
            .map { RemoteTool(it.definition, it.intent, it.needsConfirmation) }

    private fun def(name: String, description: String, vararg params: Pair<String, String>, required: List<String> = emptyList()) =
        FunctionDef(
            name = name,
            description = description,
            parameters = Parameters(
                properties = params.associate { (key, desc) -> key to Property("string", desc) },
                required = required
            )
        )
}

/**
 * A tool that lives on the other device. Registered so the LLM can see and
 * call it; the controller routes the call over the Data Layer by [intent], so
 * [execute] only runs if that routing is unavailable.
 */
class RemoteTool(
    override val definition: FunctionDef,
    override val intent: IntentType,
    private val confirm: Boolean = false,
) : Tool {
    override val name: String = definition.name
    override suspend fun execute(request: ToolRequest): ToolResult =
        ToolResult.Failure("That runs on your phone, which I can't reach right now.", reason = "phone_unreachable")
    override fun needsConfirmation(request: ToolRequest) = confirm
}
