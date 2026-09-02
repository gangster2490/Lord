package de.spardirekt.recipeveo.domain

interface AppClock {
    fun nowMillis(): Long
}

class SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class FixedAppClock(private val nowMillis: Long = 1_725_000_000_000L) : AppClock {
    override fun nowMillis(): Long = nowMillis
}
