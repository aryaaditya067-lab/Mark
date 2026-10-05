package com.example.mark.assistant

import com.example.mark.router.Intent
import com.example.mark.router.IntentType

/**
 * The question Mark asks before a parked action runs, built from the action's
 * real parameters. The model's own wording is not trusted for this: text it
 * read from a notification or calendar invite could otherwise dress up
 * "send this to a stranger" as "shall I mark these as read?".
 */
object ConfirmationText {

    fun question(intent: Intent): String {
        val p = intent.params
        return when (intent.type) {
            IntentType.SMS_EXECUTE -> {
                val via = if (p["app"].equals("whatsapp", ignoreCase = true)) " on WhatsApp" else ""
                "Shall I send \"${p["body"].orEmpty().take(120)}\" to ${p["number"].orEmpty()}$via? Say yes or no."
            }
            IntentType.CALL_EXECUTE -> "Shall I call ${p["number"].orEmpty()}? Say yes or no."
            IntentType.REPLY_MESSAGE ->
                "Shall I reply \"${p["text"].orEmpty().take(120)}\" to ${p["contact"].orEmpty()}? Say yes or no."
            IntentType.LAPTOP_CONTROL -> {
                val detail = (p["text"] ?: p["query"] ?: p["app"])?.let { " ($it)" }.orEmpty()
                "Shall I run ${p["action"].orEmpty().replace('_', ' ')} on the laptop$detail? Say yes or no."
            }
            IntentType.FORGET_FACT -> "Shall I forget what I remembered about \"${p["query"].orEmpty()}\"? Say yes or no."
            else -> "Shall I go ahead with ${intent.type.name.lowercase().replace('_', ' ')}? Say yes or no."
        }
    }
}
