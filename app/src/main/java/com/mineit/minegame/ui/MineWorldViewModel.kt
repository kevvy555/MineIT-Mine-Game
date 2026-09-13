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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SequencedTickDiagnostics(
    val sequence: Long,
    val diagnostics: MineTickDiagnostics,
    val schedulerLateMs: Float,
    val tickIntervalMs: Float,
)

class MineWorldViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(MineWorldState())
    val state: StateFlow<MineWorldState> = mutableState.asStateFlow()
    private val mutableTickDiagnostics = MutableStateFlow<SequencedTickDiagnostics?>(null)
    val tickDiagnostics: StateFlow<SequencedTickDiagnostics?> = mutableTickDiagnostics.asStateFlow()

    private val stateMutex = Mutex()
    private var diggingJob: Job? = null
    private var tickSequence = 0L

    fun setSteering(value: Float) = updateDomainState { MineWorldController.setSteering(it, value) }

    fun setVerticalAngle(degrees: Float) = updateDomainState {
        MineWorldController.setVerticalAngle(it, degrees)
    }

    fun setDigSpeedMultiplier(multiplier: Float) = updateDomainState {
        MineWorldController.setDigSpeedMultiplier(it, multiplier)
    }

    fun turnHeadingBy(degrees: Float) = updateDomainState {
        MineWorldController.turnHeadingBy(it, degrees)
    }

    fun toggleDigging() {
        viewModelScope.launch {
            stateMutex.withLock {
                val current = mutableState.value
                if (current.isDigging) {
                    diggingJob?.cancel()
                    diggingJob = null
                    mutableState.value = MineWorldController.stopDigging(current)
                } else {
                    val started = MineWorldController.startDigging(current)
                    mutableState.value = started
                    if (started.isDigging) startDiggingLoop()
                }
            }
        }
    }

    fun reset() {
        diggingJob?.cancel()
        diggingJob = null
        viewModelScope.launch {
            stateMutex.withLock {
                mutableState.value = MineWorldController.reset()
                mutableTickDiagnostics.value = null
                tickSequence = 0L
            }
        }
    }

    private fun updateDomainState(transform: (MineWorldState) -> MineWorldState) {
        viewModelScope.launch {
            stateMutex.withLock {
                mutableState.value = transform(mutableState.value)
            }
        }
    }

    private fun startDiggingLoop() {
        diggingJob?.cancel()
        diggingJob = viewModelScope.launch {
            var nextDeadlineNanos = System.nanoTime() + DIG_TICK_NANOS
            var previousCompletionNanos = System.nanoTime()

            while (isActive) {
                delayUntil(nextDeadlineNanos)
                val tickStartedNanos = System.nanoTime()
                val schedulerLateMs =
                    ((tickStartedNanos - nextDeadlineNanos).coerceAtLeast(0L)) / 1_000_000f
                var diagnostics: MineTickDiagnostics? = null

                stateMutex.lock()
                try {
                    val snapshot = mutableState.value
                    if (!snapshot.isDigging) break
                    val nextState = withContext(Dispatchers.Default) {
                        MineWorldController.tick(snapshot, DIG_TICK_SECONDS) { diagnostics = it }
                    }
                    mutableState.value = nextState
                } finally {
                    stateMutex.unlock()
                }

                val completedNanos = System.nanoTime()
                diagnostics?.let { value ->
                    tickSequence += 1
                    mutableTickDiagnostics.value = SequencedTickDiagnostics(
                        sequence = tickSequence,
                        diagnostics = value,
                        schedulerLateMs = schedulerLateMs,
                        tickIntervalMs = (completedNanos - previousCompletionNanos) / 1_000_000f,
                    )
                }
                previousCompletionNanos = completedNanos
                if (!mutableState.value.isDigging) break

                nextDeadlineNanos += DIG_TICK_NANOS
                if (nextDeadlineNanos <= completedNanos) {
                    val missedIntervals = ((completedNanos - nextDeadlineNanos) / DIG_TICK_NANOS) + 1L
                    nextDeadlineNanos += missedIntervals * DIG_TICK_NANOS
                }
            }
        }
    }

    private suspend fun delayUntil(deadlineNanos: Long) {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos > 0L) {
            delay((remainingNanos + 999_999L) / 1_000_000L)
        }
    }

    private companion object {
        const val DIG_TICK_MILLIS = 100L
        const val DIG_TICK_NANOS = DIG_TICK_MILLIS * 1_000_000L
        const val DIG_TICK_SECONDS = 0.10f
    }
}
