# Rando GPX

*[Version française](README.md)*

Android hiking-tracking app, **free/libre and with no dependency on Google
Play Services**. Location via the standard Android API (`LocationManager`),
no tracker, no telemetry.

## Screenshots

<!-- Replace with real screenshots, e.g.:
<p float="left">
  <img src="docs/screenshots/home.png" width="200" />
  <img src="docs/screenshots/detail.png" width="200" />
  <img src="docs/screenshots/map.png" width="200" />
</p>
-->

## Features

- Background GPS recording (screen off), with a live notification showing distance and duration
- Live tracking on a map (OpenTopoMap tiles, hiking-oriented)
- EGM96 geoid altitude correction, for elevation gain/loss more accurate than raw GPS altitude
- Export and import in standard GPX 1.1 format
- Hike history with detailed statistics: distance, elevation gain/loss, average speed and moving speed (excluding stops), min/max altitude, duration
- Full route review map and elevation profile for each hike
- Hikes grouped by mountain range ("massif"), with filters by range and by year
- Selecting a map area to make available offline
- French and English interface, selected automatically based on the phone's language

## Installation

### From source

```bash
git clone <repo-url>
cd rando-gpx
./gradlew :app:assembleDebug
```

Opening the folder directly in Android Studio also works. Requires an
Android 8.0+ device or emulator (minSdk 26).

### F-Droid

Coming soon — the app is being prepared for submission to the official
F-Droid repository.

## Privacy and licensing

- No dependency on Google Play Services: location uses the standard Android
  API (`LocationManager`), not `FusedLocationProviderClient`
- No analytics or tracking SDK
- All dependencies (AndroidX, [osmdroid](https://github.com/osmdroid/osmdroid),
  [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)) are free/libre
- EGM96 geoid grid: [GeographicLib](https://geographiclib.sourceforge.io/)
  distribution (BSD license) of public-domain NGA WW15MGH data
- Project licensed under **GNU GPL v3.0 or later** — see [LICENSE](LICENSE)

## Contributing

Feedback, bug reports and suggestions are welcome via the repo's issues.
