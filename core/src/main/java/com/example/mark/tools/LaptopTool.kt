package com.example.mark.tools

import android.content.Context
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the Mark laptop agent (mark_agent.py) over the local network.
 *
 * Chain: watch speaks -> phone routes -> this tool -> HTTP -> agent -> Windows.
 *
 * Runs on the PHONE, never on the watch: the watch's network path goes through
 * the phone anyway, so one hop is wasted and the watch would fail whenever it
 * is off WiFi.
 *
 * Reachability is not assumed. The laptop is asleep, off, or on another network
 * most of the day, so every call is short-timeout and failure is a normal,
 * spoken answer — not an error.
 */
class LaptopTool(private val context: Context) : Tool {

    override val name = "laptop_control"

    override val intent = IntentType.LAPTOP_CONTROL

    override val definition = FunctionDef(
        name = name,
        description = "Control the user's Windows laptop over the local network: " +
            "lock, sleep, shutdown, restart, open or close apps, media playback, " +
            "volume, brightness, screenshot, and status.",
        parameters = Parameters(
            properties = mapOf(
                "action" to Property(
                    "string",
                    "One of: status, lock, sleep, screen_off, shutdown, restart, logoff, " +
                        "cancel_shutdown, open, close, switch, media, brightness, " +
                        "dark_mode, wifi, folder, browser, type, clipboard_get, clipboard_set, " +
                        "screenshot, screen_record, foreground, gradle"
                ),
                "app" to Property("string", "The app to open or close"),
                "media_action" to Property("string", "Media control action"),
                "direction" to Property("string", "Direction for volume or brightness"),
                "level" to Property("string", "Target level for volume or brightness"),
                "state" to Property("string", "State to set (on/off)"),
                "folder" to Property("string", "Folder to open"),
                "text" to Property("string", "Text to type or process"),
                "query" to Property("string", "Search query"),
                "task" to Property("string", "Task description")
            )
        )
    )

    /** Ending the user's session on the laptop needs a spoken yes. */
    override fun needsConfirmation(request: ToolRequest): Boolean =
        request.string("action")?.lowercase() in DESTRUCTIVE_ACTIONS

    private companion object {
        val DESTRUCTIVE_ACTIONS = setOf("shutdown", "restart", "logoff")
        const val PREFS = "laptop_agent"
        const val KEY_HOST = "host"
        const val KEY_TOKEN = "token"
        const val PORT = 8765

        // Short on purpose. If the laptop is asleep we want to say so quickly,
        // not leave the user staring at the orb.
        const val CONNECT_TIMEOUT_MS = 1500
        const val READ_TIMEOUT_MS = 4000
        const val OVERALL_TIMEOUT_MS = 6000L
    }

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Set once from the phone's settings screen. */
    fun configure(host: String, token: String) {
        android.util.Log.d("MarkLaptop", "configured host=$host")
        prefs.edit().putString(KEY_HOST, host.trim()).putString(KEY_TOKEN, token.trim()).apply()
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        val host = prefs.getString(KEY_HOST, null)?.takeIf { it.isNotBlank() }
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

        if (host == null || token == null) {
            android.util.Log.e("MarkLaptop", "not configured: host=$host token=${token != null}")
            return ToolResult.Failure(
                "Laptop isn't set up yet. Add its address in Mark's settings on the phone.",
                reason = "not_configured"
            )
        }

        val action = request.string("action") ?: "status"

        // The resolver's laptop rule has two alternatives (laptop-first and
        // app-first word order), so the app name lands in whichever group matched.
        val app = request.string("app") ?: request.string("app2")

        val body = JSONObject().apply {
            put("action", if (action == "status") "status" else action)
            app?.let { put("app", normalizeApp(it)) }
            request.string("media_action")?.let { put("media_action", it) }
            request.string("direction")?.let { put("direction", it) }
            request.string("level")?.let { put("level", it) }
            request.string("amount")?.let { put("amount", it) }
            request.string("state")?.let { put("state", it) }
            request.string("folder")?.let { put("folder", it) }
            request.string("text")?.let { put("text", it) }
            request.string("query")?.let { put("query", it) }
            request.string("task")?.let { put("task", it) }
        }

        val result = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            post("http://$host:$PORT/command", token, body.toString())
        }

        return when {
            result == null -> ToolResult.Failure(
                "Laptop isn't responding, sir.", reason = "timeout"
            )
            result.first == 401 -> ToolResult.Failure(
                "The laptop rejected the token. Check it in settings.", reason = "unauthorized"
            )
            result.first != 200 -> ToolResult.Failure(
                "Laptop couldn't do that, sir.", reason = "agent_error"
            )
            else -> {
                val json = runCatching { JSONObject(result.second) }.getOrNull()
                val ok = json?.optBoolean("ok", false) ?: false
                val text = json?.optString("text").orEmpty()
                    .ifBlank { if (ok) "Done, sir." else "That didn't work on the laptop." }
                if (ok) {
                    ToolResult.Success(text, mapOf("action" to action))
                } else {
                    ToolResult.Failure(text, reason = "agent_refused")
                }
            }
        }
    }

    /** "vs code" and "android studio" arrive spaced; the agent keys are compact. */
    private fun normalizeApp(raw: String): String = raw.trim().lowercase()
        .replace("vs code", "vscode")
        .replace("android studio", "androidstudio")

    private suspend fun post(url: String, token: String, json: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Mark-Token", token)
            }
            try {
                conn.outputStream.use { it.write(json.toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                code to text
            } finally {
                conn.disconnect()
            }
        }
}
