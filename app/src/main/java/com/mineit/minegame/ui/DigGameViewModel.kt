package com.mineit.minegame.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mineit.minegame.domain.DigSimulation
import com.mineit.minegame.domain.DiggerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DigGameViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(DiggerState())
    val state: StateFlow<DiggerState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            var previousNanos = System.nanoTime()
            while (isActive) {
                delay(FRAME_DELAY_MILLIS)
                val now = System.nanoTime()
                val deltaSeconds = (now - previousNanos) / NANOS_PER_SECOND
                previousNanos = now
                mutableState.update { DigSimulation.tick(it, deltaSeconds) }
            }
        }
    }

    fun setTargetHeading(degrees: Float) {
        mutableState.update { DigSimulation.setTargetHeading(it, degrees) }
    }

    fun startDigging() {
        mutableState.update(DigSimulation::start)
    }

    fun stopDigging() {
        mutableState.update(DigSimulation::stop)
    }

    fun reset() {
        mutableState.value = DigSimulation.reset()
    }

    private companion object {
        const val FRAME_DELAY_MILLIS = 16L
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}
