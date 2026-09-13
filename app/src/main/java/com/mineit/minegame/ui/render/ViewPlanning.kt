package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldState

internal enum class CameraMode {
    ORBIT,
    DIGGER_POV,
}

internal enum class OrbitGestureMode {
    ROTATE,
    PAN,
}

internal data class SliceFractions(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    fun forAxis(axis: ClipAxis): Float = when (axis) {
        ClipAxis.X -> x
        ClipAxis.Y -> y
        ClipAxis.Z -> z
    }

    fun withAxis(axis: ClipAxis, value: Float): SliceFractions = when (axis) {
        ClipAxis.X -> copy(x = value)
        ClipAxis.Y -> copy(y = value)
        ClipAxis.Z -> copy(z = value)
    }
}

/**
 * Transient CT inspection state. Each axis retains its own position, enabled state and cut side so
 * X/Y/Z can be combined into a true tri-planar cut without changing canonical mine state.
 */
internal data class SliceConfiguration(
    val fractions: SliceFractions = SliceFractions(0.18f, 0.18f, 0.18f),
    val enabledAxes: Set<ClipAxis> = setOf(ClipAxis.X),
    val flippedAxes: Set<ClipAxis> = emptySet(),
) {
    fun fraction(axis: ClipAxis): Float = fractions.forAxis(axis)

    fun isEnabled(axis: ClipAxis): Boolean = axis in enabledAxes

    fun isFlipped(axis: ClipAxis): Boolean = axis in flippedAxes

    fun withFraction(axis: ClipAxis, value: Float): SliceConfiguration = copy(
        fractions = fractions.withAxis(axis, value.coerceIn(MIN_SLICE_FRACTION, MAX_SLICE_FRACTION)),
    )

    fun toggleAxis(axis: ClipAxis): SliceConfiguration = copy(
        enabledAxes = enabledAxes.toggle(axis),
    )

    fun toggleFlipped(axis: ClipAxis): SliceConfiguration = copy(
        flippedAxes = flippedAxes.toggle(axis),
    )

    fun withFollowFractions(followFractions: SliceFractions): SliceConfiguration = copy(
        fractions = followFractions,
    )

    private fun Set<ClipAxis>.toggle(axis: ClipAxis): Set<ClipAxis> = if (axis in this) {
        this - axis
    } else {
        this + axis
    }

    companion object {
        const val MIN_SLICE_FRACTION = 0.02f
        const val MAX_SLICE_FRACTION = 0.98f
    }
}

internal object TunnelRenderPlanner {
    fun shouldRenderOverview(rockVisible: Boolean, tunnelVisible: Boolean): Boolean =
        !rockVisible && tunnelVisible
}

internal object FollowSlicePlanner {
    fun forDigger(state: MineWorldState): SliceFractions = forPoint(state, state.tunnel.end)

    fun forPoint(state: MineWorldState, point: MinePoint3D): SliceFractions {
        val bounds = state.bounds
        return SliceFractions(
            x = fraction(point.x, bounds.minX, bounds.width),
            y = fraction(point.y, bounds.minY, bounds.height),
            z = fraction(point.z, bounds.minZ, bounds.depth),
        )
    }

    private fun fraction(value: Float, minimum: Float, span: Float): Float {
        if (span <= 0f) return 0.5f
        return ((value - minimum) / span).coerceIn(
            SliceConfiguration.MIN_SLICE_FRACTION,
            SliceConfiguration.MAX_SLICE_FRACTION,
        )
    }
}
