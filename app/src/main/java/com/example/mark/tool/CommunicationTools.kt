package com.example.mark.tool

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.repository.Contact
import com.example.mark.repository.ContactRepository
import com.example.mark.router.IntentType
import kotlinx.coroutines.delay

abstract class BaseCommunicationTool(protected val context: Context) : Tool {
    protected val contacts = ContactRepository.getInstance(context)

    protected fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    protected suspend fun resolveContact(query: String?): ContactResolution {
        if (query.isNullOrBlank()) return ContactResolution.NotFound
        val matches = contacts.resolve(query)
        
        if (matches.isEmpty()) return ContactResolution.NotFound
        
        val best = matches.first()
        if (matches.size > 1 && (best.confidence - matches[1].confidence) < 0.1f) {
            return ContactResolution.Ambiguous(matches.take(3).map { it.contact })
        }
        
        return if (best.confidence >= 0.85f) {
            ContactResolution.Resolved(best.contact)
        } else {
            ContactResolution.NeedConfirmation(best.contact)
        }
    }

    sealed interface ContactResolution {
        data class Resolved(val contact: Contact) : ContactResolution
        data class NeedConfirmation(val contact: Contact) : ContactResolution
        data class Ambiguous(val candidates: List<Contact>) : ContactResolution
        data object NotFound : ContactResolution
    }
}

/**
 * Phase 1: RESOLVE
 * Returns contact data to the watch immediately.
 */
class CallTool(context: Context) : BaseCommunicationTool(context) {
    override val name = "call_contact"
    override val intent = IntentType.CALL_CONTACT
    override val definition = FunctionDef(
        name = name,
        description = "Resolve a contact name for calling.",
        parameters = Parameters(
            properties = mapOf("contact" to Property("string", "Name of the person to call")),
            required = listOf("contact")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        if (!hasPermission(Manifest.permission.CALL_PHONE) || !hasPermission(Manifest.permission.READ_CONTACTS)) {
            return ToolResult.Failure("I need phone and contacts permission — open Mark on your phone to grant it.", reason = "no_permission")
        }

        val contactName = request.string("contact")
        val resolution = resolveContact(contactName)
        
        return when (resolution) {
            is ContactResolution.Resolved -> {
                ToolResult.Success(
                    text = "resolved",
                    data = mapOf(
                        "status" to "resolved",
                        "name" to resolution.contact.name,
                        "number" to resolution.contact.number,
                        "confidence" to "1.0"
                    )
                )
            }
            is ContactResolution.NeedConfirmation -> {
                ToolResult.Success(
                    text = "confirmation_needed",
                    data = mapOf(
                        "status" to "confirmation_needed",
                        "name" to resolution.contact.name,
                        "number" to resolution.contact.number,
                        "confidence" to "0.7"
                    )
                )
            }
            is ContactResolution.Ambiguous -> {
                ToolResult.Success(
                    text = "ambiguous",
                    data = mapOf(
                        "status" to "ambiguous",
                        "candidates" to resolution.candidates.joinToString("|") { it.name }
                    )
                )
            }
            ContactResolution.NotFound -> {
                ToolResult.Failure("I couldn't find $contactName.", reason = "not_found")
            }
        }
    }
}

/**
 * Phase 2: EXECUTE
 * Dials the number immediately.
 */
class CallExecuteTool(private val context: Context) : Tool {
    override val name = "call_execute"
    override val intent = IntentType.CALL_EXECUTE
    override val definition = FunctionDef(
        name = name,
        description = "Dial a number immediately.",
        parameters = Parameters(
            properties = mapOf("number" to Property("string", "The phone number to dial")),
            required = listOf("number")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val number = request.string("number") ?: return ToolResult.Failure("No number provided.", reason = "missing_arg")
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Connecting...")
        } catch (e: Exception) {
            ToolResult.Failure("Failed to dial.", reason = "system_error")
        }
    }
}

/**
 * Phase 1: RESOLVE
 * Returns recipient data to the watch.
 */
class SmsTool(context: Context) : BaseCommunicationTool(context) {
    override val name = "send_sms"
    override val intent = IntentType.SEND_SMS
    override val definition = FunctionDef(
        name = name,
        description = "Resolve a contact for messaging.",
        parameters = Parameters(
            properties = mapOf(
                "contact" to Property("string", "Recipient name"),
                "message_body" to Property("string", "The message text")
            ),
            required = listOf("contact", "message_body")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        android.util.Log.d("MarkSms", "permission=${ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)}")
        
        if (!hasPermission(Manifest.permission.SEND_SMS) || !hasPermission(Manifest.permission.READ_CONTACTS)) {
            return ToolResult.Failure("I need SMS and contacts permission — open Mark on your phone to grant it.", reason = "no_permission")
        }

        val payload = request.string("payload") ?: ""
        val contactName = request.string("contact") ?: payload.split(" ").firstOrNull() ?: ""
        
        // Extract body from raw input if available to preserve fillers
        val rawInput = request.rawInput ?: ""
        val body = if (rawInput.isNotBlank()) {
            val contactIdx = rawInput.lowercase().indexOf(contactName.lowercase())
            if (contactIdx != -1) {
                rawInput.substring(contactIdx + contactName.length).trim()
            } else {
                request.string("message_body") ?: payload.substringAfter(contactName).trim()
            }
        } else {
            request.string("message_body") ?: payload.substringAfter(contactName).trim()
        }

        if (body.isEmpty()) return ToolResult.Failure("What should the message say?", reason = "missing_arg")

        val resolution = resolveContact(contactName)
        
        return when (resolution) {
            is ContactResolution.Resolved -> {
                val contact = resolution.contact
                android.util.Log.d("MarkSms", "phase=resolve recipient=${contact.name} body='$body'")
                ToolResult.Success(
                    text = "resolved",
                    data = mapOf(
                        "status" to "resolved",
                        "name" to contact.name,
                        "number" to contact.number,
                        "body" to body
                    )
                )
            }
            is ContactResolution.NeedConfirmation -> {
                ToolResult.Success(
                    text = "confirmation_needed",
                    data = mapOf(
                        "status" to "confirmation_needed",
                        "name" to resolution.contact.name,
                        "number" to resolution.contact.number,
                        "body" to body
                    )
                )
            }
            is ContactResolution.Ambiguous -> {
                ToolResult.Success(
                    text = "ambiguous",
                    data = mapOf(
                        "status" to "ambiguous",
                        "candidates" to resolution.candidates.joinToString("|") { it.name }
                    )
                )
            }
            ContactResolution.NotFound -> {
                ToolResult.Failure("I couldn't find $contactName.", reason = "not_found")
            }
        }
    }
}

/**
 * Phase 2: EXECUTE
 * Sends SMS immediately or opens WhatsApp.
 */
class SmsExecuteTool(private val context: Context) : Tool {
    override val name = "sms_execute"
    override val intent = IntentType.SMS_EXECUTE
    override val definition = FunctionDef(
        name = name,
        description = "Send message immediately.",
        parameters = Parameters(
            properties = mapOf(
                "number" to Property("string", "The recipient number"),
                "body" to Property("string", "The message text"),
                "app" to Property("string", "sms or whatsapp")
            ),
            required = listOf("number", "body")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val number = request.string("number") ?: return ToolResult.Failure("No number.", reason = "missing_arg")
        val body = request.string("body") ?: return ToolResult.Failure("No body.", reason = "missing_arg")
        val app = request.string("app")?.lowercase() ?: "sms"

        return if (app == "whatsapp") {
            val cleanNumber = number.filter { it.isDigit() }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(body)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Opening WhatsApp.")
        } else {
            android.util.Log.d("MarkSms", "phase=execute number=$number")
            val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
            smsManager.sendTextMessage(number, null, body, null, null)
            ToolResult.Success("Message sent.")
        }
    }
}
