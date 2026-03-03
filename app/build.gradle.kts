import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.smartfolder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartfolder"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += "tflite"
    }
}

val downloadModels by tasks.registering {
    val modelsDir = file("src/main/assets/models")
    val models = mapOf(
        "mobilenet_v3_small.tflite" to mapOf(
            "url" to "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite",
            "minSize" to "10000"
        ),
        "mobilenet_v3_large.tflite" to mapOf(
            "url" to "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_large/float32/latest/mobilenet_v3_large.tflite",
            "minSize" to "10000"
        )
    )

    outputs.dir(modelsDir)

    doLast {
        modelsDir.mkdirs()
        models.forEach { (name, config) ->
            val outputFile = File(modelsDir, name)
            val urlStr = config["url"]!!
            val minSize = config["minSize"]!!.toLong()

            if (outputFile.exists() && outputFile.length() >= minSize) {
                println("$name already exists (${outputFile.length()} bytes), skipping download")
                return@forEach
            }

            val maxRetries = 3
            for (attempt in 1..maxRetries) {
                println("Downloading $name (attempt $attempt/$maxRetries)...")
                val tempFile = File(modelsDir, "$name.tmp")
                try {
                    val connection = URL(urlStr).openConnection().apply {
                        connectTimeout = 30_000
                        readTimeout = 120_000
                    }
                    connection.getInputStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (tempFile.length() < minSize) {
                        tempFile.delete()
                        throw RuntimeException(
                            "$name download too small: ${tempFile.length()} bytes (expected >= $minSize)"
                        )
                    }

                    tempFile.renameTo(outputFile)
                    println("Downloaded $name (${outputFile.length()} bytes)")
                    return@forEach
                } catch (e: Exception) {
                    tempFile.delete()
                    if (attempt == maxRetries) {
                        throw RuntimeException("Failed to download $name after $maxRetries attempts: ${e.message}", e)
                    }
                    println("Attempt $attempt failed: ${e.message}. Retrying...")
                    Thread.sleep(2000L * attempt)
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadModels)
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coil for images
    implementation("io.coil-kt:coil-compose:2.5.0")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // MediaPipe for image embeddings
    implementation("com.google.mediapipe:tasks-vision:0.10.9")

    // DocumentFile for SAF
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
