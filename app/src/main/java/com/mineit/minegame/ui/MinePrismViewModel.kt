package com.mineit.minegame.ui

import androidx.lifecycle.ViewModel
import com.mineit.minegame.domain.MinePrismController
import com.mineit.minegame.domain.MinePrismState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MinePrismViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(MinePrismState())
    val state: StateFlow<MinePrismState> = mutableState.asStateFlow()

    fun setCutDepth(depthMetres: Float) {
        mutableState.update { MinePrismController.setCutDepth(it, depthMetres) }
    }

    fun toggleExploded() {
        mutableState.update(MinePrismController::toggleExploded)
    }

    fun selectLevel(levelId: String) {
        mutableState.update { MinePrismController.selectLevel(it, levelId) }
    }

    fun closeLevel() {
        mutableState.update(MinePrismController::closeLevel)
    }

    fun reset() {
        mutableState.value = MinePrismController.reset()
    }
}
