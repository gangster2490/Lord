package de.spardirekt.recipeveo

import android.app.Application
import de.spardirekt.recipeveo.domain.FileStudioPersist
import de.spardirekt.recipeveo.domain.StudioStore
import de.spardirekt.recipeveo.domain.SystemAppClock
import java.io.File

class RecipeVeoApplication : Application() {
    lateinit var store: StudioStore
        private set
    lateinit var photos: PhotoStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = StudioStore(
            persist = FileStudioPersist(File(filesDir, "studio.json")),
            clock = SystemAppClock(),
        )
        photos = PhotoStore(this)
    }
}
