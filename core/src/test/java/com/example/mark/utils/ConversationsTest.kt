package com.example.mark.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationsTest {

    private fun chat(title: String, app: String = "WhatsApp", pkg: String = "com.whatsapp", time: Long = 0, vararg lines: ChatLine) =
        Conversation("k-$title-$app", app, pkg, title, lines.toList(), canReply = true, postTime = time)

    private val all = listOf(
        chat("Rahulya Group", time = 5),
        chat("Rahul Sharma", time = 3, lines = arrayOf(ChatLine("Rahul Sharma", "are you coming?", 1), ChatLine("You", "yes", 2))),
        chat("Mom", app = "Messages", pkg = "com.google.android.apps.messaging", time = 9),
    )

    @Test
    fun findsByWholeWordBeforePrefix() {
        assertEquals("Rahul Sharma", Conversations.find(all, "rahul")?.title)
        assertEquals("Mom", Conversations.find(all, "MOM")?.title)
        assertEquals("Rahulya Group", Conversations.find(all, "rahulya")?.title)
        assertNull(Conversations.find(all, "priya"))
        assertNull(Conversations.find(all, "  "))
    }

    @Test
    fun filtersByApp() {
        assertNull(Conversations.find(all, "mom", app = "WhatsApp"))
        assertEquals("Mom", Conversations.find(all, "mom", app = "messages")?.title)
    }

    @Test
    fun describesLastLines() {
        assertEquals("WhatsApp, Rahul Sharma: Rahul Sharma: are you coming? | You: yes", Conversations.describe(all[1]))
    }

    @Test
    fun announcementNamesSenderInGroups() {
        val group = chat("Family", lines = arrayOf(ChatLine("Papa", "dinner at 8", 1)))
        assertEquals("Message from Papa in Family on WhatsApp: dinner at 8", Conversations.announcement(group))
        val direct = chat("Rahul", lines = arrayOf(ChatLine("Rahul", "on my way", 1)))
        assertEquals("Message from Rahul on WhatsApp: on my way", Conversations.announcement(direct))
    }

    @Test
    fun ownMessagesAreNeverAnnounced() {
        assertNull(Conversations.announcement(all[1])) // last line is "You: yes"
    }

    @Test
    fun quietHoursWrapMidnight() {
        assertTrue(Conversations.inQuietHours(23))
        assertTrue(Conversations.inQuietHours(3))
        assertFalse(Conversations.inQuietHours(7))
        assertFalse(Conversations.inQuietHours(14))
    }
}
