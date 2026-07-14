package com.smartfolder.presentation.visual

import com.smartfolder.presentation.screens.home.HomeUiState

enum class HomeHeroStage {
    SETUP,
    READY
}

data class HomeHeroContent(
    val stage: HomeHeroStage,
    val completedSteps: Int,
    val totalSteps: Int,
    val progress: Float,
    val isReady: Boolean
)

object HomeVisuals {

    fun buildHeroContent(state: HomeUiState): HomeHeroContent {
        val completedSteps = buildList {
            add(state.destinationFolders.isNotEmpty())
            add(state.sourceFolders.isNotEmpty())
        }.count { it }
        val totalSteps = 2
        val progress = completedSteps.toFloat() / totalSteps.toFloat()

        val stage = if (state.canStartTriage) HomeHeroStage.READY else HomeHeroStage.SETUP

        return HomeHeroContent(
            stage = stage,
            completedSteps = completedSteps,
            totalSteps = totalSteps,
            progress = progress,
            isReady = stage == HomeHeroStage.READY
        )
    }
}
