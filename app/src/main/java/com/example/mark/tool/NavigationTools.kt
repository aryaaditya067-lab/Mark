package com.example.mark.tool

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.mark.assistant.PhoneToolSchemas
import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.router.IntentType
import com.example.mark.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class NavigateToTool(private val context: Context) : Tool {
    override val name = "navigate_to"
    override val intent = IntentType.NAVIGATE_TO
    override val definition = PhoneToolSchemas.NAVIGATE_TO

    override suspend fun execute(request: ToolRequest): ToolResult {
        val rawDest = request.string("destination") ?: return ToolResult.Failure("Where do you want to go?", reason = "missing_arg")
        
        val settings = SettingsRepository(context)
        val places = settings.savedPlaces.first()
        
        val isHome = rawDest.lowercase() == "home" || rawDest.lowercase() == "ghar"
        val isWork = setOf("work", "office", "college", "kaam").contains(rawDest.lowercase())

        val destination = when {
            isHome -> places["home"]
            isWork -> places["work"]
            else -> rawDest
        }

        if (isHome && destination.isNullOrBlank()) {
            return ToolResult.Failure("I don't have your home address saved yet — set it in Mark on your phone.", reason = "not_set")
        }
        if (isWork && destination.isNullOrBlank()) {
            return ToolResult.Failure("I don't have your work address saved yet — set it in Mark on your phone.", reason = "not_set")
        }

        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination!!)}")
        android.util.Log.d("MarkNav", "destination='$destination' uri=$gmmIntentUri")

        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}"))
                geoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(geoIntent)
            }
            ToolResult.Success("Opening navigation to $destination")
        } catch (e: Exception) {
            ToolResult.Failure("Could not open maps: ${e.message}", reason = "system_error")
        }
    }
}

class GetDistanceTool(private val context: Context) : Tool {
    override val name = "get_distance"
    override val intent = IntentType.GET_DISTANCE
    override val definition = PhoneToolSchemas.GET_DISTANCE

    override suspend fun execute(request: ToolRequest): ToolResult {
        val rawDest = request.string("destination") ?: return ToolResult.Failure("Tell me the destination.", reason = "missing_arg")
        
        val settings = SettingsRepository(context)
        val places = settings.savedPlaces.first()
        
        val destination = when (rawDest.lowercase()) {
            "home", "ghar" -> places["home"] ?: rawDest
            "work", "office", "college", "kaam" -> places["work"] ?: rawDest
            else -> rawDest
        }

        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(mapIntent)
            ToolResult.Success("Opening directions to $destination")
        } catch (e: Exception) {
            ToolResult.Failure("Could not open maps.", reason = "system_error")
        }
    }
}

class FindNearbyTool(private val context: Context) : Tool {
    override val name = "find_nearby"
    override val intent = IntentType.FIND_NEARBY
    override val definition = PhoneToolSchemas.FIND_NEARBY

    override suspend fun execute(request: ToolRequest): ToolResult {
        val placeType = request.string("placeType") ?: return ToolResult.Failure("What are you looking for nearby?", reason = "missing_arg")
        
        val intentUri = Uri.parse("geo:0,0?q=${Uri.encode(placeType)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, intentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(mapIntent)
            ToolResult.Success("Searching for $placeType nearby.")
        } catch (e: Exception) {
            ToolResult.Failure("Could not open maps.", reason = "system_error")
        }
    }
}
