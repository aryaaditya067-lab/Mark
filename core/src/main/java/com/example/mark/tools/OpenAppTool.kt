package com.example.mark.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.router.IntentType
import com.example.mark.utils.FuzzyMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAppTool(private val context: Context) : Tool {

    override val name = "open_app"
    override val intent = IntentType.OPEN_APP

    override val definition = FunctionDef(
        name = name,
        description = "Open an installed application by its name.",
        parameters = Parameters(
            properties = mapOf(
                "app_name" to Property("string", "The common name of the app, e.g. 'YouTube' or 'WhatsApp'"),
                "target" to Property("string", "Target device: 'phone' or 'watch'")
            ),
            required = listOf("app_name")
        )
    )

    init {
        // Build the index in a separate thread to avoid blocking main thread at startup.
        // Since getOrBuildAppIndexSync is gated by cachedApps null check, multiple 
        // calls are safe.
        kotlin.concurrent.thread {
            getOrBuildAppIndexSync()
        }
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        val appName = request.string("app_name")?.lowercase()?.trim() 
            ?: return ToolResult.Failure("Missing app name.", reason = "missing_arg")
        val target = request.string("target") ?: "phone"

        android.util.Log.d("MarkOpen", "requested='$appName' target=$target")

        // 1. Check direct aliases first (highest priority)
        ALIASES[appName]?.let { pkg ->
            android.util.Log.d("MarkOpen", "requested='$appName' resolved=$pkg (alias)")
            return launchPackage(pkg)
        }

        // 2. Search indexed apps
        val apps = getOrBuildAppIndex()
        
        // Exact match
        apps[appName]?.let { pkg ->
            android.util.Log.d("MarkOpen", "requested='$appName' resolved=$pkg (exact)")
            return launchPackage(pkg)
        }

        // Fuzzy match using Levenshtein
        val bestMatch = apps.keys
            .map { label -> label to FuzzyMatcher.levenshtein(label, appName, 2) }
            .filter { it.second <= 2 }
            .minByOrNull { it.second }

        return if (bestMatch != null) {
            val pkg = apps[bestMatch.first]!!
            android.util.Log.d("MarkOpen", "requested='$appName' resolved=$pkg (fuzzy: ${bestMatch.first})")
            launchPackage(pkg)
        } else {
            android.util.Log.e("MarkOpen", "requested='$appName' resolved=null")
            ToolResult.Failure("Could not find an app named '$appName'.", reason = "not_found")
        }
    }

    private fun launchPackage(packageName: String): ToolResult {
        val pm = context.packageManager
        var intent = pm.getLaunchIntentForPackage(packageName)

        if (intent == null) {
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            if (resolveInfos.isNotEmpty()) {
                val launchable = resolveInfos.first()
                intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(launchable.activityInfo.packageName, launchable.activityInfo.name)
                }
            }
        }

        if (intent == null) return ToolResult.Failure("Cannot launch $packageName.", reason = "not_launchable")

        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            val label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))
            ToolResult.Success("Opening $label.")
        } catch (e: Exception) {
            ToolResult.Failure("Failed to open $packageName.", reason = "hardware_error")
        }
    }

    private suspend fun getOrBuildAppIndex(): Map<String, String> = withContext(Dispatchers.IO) {
        getOrBuildAppIndexSync()
    }

    private fun getOrBuildAppIndexSync(): Map<String, String> {
        cachedApps?.let { return it }
        
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchables = pm.queryIntentActivities(mainIntent, 0)
        
        val index = mutableMapOf<String, String>()
        for (resolve in launchables) {
            val label = resolve.loadLabel(pm).toString().lowercase().trim()
            val pkg = resolve.activityInfo.packageName
            if (!index.containsKey(label)) {
                index[label] = pkg
            }
        }
        cachedApps = index
        return index
    }

    companion object {
        @Volatile private var cachedApps: Map<String, String>? = null
        
        private val ALIASES = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music"
        )
    }
}
