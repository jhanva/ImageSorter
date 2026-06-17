package com.smartfolder.presentation.visual

import com.smartfolder.presentation.screens.home.HomeUiState

data class HomeHeroContent(
    val title: String,
    val subtitle: String,
    val primaryActionLabel: String,
    val progressLabel: String,
    val progress: Float,
    val isReady: Boolean
)

object HomeVisuals {

    fun buildHeroContent(state: HomeUiState): HomeHeroContent {
        val completedSteps = buildList {
            add(state.destinationFolders.isNotEmpty())
            add(state.destinationFolders.any { it.imageCount > 0 && it.indexedCount >= it.imageCount })
            add(state.sourceFolders.isNotEmpty())
            add(state.sourceFolders.any { it.imageCount > 0 && it.indexedCount >= it.imageCount })
        }.count { it }
        val totalSteps = 4
        val progress = completedSteps.toFloat() / totalSteps.toFloat()

        return when {
            state.isIndexingDestinations || state.isIndexingSources -> HomeHeroContent(
                title = "Indexing your folders",
                subtitle = "Keep the app open while ImageSorter builds local embeddings for the selected library.",
                primaryActionLabel = "Indexing in progress",
                progressLabel = "$completedSteps of $totalSteps setup steps complete",
                progress = progress,
                isReady = false
            )

            state.canAnalyze -> HomeHeroContent(
                title = "Library ready for routing",
                subtitle = "Everything is indexed locally. Run analysis to route your source images into the right destinations.",
                primaryActionLabel = "Analyze library",
                progressLabel = "$completedSteps of $totalSteps setup steps complete",
                progress = progress,
                isReady = true
            )

            else -> HomeHeroContent(
                title = "Build your offline sorter",
                subtitle = "Choose destination and source folders, then index them once for local matching.",
                primaryActionLabel = "Choose folders",
                progressLabel = "$completedSteps of $totalSteps setup steps complete",
                progress = progress,
                isReady = false
            )
        }
    }
}
