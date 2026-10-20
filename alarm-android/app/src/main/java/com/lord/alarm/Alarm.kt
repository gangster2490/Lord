package com.lord.alarm

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.ZonedDateTime

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    /** null = system default alarm sound. */
    val ringtoneUri: String? = null,
    /** Epoch millis of a pending snooze, or null. */
    val snoozeAt: Long? = null,
) {
    val isRepeating: Boolean get() = days.isNotEmpty()

    val timeText: String get() = "%02d:%02d".format(hour, minute)

    /** Next regular (non-snooze) ring time strictly after [now]. */
    fun nextTrigger(now: ZonedDateTime): ZonedDateTime {
        val today = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        for (offset in 0..7L) {
            val candidate = today.plusDays(offset)
            if (candidate.isAfter(now) && (days.isEmpty() || candidate.dayOfWeek in days)) {
                return candidate
            }
        }
        error("unreachable")
    }

    /** Epoch millis when this alarm should next ring, or null when nothing is scheduled. */
    fun nextRingMillis(now: ZonedDateTime): Long? {
        snoozeAt?.let { if (it > now.toInstant().toEpochMilli()) return it }
        if (!enabled) return null
        return nextTrigger(now).toInstant().toEpochMilli()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("days", JSONArray(days.map { it.value }))
        put("label", label)
        put("enabled", enabled)
        put("vibrate", vibrate)
        ringtoneUri?.let { put("ringtone", it) }
        snoozeAt?.let { put("snoozeAt", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val daysArr = o.optJSONArray("days") ?: JSONArray()
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                days = (0 until daysArr.length()).map { DayOfWeek.of(daysArr.getInt(it)) }.toSet(),
                label = o.optString("label"),
                enabled = o.optBoolean("enabled", true),
                vibrate = o.optBoolean("vibrate", true),
                ringtoneUri = if (o.has("ringtone")) o.getString("ringtone") else null,
                snoozeAt = if (o.has("snoozeAt")) o.getLong("snoozeAt") else null,
            )
        }
    }
}

private val SHORT_DAYS = mapOf(
    DayOfWeek.MONDAY to "Пн",
    DayOfWeek.TUESDAY to "Вт",
    DayOfWeek.WEDNESDAY to "Ср",
    DayOfWeek.THURSDAY to "Чт",
    DayOfWeek.FRIDAY to "Пт",
    DayOfWeek.SATURDAY to "Сб",
    DayOfWeek.SUNDAY to "Вс",
)

fun DayOfWeek.shortRu(): String = SHORT_DAYS.getValue(this)

fun daysSummary(days: Set<DayOfWeek>): String {
    val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    return when {
        days.isEmpty() -> "Однократно"
        days.size == 7 -> "Каждый день"
        days == weekdays -> "По будням"
        days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "По выходным"
        else -> DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.shortRu() }
    }
}

/** "через 7 ч 5 мин" style countdown. */
fun untilText(fromMillis: Long, toMillis: Long): String {
    val totalMin = ((toMillis - fromMillis + 59_999) / 60_000).coerceAtLeast(0)
    val d = totalMin / (24 * 60)
    val h = (totalMin / 60) % 24
    val m = totalMin % 60
    val parts = buildList {
        if (d > 0) add("$d д")
        if (h > 0) add("$h ч")
        if (m > 0 || isEmpty()) add("$m мин")
    }
    return "через " + parts.joinToString(" ")
}
