package fr.pierre.randogpx.map

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.views.overlay.CopyrightOverlay

/**
 * Configuration globale d'osmdroid : user-agent obligatoire (sinon tuiles refusées par
 * certains serveurs), et répertoire de cache pour le mode hors ligne.
 * À appeler une fois au démarrage de l'application (voir RandoApplication).
 */
object OsmdroidConfigurator {

    fun configure(context: Context) {
        val config = Configuration.getInstance()
        val prefs = context.getSharedPreferences("${context.packageName}_osmdroid", Context.MODE_PRIVATE)
        config.load(context, prefs)
        config.userAgentValue = context.packageName
        config.osmdroidTileCache = context.getExternalFilesDir("osmdroid_tiles")
    }

    /**
     * Source de tuiles OpenTopoMap (gratuite, orientée randonnée : relief + sentiers).
     * Se référer à https://opentopomap.org/about pour la politique d'usage (attribution
     * obligatoire, pas d'usage commercial à fort trafic sans accord).
     */
    val openTopoMapSource: ITileSource = XYTileSource(
        "OpenTopoMap",
        0, 17, 256, ".png",
        arrayOf(
            "https://a.tile.opentopomap.org/",
            "https://b.tile.opentopomap.org/",
            "https://c.tile.opentopomap.org/"
        ),
        "© OpenTopoMap (CC-BY-SA) © OpenStreetMap contributors"
    )

    /**
     * Overlay affichant la mention de copyright de [openTopoMapSource] directement sur
     * la carte, comme le demande la FAQ OpenTopoMap ("Kartendaten [...] Kartendarstellung
     * [...] doit rester clairement visible"). À ajouter aux overlays de chaque MapView.
     */
    fun copyrightOverlay(context: Context): CopyrightOverlay =
        CopyrightOverlay(context).apply {
            setCopyrightNotice(openTopoMapSource.copyrightNotice)
            setTextSize(10)
        }
}
