package com.mineit.minegame.domain

object MinePrismController {
    const val MIN_DEPTH_METRES = 0f
    const val MAX_DEPTH_METRES = 360f

    fun setCutDepth(state: MinePrismState, depthMetres: Float): MinePrismState {
        val boundedDepth = depthMetres.coerceIn(MIN_DEPTH_METRES, MAX_DEPTH_METRES)
        val selectedStillVisible = state.selectedLevel?.depthMetres?.let { it <= boundedDepth } ?: true
        return state.copy(
            cutDepthMetres = boundedDepth,
            selectedLevelId = if (selectedStillVisible) state.selectedLevelId else null,
        )
    }

    fun toggleExploded(state: MinePrismState): MinePrismState =
        state.copy(exploded = !state.exploded)

    fun selectLevel(state: MinePrismState, levelId: String): MinePrismState {
        val level = state.levels.firstOrNull { it.id == levelId } ?: return state
        if (level.depthMetres > state.cutDepthMetres) {
            return state
        }
        return state.copy(selectedLevelId = level.id)
    }

    fun closeLevel(state: MinePrismState): MinePrismState =
        state.copy(selectedLevelId = null)

    fun reset(): MinePrismState = MinePrismState()
}
