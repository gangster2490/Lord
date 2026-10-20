package com.lord.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTest {
    private val zone = ZoneId.of("Europe/Berlin")
    // Tuesday 2026-10-20 08:00
    private val now = ZonedDateTime.of(2026, 10, 20, 8, 0, 0, 0, zone)

    @Test fun oneShotLaterToday() {
        val next = Alarm(1, 9, 30).nextTrigger(now)
        assertEquals(ZonedDateTime.of(2026, 10, 20, 9, 30, 0, 0, zone), next)
    }

    @Test fun oneShotPassedGoesToTomorrow() {
        val next = Alarm(1, 7, 0).nextTrigger(now)
        assertEquals(ZonedDateTime.of(2026, 10, 21, 7, 0, 0, 0, zone), next)
    }

    @Test fun sameMinuteIsNotNow() {
        val next = Alarm(1, 8, 0).nextTrigger(now)
        assertEquals(21, next.dayOfMonth)
    }

    @Test fun repeatingPicksNextMatchingDay() {
        val alarm = Alarm(1, 7, 0, days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertEquals(ZonedDateTime.of(2026, 10, 23, 7, 0, 0, 0, zone), alarm.nextTrigger(now))
    }

    @Test fun repeatingSameDayPassedWrapsAWeek() {
        val alarm = Alarm(1, 7, 0, days = setOf(DayOfWeek.TUESDAY))
        assertEquals(ZonedDateTime.of(2026, 10, 27, 7, 0, 0, 0, zone), alarm.nextTrigger(now))
    }

    @Test fun snoozeWinsAndDisabledIsNull() {
        val snooze = now.plusMinutes(10).toInstant().toEpochMilli()
        assertEquals(snooze, Alarm(1, 7, 0, enabled = false, snoozeAt = snooze).nextRingMillis(now))
        assertNull(Alarm(1, 7, 0, enabled = false).nextRingMillis(now))
    }

    @Test fun untilTextFormats() {
        assertEquals("через 1 ч 5 мин", untilText(0, 65 * 60_000L))
        assertEquals("через 1 мин", untilText(0, 30_000L))
    }

    @Test fun daysSummaryNames() {
        assertEquals("По будням", daysSummary(DayOfWeek.entries.take(5).toSet()))
        assertEquals("Однократно", daysSummary(emptySet()))
    }
}
