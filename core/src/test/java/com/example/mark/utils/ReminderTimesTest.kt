package com.example.mark.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimesTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 10, 5, 17, 30, 0, 0, zone) // Monday 17:30

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test
    fun resolvesIsoBareTimeAndMinutes() {
        assertEquals(at(2026, 10, 5, 18, 0), ReminderTimes.resolve("2026-10-05T18:00", null, now))
        assertEquals(at(2026, 10, 5, 18, 0), ReminderTimes.resolve("2026-10-05 18:00:00", null, now))
        assertEquals("later today", at(2026, 10, 5, 18, 0), ReminderTimes.resolve("18:00", null, now))
        assertEquals("already passed: tomorrow", at(2026, 10, 6, 9, 0), ReminderTimes.resolve("09:00", null, now))
        assertEquals(now.plusMinutes(20), ReminderTimes.resolve(null, 20, now))
    }

    @Test
    fun rejectsPastNonsenseAndNothing() {
        assertNull(ReminderTimes.resolve("2026-10-05T09:00", null, now))
        assertNull(ReminderTimes.resolve("soon", null, now))
        assertNull(ReminderTimes.resolve(null, 0, now))
        assertNull(ReminderTimes.resolve(null, null, now))
    }

    @Test
    fun repeatsMoveToTheNextFutureSlot() {
        val firedAt = at(2026, 10, 5, 17, 0).toInstant().toEpochMilli()
        assertEquals(at(2026, 10, 6, 17, 0).toInstant().toEpochMilli(), ReminderTimes.next(firedAt, "daily", now))
        assertEquals(at(2026, 10, 12, 17, 0).toInstant().toEpochMilli(), ReminderTimes.next(firedAt, "weekly", now))
        assertNull(ReminderTimes.next(firedAt, "none", now))
    }

    @Test
    fun describesNaturally() {
        fun d(t: ZonedDateTime) = ReminderTimes.describe(t.toInstant().toEpochMilli(), now)
        assertEquals("today at 6:00 PM", d(at(2026, 10, 5, 18, 0)))
        assertEquals("tomorrow at 9:30 AM", d(at(2026, 10, 6, 9, 30)))
        assertEquals("on Friday at 7:00 AM", d(at(2026, 10, 9, 7, 0)))
        assertEquals("on 20 October at 8:00 AM", d(at(2026, 10, 20, 8, 0)))
    }
}
