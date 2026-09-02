package de.spardirekt.svoe

import android.app.Application
import de.spardirekt.svoe.domain.FileLifePersist
import de.spardirekt.svoe.domain.LifeStore
import de.spardirekt.svoe.domain.SystemAppClock
import java.io.File

class SvoeApplication : Application() {
    lateinit var store: LifeStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = LifeStore(
            persist = FileLifePersist(File(filesDir, "life.json")),
            clock = SystemAppClock(),
        )
    }
}
