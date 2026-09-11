package com.mineit.minegame.ui

import androidx.lifecycle.ViewModel
import com.mineit.minegame.domain.MineWorldController
import com.mineit.minegame.domain.MineWorldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MineWorldViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(MineWorldState())
    val state: StateFlow<MineWorldState> = mutableState.asStateFlow()

    fun setAzimuth(degrees: Float) {
        mutableState.update { MineWorldController.setAzimuth(it, degrees) }
    }

    fun setDip(degrees: Float) {
        mutableState.update { MineWorldController.setDip(it, degrees) }
    }

    fun dig() {
        mutableState.update(MineWorldController::dig)
    }

    fun reset() {
        mutableState.value = MineWorldController.reset()
    }
}
