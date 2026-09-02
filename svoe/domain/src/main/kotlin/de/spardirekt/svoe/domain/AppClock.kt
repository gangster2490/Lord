package de.spardirekt.svoe.domain

import java.time.LocalDate

interface AppClock {
    fun nowMillis(): Long
    fun today(): LocalDate
}

class SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()
}

class FixedAppClock(
    private val today: LocalDate,
    private val nowMillis: Long = today.toEpochDay() * 86_400_000L,
) : AppClock {
    override fun nowMillis(): Long = nowMillis
    override fun today(): LocalDate = today
}
