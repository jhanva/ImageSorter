# ImageSorter

Android app that organizes images into folders using on-device visual similarity. Pick one or more destination folders (your organized categories, for example one folder per anime or game), pick one or more source folders (the unsorted ones), and ImageSorter suggests where each image belongs -- all 100% offline with no cloud dependencies.

## How It Works

1. Add destination folders (references the model compares against) and source folders (images to route).
2. Tap Analyze. The app indexes any folder that is missing embeddings for the active model, then compares every source image against every destination folder.
3. Review the suggestions grouped by destination, adjust anything the model got wrong, and move the accepted images in one batch.
4. Images below the similarity threshold land in an unassigned group where you can route them manually with one-tap candidate chips.

You can stop the review at any point and move only what has been accepted so far. The last move can be undone.

### Scoring

Each source image is compared against every destination folder:

- Centroid pre-filter: destinations whose centroid similarity is below `threshold * 0.6` are discarded early.
- For the remaining destinations, the top-K (default 5) most similar reference images are found and combined into a score:

```
score = 0.20 * centroidScore + 0.25 * topKMean + 0.20 * topKMax + 0.35 * referenceSupport
```

- `referenceSupport` is the mean of the supporting matches after the best hit, which rewards consensus across several references over a single spiky match. Its weight is scaled down for destination folders with very few reference images.
- The best destination wins if its score passes the threshold; otherwise the image is left unassigned with its closest candidates stored for quick manual routing.
- The second-best destination score is kept as a confidence margin shown during review.

### Embedding models

| Model | Backend | Best for |
|-------|---------|----------|
| Semantic (MobileCLIP-S0) | ONNX Runtime (NNAPI when available) | Anime, game art, illustrations. Default. |
| Anime (CCIP caformer) | ONNX Runtime | Character identity: folders organized as one character or series per folder, single character per image |
| Fast (MobileNet V3 Small) | MediaPipe TFLite | Quick passes over photographic content |
| Precise (MobileNet V3 Large) | MediaPipe TFLite | Photographic content, higher quality |

Embeddings are cached in Room per model and per `contentHash` (file size + last modified), so re-indexing only processes new or changed images.

## Architecture

Clean Architecture with four layers:

```
Presentation  (Jetpack Compose + ViewModels + StateFlow)
     |
   Domain      (Use Cases, Repository interfaces, Models)
     |
    Data        (Room, DataStore, SAF, Repository implementations)
     |
     ML         (MediaPipe ImageEmbedder / ONNX MobileCLIP, Similarity, Centroid)
```

### Tech Stack

| Component | Library |
|-----------|---------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (with KSP) |
| Database | Room |
| Preferences | DataStore |
| Image loading | Coil |
| ML inference | MediaPipe Tasks Vision + ONNX Runtime |
| File access | SAF (Storage Access Framework) + MediaStore |
| Navigation | Navigation Compose |

### Key Design Decisions

- **MediaStore-driven folder selection** -- The app enumerates image folders from MediaStore and lets the user choose from that indexed list instead of the system document tree picker.
- **ContentResolver queries** -- Folder listing uses `DocumentsContract` queries instead of `DocumentFile.listFiles()` for 10-100x faster enumeration of large folders.
- **One-tap pipeline** -- Analyze automatically indexes any folder that is missing embeddings for the active model before comparing, so there is no separate manual indexing step.
- **GPU/NNAPI with CPU fallback** -- Accelerated delegates are attempted first and fall back to CPU automatically.
- **Incremental indexing** -- Embeddings cached in Room keyed by `contentHash`; stale rows for deleted files are cleaned up on each pass.
- **Batch operations** -- Image registration and embedding lookups use chunked batch queries to minimize database round-trips.
- **TransactionRunner** -- Cross-repository atomic operations via an injectable interface, keeping domain layer independent of Room.

## Project Structure

```
app/src/main/java/com/smartfolder/
  SmartFolderApp.kt              Application class (Hilt)
  di/                            Hilt modules
  data/
    local/db/                    Room database, DAOs, entities
    local/datastore/             DataStore preferences
    media/                       MediaStore folder provider
    repository/                  Repository implementations
    saf/                         SAF file operations
  domain/
    model/                       Domain models
    repository/                  Repository interfaces
    usecase/                     Business logic
  ml/                            ML pipeline (embedders, similarity, centroid)
  presentation/
    MainActivity.kt              Entry point
    navigation/                  NavGraph + Screen routes
    theme/                       Material 3 theme (brand palette + dynamic color)
    components/                  Reusable Compose components
    screens/
      home/                      Folder selection + one-tap analyze
      analysis/                  Indexing + analysis progress
      results/                   Grouped review, quick selection, move + undo
      settings/                  Threshold, model, execution profile, appearance
```

## Building

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34

### Build

```bash
./gradlew assembleDebug
```

ML models (MobileNet V3 small/large and MobileCLIP-S0) are downloaded automatically during the first build via a Gradle task hooked into `preBuild`, with retry logic, timeouts, and file size validation.

## Versioning And Releases

- Canonical app version lives in `version.properties`.
- Local or CI builds can override it with `VERSION_NAME` and `VERSION_CODE` as environment variables or Gradle properties.
- Pushing a Git tag like `v0.3.0` triggers the GitHub Actions workflow in `.github/workflows/release.yml`, which publishes debug and release APKs plus `SHA256SUMS.txt`.
- If the signing secrets are configured, the workflow signs the release APK before publishing it.

### Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Configuration

Available in the Settings screen:

| Setting | Default | Range |
|---------|---------|-------|
| Similarity threshold | 0.55 | 0.30 -- 0.95 |
| Embedding model | Semantic (MobileCLIP-S0) | Semantic / Fast / Precise |
| Execution profile | Balanced | Battery / Balanced / Performance |
| Dynamic color (Material You) | Off | On / Off (Android 12+) |
| Dark mode | Off | On / Off |

Notes:
- Folder selection is limited to image folders discoverable through MediaStore. The app does not expose the system document tree picker.
- The UI is localized in English and Spanish.

## Requirements

- Android 9+ (API 28)
- Optimized for Samsung S23 Ultra

## License

Private repository.
