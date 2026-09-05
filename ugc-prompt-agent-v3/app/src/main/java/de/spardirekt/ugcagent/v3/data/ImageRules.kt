package de.spardirekt.ugcagent.v3.data

object ImageRules {
    const val MIN = 3
    const val MAX = 20
    const val RECOMMENDED_MIN = 5
    const val RECOMMENDED_MAX = 10

    fun canAnalyse(count: Int): Boolean = count in MIN..MAX
    fun needMoreMessage(): String = "Для анализа нужно минимум 3 изображения."
}
