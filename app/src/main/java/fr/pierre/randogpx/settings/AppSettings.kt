package fr.pierre.randogpx.settings

import android.content.Context

/** Réglages utilisateur persistés (SharedPreferences), lisibles hors UI (ex. depuis le service). */
object AppSettings {
    private const val PREFS = "app_settings"
    private const val KEY_TERRAIN_CORRECTION = "terrain_elevation_correction"

    /**
     * Correction d'altitude par tuiles de terrain AWS (envoie les coordonnées de tuile de la
     * randonnée au serveur). Activée par défaut ; désactivée, seule l'altitude GPS + EGM96 est utilisée.
     */
    fun isTerrainCorrectionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TERRAIN_CORRECTION, true)

    fun setTerrainCorrectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TERRAIN_CORRECTION, enabled).apply()
    }
}
