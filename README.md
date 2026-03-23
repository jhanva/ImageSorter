# ImageSorter

Android app that organizes images into folders using on-device visual similarity. Select a reference folder with sample images, point it at an unsorted folder, and ImageSorter will suggest which images belong together -- all 100% offline with no cloud dependencies.

## How It Works

ImageSorter supports two operation modes:

1. **Model mode (default)**
   - Select reference folder (A) and unsorted folder (B)
   - Index both folders
   - Analyze similarities
   - Review suggestions one by one and move accepted images to A
2. **Manual mode**
   - Select folders A and B
   - Load all images from B directly (no model scoring required)
   - Show the full batch from B in a scrollable thumbnail grid
   - Tap thumbnails to select images, then move the selected batch to A

During model review, you can stop early and move only what has been accepted so far.

### Manual Batch Review

Manual mode is designed for cases where the embedding suggestions are not reliable enough.

- Displays the complete contents of folder B in a lazy thumbnail grid suitable for large folders
- Uses direct thumbnail selection instead of one-by-one review
- Includes quick actions to select all images or clear the current selection
- Reuses the same move flow and write-permission prompts already used by the app

Manual mode does not use threshold filtering or similarity scoring in the results screen.

### Scoring

In model mode, each unsorted image receives a combined score:

```
score = 0.2 * centroidScore + 0.3 * topKMean + 0.5 * topKMax
```

Additional details:
- Default `topK = 5`
- Centroid pre-filter uses `threshold * 0.5`
- Top similar reference matches are shown alongside each suggestion for visual explanation

## Architecture

Clean Architecture with four layers:

```
Presentation  (Jetpack Compose + ViewModels + StateFlow)
     |
   Domain      (Use Cases, Repository interfaces, Models)
     |
    Data        (Room, DataStore, SAF, Repository implementations)
     |
     ML         (MediaPipe ImageEmbedder, Similarity, Centroid)
```

### Tech Stack

| Component | Library |
|-----------|---------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (with KSP) |
| Database | Room |
| Preferences | DataStore |
| Background work | WorkManager |
| Image loading | Coil |
| ML inference | MediaPipe Tasks Vision |
| File access | SAF (Storage Access Framework) |
| Navigation | Navigation Compose |

### Key Design Decisions

- **SAF-only file access** -- No dangerous permissions. Uses `ACTION_OPEN_DOCUMENT_TREE` with persistable URI permissions.
- **ContentResolver queries** -- Folder listing uses `DocumentsContract` queries instead of `DocumentFile.listFiles()` for 10-100x faster enumeration of large folders.
- **MediaStore-first listing for folder analysis/indexing** -- Uses `documentId`-based MediaStore lookup first, with SAF fallback for compatibility.
- **GPU with CPU fallback** -- MediaPipe attempts GPU delegate first, falls back to CPU automatically.
- **Incremental indexing** -- Embeddings are cached in Room keyed by `contentHash` (file size + last modified). Only changed or new images are re-processed.
- **Batch operations** -- Image registration and embedding lookups use chunked batch queries to minimize database round-trips.
- **TransactionRunner** -- Cross-repository atomic operations via an injectable interface, keeping domain layer independent of Room.

## Project Structure

```
app/src/main/java/com/smartfolder/
  SmartFolderApp.kt              Application class (Hilt + WorkManager)
  di/                            Hilt modules
  data/
    local/db/                    Room database, DAOs, entities
    local/datastore/             DataStore preferences
    repository/                  Repository implementations
    saf/                         SAF file operations
  domain/
    model/                       Domain models
    repository/                  Repository interfaces
    usecase/                     Business logic
  ml/                            ML pipeline (embedder, similarity, centroid)
  worker/                        WorkManager workers
  presentation/
    MainActivity.kt              Entry point + SAF launcher
    navigation/                  NavGraph + Screen routes
    theme/                       Material 3 theme
    components/                  Reusable Compose components
    screens/
      home/                      Folder selection + indexing
      analysis/                  Analysis progress
      results/                   Model review or manual batch selection + move images
      settings/                  Threshold, model, execution profile, manual mode, dark mode
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

ML models (MobileNet V3 small and large) are downloaded automatically during the first build via a Gradle task hooked into `preBuild`. The download includes retry logic, timeouts, and file size validation.

## Versioning And Releases

- Canonical app version lives in `version.properties`.
- Local or CI builds can override it with `VERSION_NAME` and `VERSION_CODE` as environment variables or Gradle properties.
- Pushing a Git tag like `v0.2.0` triggers the GitHub Actions workflow in `.github/workflows/release.yml`.
- Each tagged build publishes a GitHub Release with the debug APK, the release APK, and `SHA256SUMS.txt`.
- If repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` are configured, the workflow signs the release APK before publishing it.

Example:

```bash
git tag v0.2.0
git push origin v0.2.0
```

### Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

Unit tests cover:
- `SimilarityCalculator` -- cosine similarity and scoring
- `CentroidCalculator` -- centroid computation and normalization
- `EmbeddingNormalizer` -- L2 normalization
- `IndexFolderUseCase` -- indexing pipeline
- `AnalyzeImagesUseCase` -- analysis pipeline
- `MoveImagesUseCase` -- file move operations
- `ResultsViewModel` -- manual batch selection state and selected-image resolution

Instrumented tests cover:
- `FolderDao` -- CRUD operations
- `EmbeddingDao` -- embedding storage and queries

## Configuration

Available in the Settings screen:

| Setting | Default | Range |
|---------|---------|-------|
| Similarity threshold | 0.55 | 0.30 -- 0.95 |
| Embedding model | Fast (MobileNet V3 Small) | Fast / Precise |
| Execution profile | Balanced | Battery / Balanced / Performance |
| Manual mode | Off | On / Off |
| Dark mode | Off | On / Off |

Notes:
- In manual mode, folder B is shown as a full thumbnail grid instead of scored suggestions.
- Threshold only affects model mode.

## Requirements

- Android 9+ (API 28)
- Optimized for Samsung S23 Ultra

## License

Private repository.
