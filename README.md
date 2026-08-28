# RandoGPX — projet Android

Ce projet a été généré à partir de la spécification définie en amont (voir
`specification-app-rando.md`), puis **compilé, corrigé et testé de bout en bout
sur émulateur Android 15 (API 35)**. Le flow complet (permissions runtime,
start/pause/resume/stop, tracé live, correction EGM96, export GPX, sélection de
zone offline) a été validé manuellement.

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

## Pistes restantes (non bloquantes)

- **Icône de l'app** : `drawable/ic_launcher.xml` est un placeholder minimal à
  remplacer par un vrai design (adaptive icon recommandé pour le Play
  Store/F-Droid).
- **Vérifier le comportement batterie** : sur certains OEM (Xiaomi, Huawei...),
  les Foreground Service sont tués malgré tout par des surcouches agressives —
  prévoir un message d'aide utilisateur pour désactiver l'optimisation batterie.
- **`ACCESS_BACKGROUND_LOCATION`** : la demande de permission actuelle ne couvre
  que la localisation au premier plan ; une demande séparée pour l'arrière-plan
  (nécessaire pour un tracking fiable écran éteint) reste à ajouter avec le
  dialogue dédié Android 10+.

## Contraintes F-Droid respectées

- Aucune dépendance à Google Play Services (`LocationManager` natif, pas de
  `FusedLocationProviderClient`)
- Toutes les dépendances (AndroidX, osmdroid, MPAndroidChart) sont libres
- Licence GPL-3.0 (voir `LICENSE`)
- Aucun SDK analytics/tracker
