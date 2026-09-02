package de.spardirekt.recipeveo.domain

data class ProductProfile(
    val id: String,
    val title: String,
    val lockDetails: String,
    val demoUri: String,
    val category: String,
)

object Catalog {
    const val DEMO_CREAM = "demo://velvet-gold"
    const val DEMO_BUDS = "demo://arc-pulse"
    const val DEMO_KETTLE = "demo://ember-kettle"

    val cream = ProductProfile(
        id = "velvet-gold",
        title = "Velvet Gold Night Cream",
        lockDetails = "gold cap, ivory jar, cream texture, short jar silhouette, visible brand mark",
        demoUri = DEMO_CREAM,
        category = "skincare",
    )
    val buds = ProductProfile(
        id = "arc-pulse",
        title = "Arc Pulse Earbuds",
        lockDetails = "matte graphite charging case, two earbuds, silver hinge line, visible charging pins",
        demoUri = DEMO_BUDS,
        category = "audio",
    )
    val kettle = ProductProfile(
        id = "ember-kettle",
        title = "Ember Pour-Over Kettle",
        lockDetails = "brushed copper gooseneck, black handle, flat base, visible spout curve",
        demoUri = DEMO_KETTLE,
        category = "home",
    )

    val all = listOf(cream, buds, kettle)

    fun byId(id: String?): ProductProfile? = all.firstOrNull { it.id == id }

    fun fromPhotos(photos: List<PhotoRef>, productId: String?): ProductProfile? {
        byId(productId)?.let { return it }
        val uris = photos.map { it.uri }.toSet()
        return all.firstOrNull { it.demoUri in uris }
    }
}
