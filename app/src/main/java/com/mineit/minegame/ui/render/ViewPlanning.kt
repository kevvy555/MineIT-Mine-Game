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
        return ((value - minimum) / span).coerceIn(MIN_SLICE_FRACTION, MAX_SLICE_FRACTION)
    }

    private const val MIN_SLICE_FRACTION = 0.02f
    private const val MAX_SLICE_FRACTION = 0.98f
}
