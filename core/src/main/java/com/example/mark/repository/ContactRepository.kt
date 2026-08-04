package com.example.mark.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.mark.utils.FuzzyMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class Contact(val name: String, val number: String)

class ContactRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cachedContacts: List<Contact> = emptyList()
    private val mutex = Mutex()
    private var observerRegistered = false

    init {
        tryRefresh()
    }

    fun tryRefresh() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        scope.launch {
            refreshCache()
        }
        
        if (!observerRegistered) {
            try {
                context.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            scope.launch { refreshCache() }
                        }
                    }
                )
                observerRegistered = true
            } catch (e: Exception) {
                android.util.Log.e("ContactRepo", "Failed to register observer", e)
            }
        }
    }

    private suspend fun refreshCache() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = mutableListOf<Contact>()
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val name = it.getString(0) ?: ""
                    val number = it.getString(1) ?: ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        list.add(Contact(name, number))
                    }
                }
            }
            cachedContacts = list.distinctBy { it.name.lowercase() + it.number.filter { c -> c.isDigit() } }
        }
    }

    suspend fun resolve(query: String): List<Match> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            if (cachedContacts.isEmpty()) {
                refreshCache()
            }
        } else {
            return emptyList()
        }

        return mutex.withLock {
            cachedContacts.map { contact ->
                val name = contact.name.lowercase()
                val firstName = name.split(" ").firstOrNull() ?: ""

                val confidence = when {
                    name == q -> 1.0f
                    firstName == q -> 0.85f
                    else -> {
                        val dist = FuzzyMatcher.levenshtein(name, q, 2)
                        when (dist) {
                            1 -> 0.7f
                            2 -> 0.5f
                            else -> 0.0f
                        }
                    }
                }
                Match(contact, confidence)
            }.filter { it.confidence > 0f }
            .sortedByDescending { it.confidence }
        }
    }

    data class Match(val contact: Contact, val confidence: Float)

    companion object {
        @Volatile private var instance: ContactRepository? = null
        fun getInstance(context: Context): ContactRepository =
            instance ?: synchronized(this) {
                instance ?: ContactRepository(context.applicationContext).also { instance = it }
            }
    }
}
