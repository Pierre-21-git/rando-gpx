package fr.pierre.randogpx

import android.app.Application
import fr.pierre.randogpx.data.AppDatabase
import fr.pierre.randogpx.data.HikeRepository
import fr.pierre.randogpx.map.OsmdroidConfigurator

class RandoApplication : Application() {

    // DI manuelle volontairement simple (pas de Hilt) pour limiter les points de
    // défaillance de compilation dans ce squelette généré sans build de vérification.
    lateinit var repository: HikeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        OsmdroidConfigurator.configure(this)
        val db = AppDatabase.getInstance(this)
        repository = HikeRepository(db.hikeDao(), db.trackPointDao(), db.massifDao())
    }
}
