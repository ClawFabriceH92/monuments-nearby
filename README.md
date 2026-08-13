# Monuments Nearby

App Android (Kotlin + Jetpack Compose) qui détecte les monuments historiques autour de ta position GPS et te permet de les écouter (audioguide TTS hors-ligne) et de rejoindre via Google Maps.

**Sources 100 % gratuites, zéro clé API** : Overpass API (OpenStreetMap).

## Fonctionnalités (v0.2)

- Détection de la position GPS (FusedLocationProvider + fallback LocationManager)
- Liste des monuments historiques dans un rayon de 3 km (filtre sémantique OSM : monuments, châteaux, églises, ruines, musées…), triée par distance
- **Enrichissement Wikidata/Wikimedia** (ontologie) : type sémantique (château, sculpture, hôtel particulier…), description FR, date de construction, photo Commons
- **Audioguide** : lecture vocale TTS Android natif (hors-ligne)
- **Itinéraire** : ouverture Google Maps sur le monument
- États complets : permission refusée, position introuvable, erreur réseau, liste vide

## À venir (roadmap validée)

- Géofencing + notifications (alerte quand un monument passe à proximité)
- Mode hors-ligne complet (cache des zones + narrations)
- Filtres (type, période, distance)
- Carnet de visites + partage
- ~~IA conversationnelle / récits personnalisés~~ → écarté pour l'instant

## Build

Prérequis : JDK 17+, Android SDK 35.

```bash
./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

Compatibilité : AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, minSdk 29, targetSdk 35 — mêmes versions que transcripto-stream.

## Structure

```
app/src/main/java/com/fabrice/monumentsnearby/
├── MainActivity.kt          # permissions + point d'entrée
├── data/
│   ├── Monument.kt          # modèle
│   ├── OverpassClient.kt    # requête Overpass + parsing (miroirs de secours)
│   └── WikidataClient.kt    # enrichissement ontologique Wikidata/Wikimedia
├── location/
│   └── LocationHelper.kt    # FusedLocationProvider + fallback LocationManager
├── tts/
│   └── GuideSpeaker.kt      # audioguide TextToSpeech FR
└── ui/
    ├── MonumentsViewModel.kt
    ├── MonumentsScreen.kt   # liste Compose Material3 (photos Coil)
    └── theme/Theme.kt
```
