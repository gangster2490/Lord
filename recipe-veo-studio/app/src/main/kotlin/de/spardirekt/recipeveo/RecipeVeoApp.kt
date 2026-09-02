package de.spardirekt.recipeveo

import android.app.Application
import de.spardirekt.recipeveo.domain.FileStudioPersist
import de.spardirekt.recipeveo.domain.StudioStore
import java.io.File

class RecipeVeoApp : Application() {
    lateinit var store: StudioStore
        private set
    lateinit var photos: PhotoStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = StudioStore(FileStudioPersist(File(filesDir, "studio.json")))
        photos = PhotoStore(this)
    }
}
