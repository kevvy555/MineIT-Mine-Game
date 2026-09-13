package com.mineit.minegame.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mineit.minegame.domain.MineTickDiagnostics
import com.mineit.minegame.domain.MineWorldController
import com.mineit.minegame.domain.MineWorldState
import kotlinx.coroutines.Dispatchers
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
        diggingJob = viewModelScope.launch(Dispatchers.Default) {
            var nextTickNanos = System.nanoTime() + DIG_TICK_NANOS

            while (isActive) {
                val waitNanos = nextTickNanos - System.nanoTime()
                if (waitNanos > 0L) {
                    delay((waitNanos / NANOS_PER_MILLI).coerceAtLeast(1L))
                }
                if (!isActive) break

                val snapshot = mutableState.value
                if (!snapshot.isDigging) break

                var diagnostics: MineTickDiagnostics? = null
                val nextState = MineWorldController.tick(snapshot, DIG_TICK_SECONDS) { diagnostics = it }

                // Controls may change while the domain tick is running on the worker thread. Only
                // publish if the snapshot is still current; otherwise preserve the newer input and
                // let the next fixed tick calculate from it instead of overwriting user intent.
                if (mutableState.compareAndSet(snapshot, nextState)) {
                    diagnostics?.let { value ->
                        tickSequence += 1
                        mutableTickDiagnostics.value = SequencedTickDiagnostics(tickSequence, value)
                    }
                }

                nextTickNanos += DIG_TICK_NANOS
                val now = System.nanoTime()
                if (nextTickNanos < now - DIG_TICK_NANOS) {
                    // Never run a burst of catch-up ticks after a slow device frame or debugger
                    // pause. Re-anchor to the fixed 10 Hz cadence instead.
                    nextTickNanos = now + DIG_TICK_NANOS
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
        // Domain work runs on Dispatchers.Default at a fixed 10 Hz schedule so material
        // classification cannot block Compose/UI input. Renderer meshing has its own workers.
        const val DIG_TICK_MILLIS = 100L
        const val DIG_TICK_SECONDS = 0.10f
        const val NANOS_PER_MILLI = 1_000_000L
        const val DIG_TICK_NANOS = DIG_TICK_MILLIS * NANOS_PER_MILLI
    }
}
