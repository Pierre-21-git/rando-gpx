# RandoGPX — projet Android

Ce projet a été généré à partir de la spécification définie en amont (voir
`specification-app-rando.md`), puis **compilé, corrigé et testé de bout en bout
sur émulateur Android 15 (API 35) et sur un Motorola Moto G84 5G réel** (l'un
des deux téléphones de référence de la spec). Le flow complet (permissions
runtime, start/pause/resume/stop, tracé live, correction EGM96, export GPX,
sélection de zone offline, permission arrière-plan, exemption batterie) a été
validé manuellement sur les deux.

## Pour démarrer

1. Ouvre le dossier `rando-gpx/` dans Android Studio (ou pointe Claude Code dessus).
2. Le Gradle Wrapper est déjà généré (Gradle 8.9, AGP 8.7.3). Si besoin de le
   régénérer : `gradle wrapper --gradle-version 8.9`.
3. Renseigne `local.properties` (`sdk.dir=...`) si Android Studio ne le fait pas
   automatiquement.
4. Laisse Gradle synchroniser (télécharge Compose, Room, osmdroid, MPAndroidChart —
   toutes libres, aucune dépendance Google Play Services).
5. Lance sur un appareil/émulateur Android 8.0+ (minSdk 26).

## Ce qui est fonctionnel

- **Architecture MVVM complète** : data (Room) → location (Service GPS) → ui (Compose)
- **Foreground Service** de tracking GPS avec notification (distance/durée réelles),
  démarré uniquement après obtention de la permission de localisation runtime
- **Filtrage du bruit GPS** (`GpsFilter`) : moyenne glissante + seuil de variation
- **Correction EGM96** (`Egm96Corrector` + `GeoidGrid`) : grille réelle embarquée
  (`assets/egm96/egm96-15.pgm`, format PGM 16 bits GeographicLib, dérivé des
  données publiques NGA WW15MGH) avec interpolation bilinéaire — validé en
  conditions réelles (ondulation ≈ 50 m sur le massif du Mont-Blanc)
- **Support baromètre optionnel** (`BarometerHelper`), avec détection dynamique
- **Export GPX** conforme au format standard 1.1, formatage numérique en
  `Locale.US` (indispensable pour un GPX valide quelle que soit la langue du
  téléphone), partage via `FileProvider` testé avec le share sheet Android
- **Carte osmdroid** avec tuiles OpenTopoMap, **tracé cumulatif live** (Polyline
  alimentée en direct via un `Flow` Room), et écran dédié de sélection de zone
  hors-ligne (`OfflineAreaScreen`) avec estimation du nombre de tuiles
- **3 écrans Compose** : Historique, Enregistrement (carte live), Détail (stats +
  graphique d'élévation MPAndroidChart), plus l'écran Carte hors ligne
- **Vitesse instantanée** affichée en direct sur l'écran d'enregistrement (en plus
  de la vitesse moyenne, calculée a posteriori sur l'écran Détail)
- **Permission de localisation en arrière-plan** (`ACCESS_BACKGROUND_LOCATION`,
  Android 10+) demandée séparément de la localisation au premier plan, avec un
  bandeau explicatif optionnel — l'app reste utilisable sans, mais le suivi
  s'interrompra si l'écran s'éteint sans cette permission
- **Aide à la désactivation de l'optimisation batterie** : bandeau redirigeant
  vers les réglages système si l'app n'est pas exemptée (nécessaire sur
  certains OEM pour ne pas voir le Foreground Service tué en tâche de fond)
- **Icône adaptative** (`ic_launcher_background`/`_foreground`) reprenant la
  palette de l'app, au lieu du placeholder plat initial

## Historique des corrections notables (session de mise au point)

- **Crash au démarrage de l'enregistrement** : `LocationManager.requestLocationUpdates`
  était appelé depuis un thread `Dispatchers.IO` sans Looper préparé → passage
  explicite de `Looper.getMainLooper()`.
- **Crash à l'arrêt de l'enregistrement** : la navigation Compose déclenchée par
  `onFinished` était appelée depuis une coroutine IO → `withContext(Dispatchers.Main)`.
- **Permission runtime absente** : sur Android 14+, démarrer un foreground service
  de type `location` sans permission déjà accordée lève une exception immédiate —
  ajout de la demande de permission (`ACCESS_FINE_LOCATION` + `POST_NOTIFICATIONS`)
  avant tout démarrage du service.
- **Bug de formatage GPX** : `String.format` sans `Locale` explicite produisait des
  décimales à virgule sur un téléphone en français (`987,4` au lieu de `987.4`),
  ce qui aurait généré un fichier GPX invalide — détecté par les tests unitaires.
- **Notification figée à `00:00:00` sans fix GPS** : la notification n'était mise
  à jour que depuis `onLocationChanged`, jamais appelé sans fix (immeuble,
  sous-bois...) — repéré en testant à l'intérieur sur le Moto G84 réel. Corrigé
  par un ticker périodique (1s) indépendant des remontées GPS.

## Pistes restantes (non bloquantes)

- **Téléchargement offline réel** : l'estimation du nombre de tuiles a été
  testée (`OfflineAreaScreen`), mais pas le téléchargement effectif (nécessite
  une session réseau plus longue vers OpenTopoMap).
- **Tests F-Droid** : pas de métadonnées `fastlane` (description, captures
  d'écran) ni de configuration de build reproductible pour la soumission à
  `fdroiddata`.

## Contraintes F-Droid respectées

- Aucune dépendance à Google Play Services (`LocationManager` natif, pas de
  `FusedLocationProviderClient`)
- Toutes les dépendances (AndroidX, osmdroid, MPAndroidChart) sont libres
- Licence GPL-3.0 (voir `LICENSE`)
- Aucun SDK analytics/tracker
