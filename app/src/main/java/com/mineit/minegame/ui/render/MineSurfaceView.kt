package com.mineit.minegame.ui.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.mineit.minegame.domain.MineWorldState

internal class MineSurfaceView(context: Context) : GLSurfaceView(context) {
    private val mineRenderer = MineWorldGlRenderer()
    private var lastX = 0f
    private var lastY = 0f
    private var performanceListener: ((RenderPerformanceStats) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                mineRenderer.zoom(detector.scaleFactor)
                return true
            }
        },
    )

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(mineRenderer)
        // Continuous mode is deliberate while performance work is active: it provides a genuine
        // FPS signal and keeps camera/x-ray motion smooth even while CPU mesh work runs off-thread.
        renderMode = RENDERMODE_CONTINUOUSLY
        mineRenderer.setPerformanceListener { stats ->
            post { performanceListener?.invoke(stats) }
        }
    }

    fun setWorldState(state: MineWorldState) {
        mineRenderer.setWorldState(state)
    }

    fun setClip(axis: ClipAxis, fraction: Float, flipped: Boolean, enabled: Boolean) {
        mineRenderer.setClip(axis, fraction, flipped, enabled)
    }

    fun setFollowDigger(enabled: Boolean) {
        mineRenderer.setFollowDigger(enabled)
    }

    fun setCameraMode(mode: CameraMode) {
        mineRenderer.setCameraMode(mode)
    }

    fun setRockVisible(visible: Boolean) {
        mineRenderer.setRockVisible(visible)
    }

    fun setPerformanceListener(listener: ((RenderPerformanceStats) -> Unit)?) {
        performanceListener = listener
    }

    fun resetCamera() {
        mineRenderer.resetCamera()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    mineRenderer.rotate(dx, dy)
                }
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                if (event.pointerCount > 0) {
                    lastX = event.x
                    lastY = event.y
                }
            }
        }

        return true
    }
}
