package de.spardirekt.recipeveo.domain

import java.time.LocalDate
import java.time.LocalTime

interface AppClock {
    fun nowMillis(): Long
    fun today(): LocalDate
    fun nowTime(): LocalTime
}

class SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()
    override fun nowTime(): LocalTime = LocalTime.now()
}

class FixedAppClock(
    private val today: LocalDate = LocalDate.of(2026, 9, 2),
    private val nowMillis: Long = 1_725_000_000_000L,
    private val nowTime: LocalTime = LocalTime.of(19, 30),
) : AppClock {
    override fun nowMillis(): Long = nowMillis
    override fun today(): LocalDate = today
    override fun nowTime(): LocalTime = nowTime
}
