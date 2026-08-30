package fr.pierre.randogpx.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [Hike::class, TrackPoint::class, Massif::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun hikeDao(): HikeDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun massifDao(): MassifDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Ajout des statistiques étendues (vitesse max, altitude min/max) affichées dans le détail. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hikes ADD COLUMN maxSpeedKmh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE hikes ADD COLUMN minAltitudeMeters REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE hikes ADD COLUMN maxAltitudeMeters REAL NOT NULL DEFAULT 0.0")
            }
        }

        /** Ajout des massifs (classement des randonnées) : table dédiée + colonne de rattachement sur hikes. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS massifs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                db.execSQL("ALTER TABLE hikes ADD COLUMN massifId INTEGER")
            }
        }

        /** Ajout de la vitesse de déplacement (distance / temps hors arrêts). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hikes ADD COLUMN movingSpeedKmh REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rando_gpx.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
