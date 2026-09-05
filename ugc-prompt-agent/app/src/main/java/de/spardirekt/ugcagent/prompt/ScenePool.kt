package de.spardirekt.ugcagent.prompt

data class SceneIdea(
    val opener: String,
    val environment: String,
    val action: String,
) {
    val key: String get() = "$opener|$environment|$action"
}

object ScenePool {
    val openers = listOf(
        "Hand greift ins Bild",
        "Person schaut kurz irritiert",
        "Produkt liegt schon angefangen benutzt im Bild",
        "Kamera wird zufällig draufgehalten",
    )

    val environments = listOf(
        "normale Küche mit Alltagsunordnung",
        "Rücksitz Auto",
        "Camping-Tisch mit anderen Gegenständen drauf",
        "Badezimmer-Ablage",
    )

    val actions = listOf(
        "beiläufiges Ausprobieren",
        "kurzes Zögern dann Zufriedenheit",
        "Weiterreichen an zweite Person",
        "Reaktion mit leichtem Lachen",
    )

    fun allCombinations(): List<SceneIdea> {
        val out = ArrayList<SceneIdea>(openers.size * environments.size * actions.size)
        for (opener in openers) {
            for (environment in environments) {
                for (action in actions) {
                    out += SceneIdea(opener, environment, action)
                }
            }
        }
        return out
    }

    fun pick(count: Int = 4, excludeKey: String? = null, random: java.util.Random = java.util.Random()): List<SceneIdea> {
        val wanted = count.coerceIn(3, 5)
        val pool = allCombinations().toMutableList()
        if (!excludeKey.isNullOrBlank()) {
            pool.removeAll { it.key == excludeKey }
        }
        pool.shuffle(random)
        return pool.take(wanted)
    }
}
