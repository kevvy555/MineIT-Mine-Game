package com.mineit.minegame.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mineit.minegame.domain.MineTickDiagnostics
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

data class SequencedTickDiagnostics(val sequence: Long, val diagnostics: MineTickDiagnostics)

class MineWorldViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(MineWorldState())
    val state: StateFlow<MineWorldState> = mutableState.asStateFlow()
    private val mutableTickDiagnostics = MutableStateFlow<SequencedTickDiagnostics?>(null)
    val tickDiagnostics: StateFlow<SequencedTickDiagnostics?> = mutableTickDiagnostics.asStateFlow()

    private var diggingJob: Job? = null
    private var tickSequence = 0L

    fun setSteering(value: Float) {
        mutableState.update { MineWorldController.setSteering(it, value) }
    }

    fun setVerticalAngle(degrees: Float) {
        mutableState.update { MineWorldController.setVerticalAngle(it, degrees) }
    }

    fun setDigSpeedMultiplier(multiplier: Float) {
        mutableState.update { MineWorldController.setDigSpeedMultiplier(it, multiplier) }
    }

    fun turnHeadingBy(degrees: Float) {
        mutableState.update { MineWorldController.turnHeadingBy(it, degrees) }
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
                var diagnostics: MineTickDiagnostics? = null
                mutableState.update { state ->
                    MineWorldController.tick(state, DIG_TICK_SECONDS) { diagnostics = it }
                }
                diagnostics?.let { value ->
                    tickSequence += 1
                    mutableTickDiagnostics.value = SequencedTickDiagnostics(tickSequence, value)
                }
            }
        }
    }

    fun reset() {
        diggingJob?.cancel()
        diggingJob = null
        mutableState.value = MineWorldController.reset()
        mutableTickDiagnostics.value = null
    }

    private companion object {
        // Domain state moves smoothly at 10 Hz. The renderer independently throttles expensive
        // geological remeshing, so machine motion no longer forces a mesh rebuild every tick.
        const val DIG_TICK_MILLIS = 100L
        const val DIG_TICK_SECONDS = 0.10f
    }
}
