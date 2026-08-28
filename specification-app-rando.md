# Spécification — Application de randonnée GPX (Android natif)

## 1. Objectif

Application Android native permettant d'enregistrer une randonnée via géolocalisation, de générer un fichier GPX, et de consulter les statistiques du parcours (distance, dénivelé, vitesse). Publication visée sur **F-Droid**, ce qui impose des contraintes de dépendances 100% libres.

## 2. Contraintes clés

- **Cible** : Android uniquement (pas d'iOS)
- **Approche** : natif Kotlin (pas de framework cross-platform), pour robustesse maximale du tracking GPS en arrière-plan
- **Usage terrain** : téléphone rangé (poche/sac), écran éteint pendant l'enregistrement → nécessite un tracking fiable en arrière-plan
- **Publication F-Droid** : aucune dépendance propriétaire (Google Play Services exclu), licence open source (GPL v3 recommandée), dépôt public (GitHub/GitLab/Codeberg)
- **Téléphones de référence testés** : Motorola Moto G84 5G et Honor 400 Lite — **aucun des deux n'a de baromètre**. Le mode GPS-seul avec filtrage doit donc être le chemin principal, pas un simple fallback.

## 3. Stack technique

| Composant | Choix | Justification |
|---|---|---|
| Langage / UI | Kotlin + Jetpack Compose | Standard moderne Android, AndroidX (libre) |
| Géolocalisation | `LocationManager` (API Android standard) | `FusedLocationProviderClient` exclu car dépend de Google Play Services (non conforme F-Droid) |
| Tracking arrière-plan | `Foreground Service` + notification persistante | Seul moyen fiable de continuer le tracking écran éteint/app fermée |
| Cartes | **osmdroid** (basé mapsforge) | Libre (LGPL), gère nativement le cache offline, largement utilisé par des apps F-Droid (OsmAnd~, etc.) |
| Fond de carte | Tuiles **OpenTopoMap** (gratuit, relief + sentiers) | Pas de dépendance réseau propriétaire |
| Stockage local | **Room** (SQLite) | Libre, standard AndroidX |
| Graphique élévation | **MPAndroidChart** | Libre (Apache 2.0), courant dans l'écosystème F-Droid |
| Export | Génération XML GPX native (`FileWriter`) + partage via `FileProvider` | Pas de lib tierce nécessaire |

## 4. Fonctionnalités

1. **Enregistrement** : start / pause / stop via Foreground Service, avec notification affichant distance/durée en temps réel
2. **Carte live** : affichage du tracé en cours sur fond de carte osmdroid
3. **Statistiques live et post-rando** :
   - Distance (formule de Haversine)
   - Dénivelé positif/négatif (voir pipeline altitude ci-dessous)
   - Vitesse instantanée et moyenne
   - Durée
4. **Graphique d'élévation** : courbe altitude/distance cumulée (MPAndroidChart), affichée sur l'écran de détail d'une rando
5. **Historique** : liste des randos enregistrées (Room), avec accès au détail de chacune
6. **Export GPX** : génération d'un fichier GPX standard (`<trk><trkseg><trkpt>` avec `<ele>` et `<time>`), partageable via `Intent`
7. **Cartes hors ligne** : téléchargement des tuiles pour une zone sélectionnée (rectangle sur la carte), géré nativement par osmdroid

## 5. Pipeline de calcul d'altitude

Le GPS seul étant peu fiable en altitude (précision verticale 2-3x moins bonne qu'horizontale, bruit important), le calcul se fait en plusieurs étapes :

1. **Lecture altitude GPS brute** via `LocationManager`
2. **Filtrage/lissage** : moyenne glissante + seuil de variation minimale (ignorer les changements < 2-3m entre points consécutifs) pour éliminer le bruit GPS
3. **Correction géoïde EGM96** : le GPS mesure l'altitude par rapport à l'ellipsoïde WGS84 (modèle mathématique lisse), alors que l'altitude "au-dessus du niveau de la mer" doit être relative au géoïde (forme réelle de la Terre liée à la gravité). L'écart peut atteindre plusieurs dizaines de mètres selon la zone du globe — c'est une erreur systématique, pas du bruit. Correction via une grille EGM96 embarquée offline (référence : implémentation de BasicAirData GPS Logger, open source GPL-3.0, `github.com/BasicAirData/GPSLogger`)
4. **Cumul du dénivelé +/-** à partir de la série de points corrigés

**Détection dynamique du baromètre** : si l'appareil dispose d'un baromètre (`SensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)` non null), l'utiliser en complément pour affiner les variations relatives d'altitude, recalé périodiquement sur l'altitude GPS/EGM96 pour la valeur absolue. Sinon, fonctionner intégralement sur le pipeline GPS + filtrage + EGM96 ci-dessus (cas par défaut, puisque les téléphones de test n'ont pas de baromètre).

## 6. Modèle de données

```kotlin
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val altitudeGps: Double,
    val altitudeCorrected: Double? = null, // après filtrage + EGM96
    val timestamp: Long,
    val speed: Float?
)

data class Hike(
    val id: Long,
    val name: String,
    val startTime: Long,
    val endTime: Long?,
    val points: List<TrackPoint>
)
```

## 7. Architecture du projet

**Pattern** : MVVM (Model-View-ViewModel), standard Android moderne, sépare UI / logique métier / données.

**Arborescence des packages**

```
com.tonapp.rando/
├── data/           → Room (DB), modèles (Hike, TrackPoint), repository
├── location/       → Foreground Service, LocationManager, filtrage GPS, correction EGM96
├── gpx/            → génération/export du fichier GPX
├── map/            → intégration osmdroid, gestion tuiles offline
├── ui/             → écrans Compose
│   ├── recording/  → écran d'enregistrement + carte live
│   ├── history/    → liste des randos enregistrées
│   └── detail/     → détail d'une rando (carte, stats, graphique élévation)
└── di/             → injection de dépendances (Hilt, optionnel)
```

**Fichiers clés à créer en premier**
- `AndroidManifest.xml` (permissions, déclaration du Service)
- Entités Room (`Hike`, `TrackPoint`)
- Squelette du Foreground Service de tracking

## 8. Permissions Android requises

- `ACCESS_FINE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION` (demande séparée, dialogue spécifique depuis Android 10+)
- `FOREGROUND_SERVICE_LOCATION` (Android 14+)
- `POST_NOTIFICATIONS` (Android 13+)

## 9. Prérequis pour publication F-Droid

- Licence open source (GPL v3 recommandée)
- Dépôt public (GitHub/GitLab/Codeberg)
- Aucune dépendance ni repository Gradle propriétaire
- Aucun SDK analytics/tracker (Firebase, Crashlytics, etc.)
- Build reproductible recommandé (non obligatoire, améliore le score F-Droid)

## 10. Références et sources d'inspiration

- **BasicAirData GPS Logger** (`github.com/BasicAirData/GPSLogger`, GPL-3.0) — référence principale pour le Foreground Service et la correction EGM96
- **A-GPS Tracker** (`com.giobat.troviamoci`) — référence pour l'approche GPX et le suivi en veille
- **GPX Viewer** (`com.vecturagames.android.app.gpxviewer`) — référence pour les fonctionnalités de statistiques/graphiques (profil d'élévation)

## 11. Fonctionnalités hors périmètre MVP (v2 potentielle)

- Alerte de déviation par rapport à un tracé chargé ("follow path")
- Notifications vocales basées sur le temps/distance
- Colorisation du tracé selon l'élévation/vitesse
- Affichage live du graphique d'élévation pendant l'enregistrement (v1 : uniquement post-rando)
