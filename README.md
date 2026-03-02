# SmartFolder

Android app that organizes images into folders using on-device visual similarity. Select a reference folder with sample images, point it at an unsorted folder, and SmartFolder will suggest which images belong together -- all 100% offline with no cloud dependencies.

## How It Works

1. **Select folders** -- Pick a reference folder (A) containing example images and an unsorted folder (B) with images to classify.
2. **Index** -- The app generates ML embeddings for each image using MediaPipe's MobileNet V3 models.
3. **Analyze** -- SmartFolder computes a centroid of folder A's embeddings, pre-filters candidates, then runs k-nearest-neighbor scoring against individual images.
4. **Review** -- Browse suggestions one by one, accept or skip each image, then move accepted images to the reference folder.

### Scoring

Each unsorted image receives a combined score:

```
score = 0.4 * cosine(image, centroid_A) + 0.6 * mean(top-K similarities to A)
```

The top 3 most similar reference images are displayed alongside each suggestion for visual explanation.

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
      results/                   Review suggestions + move images
      settings/                  Threshold, model, dark mode
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

Instrumented tests cover:
- `FolderDao` -- CRUD operations
- `EmbeddingDao` -- embedding storage and queries

## Configuration

Available in the Settings screen:

| Setting | Default | Range |
|---------|---------|-------|
| Similarity threshold | 0.80 | 0.70 -- 0.95 |
| Embedding model | Fast (MobileNet V3 Small) | Fast / Precise |
| Dark mode | System default | On / Off |

## Requirements

- Android 9+ (API 28)
- Optimized for Samsung S23 Ultra

## License

Private repository.
