package com.example.mark.utils

/** A chat as seen through its notification: who, which app, the latest lines. */
data class Conversation(
    val key: String,                 // StatusBarNotification key
    val app: String,                 // app label, e.g. "WhatsApp"
    val packageName: String,
    val title: String,               // contact or group name
    val lines: List<ChatLine>,       // oldest first
    val canReply: Boolean,
    val postTime: Long
)

data class ChatLine(val sender: String, val text: String, val time: Long)

/** Sender name used for the user's own messages. */
const val SELF = "You"

/** Finding and describing conversations; pure, so it can be tested. */
object Conversations {

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * The conversation best matching [contact] (and [app] if given), newest
     * first among equals. Matches whole name, then first-word prefix, so
     * "rahul" finds "Rahul Sharma" but not "Rahulya Group" over an exact hit.
     */
    fun find(all: List<Conversation>, contact: String, app: String? = null): Conversation? {
        val wanted = norm(contact)
        if (wanted.isEmpty()) return null
        val pool = all.filter { c -> app.isNullOrBlank() || norm(c.app).contains(norm(app)) || c.packageName.contains(norm(app).replace(" ", "")) }
            .sortedByDescending { it.postTime }
        return pool.firstOrNull { norm(it.title) == wanted }
            ?: pool.firstOrNull { norm(it.title).split(" ").any { word -> word == wanted } }
            ?: pool.firstOrNull { norm(it.title).startsWith(wanted) }
            ?: pool.firstOrNull { norm(it.title).contains(wanted) }
    }

    /** "WhatsApp, Rahul: Rahul: are you coming? | You: yes" — the last [max] lines, for the LLM. */
    fun describe(c: Conversation, max: Int = 6): String {
        val lines = c.lines.takeLast(max).joinToString(" | ") { "${it.sender}: ${it.text}" }
        return "${c.app}, ${c.title}: " + lines.ifBlank { "(no message text)" }
    }

    /** One spoken line for an announcement; null when the latest line is the user's own. */
    fun announcement(c: Conversation): String? {
        val last = c.lines.lastOrNull() ?: return null
        if (last.sender == SELF) return null
        val who = if (last.sender.isBlank() || last.sender == c.title) c.title else "${last.sender} in ${c.title}"
        return "Message from $who on ${c.app}: ${last.text.take(160)}"
    }

    /** Quiet hours wrap past midnight, e.g. 22 to 7. */
    fun inQuietHours(hour: Int, startHour: Int = 22, endHour: Int = 7): Boolean =
        if (startHour <= endHour) hour in startHour until endHour else hour >= startHour || hour < endHour
}
