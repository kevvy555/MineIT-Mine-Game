package com.mineit.minegame.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mineit.minegame.domain.MineWorldController
import com.mineit.minegame.domain.MineWorldState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MineWorldViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(MineWorldState())
    val state: StateFlow<MineWorldState> = mutableState.asStateFlow()

    private var diggingJob: Job? = null

    fun setSteering(value: Float) {
        mutableState.update { MineWorldController.setSteering(it, value) }
    }

    fun setVerticalAngle(degrees: Float) {
        mutableState.update { MineWorldController.setVerticalAngle(it, degrees) }
    }

    fun toggleDigging() {
        if (mutableState.value.isDigging) {
            diggingJob?.cancel()
            diggingJob = null
            mutableState.update(MineWorldController::stopDigging)
            return
        }

        mutableState.update(MineWorldController::startDigging)
        if (!mutableState.value.isDigging) return

        diggingJob?.cancel()
        diggingJob = viewModelScope.launch {
            while (isActive && mutableState.value.isDigging) {
                delay(DIG_TICK_MILLIS)
                mutableState.update { state ->
                    MineWorldController.tick(state, DIG_TICK_SECONDS)
                }
            }
        }
    }

    fun reset() {
        diggingJob?.cancel()
        diggingJob = null
        mutableState.value = MineWorldController.reset()
    }

    private companion object {
        // Chunk-local remeshing makes a faster simulation cadence practical without rebuilding
        // the whole geological volume each time the cutter advances.
        const val DIG_TICK_MILLIS = 100L
        const val DIG_TICK_SECONDS = 0.10f
    }
}
