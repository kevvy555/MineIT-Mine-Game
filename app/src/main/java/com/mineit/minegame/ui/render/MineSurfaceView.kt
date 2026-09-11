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

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                mineRenderer.zoom(detector.scaleFactor)
                requestRender()
                return true
            }
        },
    )

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(mineRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setWorldState(state: MineWorldState) {
        mineRenderer.setWorldState(state)
        requestRender()
    }

    fun setClip(axis: ClipAxis, fraction: Float, flipped: Boolean, enabled: Boolean) {
        mineRenderer.setClip(axis, fraction, flipped, enabled)
        requestRender()
    }

    fun resetCamera() {
        mineRenderer.resetCamera()
        requestRender()
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
                    requestRender()
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
