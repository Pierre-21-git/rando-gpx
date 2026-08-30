package fr.pierre.randogpx.ui.theme

import androidx.compose.ui.graphics.Color

// Palette inspirée nature/randonnée
val ForestGreen = Color(0xFF2E5339)
val MossGreen = Color(0xFF5C8A5C)
val StoneGrey = Color(0xFF6B6B6B)
val SkyBlue = Color(0xFF7FA8C9)
val TrailOrange = Color(0xFFD98E4A)

/**
 * Couleur du tracé GPS sur la carte (osmdroid Polyline, hors Compose donc en Int ARGB) :
 * un rouge vif choisi pour rester lisible par-dessus les fonds verts/bruns d'OpenTopoMap,
 * contrairement au noir par défaut d'osmdroid qui s'y fondait.
 */
const val TrackColorArgb: Int = 0xFFE63946.toInt()
