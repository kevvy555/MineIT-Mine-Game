package com.mineit.minegame.ui.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.mineit.minegame.domain.MineWorldState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal enum class ClipAxis { X, Y, Z }

internal class MineWorldGlRenderer : GLSurfaceView.Renderer {
    @Volatile
    private var worldState = MineWorldState()

    @Volatile
    private var meshDirty = true

    @Volatile
    private var capDirty = true

    @Volatile
    private var machineDirty = true

    @Volatile
    private var clipAxis = ClipAxis.Z

    @Volatile
    private var clipFraction = 0.18f

    @Volatile
    private var clipFlipped = false

    @Volatile
    private var clipEnabled = true

    @Volatile
    private var yawDegrees = 38f

    @Volatile
    private var pitchDegrees = 24f

    @Volatile
    private var zoomScale = 1f

    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var program = 0

    private var rockBuffer: FloatBuffer? = null
    private var rockVertexCount = 0
    private var capBuffer: FloatBuffer? = null
    private var capVertexCount = 0
    private var machineBuffer: FloatBuffer? = null
    private var machineVertexCount = 0

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewModelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun setWorldState(state: MineWorldState) {
        val previous = worldState
        val geometryChanged = state.tunnel != previous.tunnel ||
            state.extent != previous.extent ||
            state.oreBody != previous.oreBody
        val oreDiscoveryChanged = state.oreBodyDiscovered != previous.oreBodyDiscovered
        val machineChanged = state.tunnel.end != previous.tunnel.end ||
            state.headingDegrees != previous.headingDegrees ||
            state.verticalAngleDegrees != previous.verticalAngleDegrees

        worldState = state
        if (geometryChanged) meshDirty = true
        if (geometryChanged || oreDiscoveryChanged) capDirty = true
        if (geometryChanged || machineChanged) machineDirty = true
    }

    fun setClip(axis: ClipAxis, fraction: Float, flipped: Boolean, enabled: Boolean) {
        val boundedFraction = fraction.coerceIn(0f, 1f)
        if (
            axis != clipAxis ||
            boundedFraction != clipFraction ||
            flipped != clipFlipped ||
            enabled != clipEnabled
        ) {
            capDirty = true
        }
        clipAxis = axis
        clipFraction = boundedFraction
        clipFlipped = flipped
        clipEnabled = enabled
    }

    fun rotate(deltaX: Float, deltaY: Float) {
        yawDegrees = (yawDegrees + (deltaX * 0.35f)) % 360f
        pitchDegrees = (pitchDegrees + (deltaY * 0.30f)).coerceIn(-82f, 82f)
    }

    fun zoom(scaleFactor: Float) {
        if (scaleFactor <= 0f) return
        zoomScale = (zoomScale / scaleFactor).coerceIn(0.42f, 2.8f)
    }

    fun resetCamera() {
        yawDegrees = 38f
        pitchDegrees = 24f
        zoomScale = 1f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.055f, 0.075f, 0.095f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        configureDomainToOpenGlMatrix()
        meshDirty = true
        capDirty = true
        machineDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = max(1, width)
        surfaceHeight = max(1, height)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val state = worldState
        if (meshDirty) {
            val mesh = MineMeshBuilder.build(state)
            rockBuffer = createBuffer(mesh)
            rockVertexCount = mesh.vertexCount
            meshDirty = false
        }
        if (capDirty) {
            if (clipEnabled) {
                val cap = MineMeshBuilder.buildCutCap(
                    state = state,
                    axis = clipAxis,
                    fraction = clipFraction,
                    flipped = clipFlipped,
                )
                capBuffer = createBuffer(cap)
                capVertexCount = cap.vertexCount
            } else {
                capBuffer = null
                capVertexCount = 0
            }
            capDirty = false
        }
        if (machineDirty) {
            val machine = MineMeshBuilder.buildMachine(state)
            machineBuffer = createBuffer(machine)
            machineVertexCount = machine.vertexCount
            machineDirty = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return

        updateMatrices(state)
        GLES20.glUseProgram(program)

        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(program, "uMvp"),
            1,
            false,
            mvpMatrix,
            0,
        )
        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(program, "uModel"),
            1,
            false,
            modelMatrix,
            0,
        )

        val bounds = state.bounds
        val clipValue = when (clipAxis) {
            ClipAxis.X -> bounds.minX + (bounds.width * clipFraction)
            ClipAxis.Y -> bounds.minY + (bounds.height * clipFraction)
            ClipAxis.Z -> bounds.minZ + (bounds.depth * clipFraction)
        }
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uClipAxis"), clipAxis.ordinal)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uClipValue"), clipValue)
        GLES20.glUniform1f(
            GLES20.glGetUniformLocation(program, "uClipDirection"),
            if (clipFlipped) -1f else 1f,
        )
        GLES20.glUniform1f(
            GLES20.glGetUniformLocation(program, "uClipEnabled"),
            if (clipEnabled) 1f else 0f,
        )

        drawMesh(rockBuffer, rockVertexCount)
        drawMesh(capBuffer, capVertexCount)
        drawMesh(machineBuffer, machineVertexCount)
    }

    private fun drawMesh(buffer: FloatBuffer?, vertexCount: Int) {
        if (buffer == null || vertexCount <= 0) return
        val strideBytes = FLOATS_PER_VERTEX * Float.SIZE_BYTES

        val positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        buffer.position(0)
        GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, strideBytes, buffer)
        GLES20.glEnableVertexAttribArray(positionLocation)

        val normalLocation = GLES20.glGetAttribLocation(program, "aNormal")
        buffer.position(3)
        GLES20.glVertexAttribPointer(normalLocation, 3, GLES20.GL_FLOAT, false, strideBytes, buffer)
        GLES20.glEnableVertexAttribArray(normalLocation)

        val colourLocation = GLES20.glGetAttribLocation(program, "aColor")
        buffer.position(6)
        GLES20.glVertexAttribPointer(colourLocation, 3, GLES20.GL_FLOAT, false, strideBytes, buffer)
        GLES20.glEnableVertexAttribArray(colourLocation)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(normalLocation)
        GLES20.glDisableVertexAttribArray(colourLocation)
    }

    private fun createBuffer(mesh: MineMesh): FloatBuffer? {
        if (mesh.vertices.isEmpty()) return null
        return ByteBuffer
            .allocateDirect(mesh.vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(mesh.vertices)
                position(0)
            }
    }

    private fun updateMatrices(state: MineWorldState) {
        val bounds = state.bounds
        val centre = bounds.centre
        val targetX = centre.x
        val targetY = -centre.z
        val targetZ = centre.y
        val largestSpan = max(bounds.width, max(bounds.height, bounds.depth))
        val distance = largestSpan * 2.25f * zoomScale
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val horizontal = cos(pitch).toFloat() * distance

        val eyeX = targetX + (cos(yaw).toFloat() * horizontal)
        val eyeY = targetY + (sin(pitch).toFloat() * distance)
        val eyeZ = targetZ + (sin(yaw).toFloat() * horizontal)

        Matrix.setLookAtM(
            viewMatrix,
            0,
            eyeX,
            eyeY,
            eyeZ,
            targetX,
            targetY,
            targetZ,
            0f,
            1f,
            0f,
        )
        Matrix.perspectiveM(
            projectionMatrix,
            0,
            42f,
            surfaceWidth.toFloat() / surfaceHeight.toFloat(),
            0.5f,
            largestSpan * 10f,
        )
        Matrix.multiplyMM(viewModelMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewModelMatrix, 0)
    }

    private fun configureDomainToOpenGlMatrix() {
        Matrix.setIdentityM(modelMatrix, 0)
        modelMatrix[0] = 1f
        modelMatrix[1] = 0f
        modelMatrix[2] = 0f
        modelMatrix[4] = 0f
        modelMatrix[5] = 0f
        modelMatrix[6] = 1f
        modelMatrix[8] = 0f
        modelMatrix[9] = -1f
        modelMatrix[10] = 0f
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { result ->
            GLES20.glAttachShader(result, vertexShader)
            GLES20.glAttachShader(result, fragmentShader)
            GLES20.glLinkProgram(result)
            val status = IntArray(1)
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val message = GLES20.glGetProgramInfoLog(result)
                GLES20.glDeleteProgram(result)
                throw IllegalStateException("OpenGL program link failed: $message")
            }
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        }
    }

    private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("OpenGL shader compile failed: $message")
        }
    }

    private companion object {
        const val FLOATS_PER_VERTEX = 9

        const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            varying vec3 vDomainPosition;
            varying vec3 vNormal;
            varying vec3 vColor;

            void main() {
                vDomainPosition = aPosition;
                vNormal = normalize((uModel * vec4(aNormal, 0.0)).xyz);
                vColor = aColor;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform int uClipAxis;
            uniform float uClipValue;
            uniform float uClipDirection;
            uniform float uClipEnabled;
            varying vec3 vDomainPosition;
            varying vec3 vNormal;
            varying vec3 vColor;

            void main() {
                float coordinate = vDomainPosition.z;
                if (uClipAxis == 0) {
                    coordinate = vDomainPosition.x;
                } else if (uClipAxis == 1) {
                    coordinate = vDomainPosition.y;
                }

                if (uClipEnabled > 0.5 && ((coordinate - uClipValue) * uClipDirection) < 0.0) {
                    discard;
                }

                vec3 lightDirection = normalize(vec3(0.35, 0.78, 0.52));
                float diffuse = 0.40 + (0.60 * abs(dot(normalize(vNormal), lightDirection)));
                gl_FragColor = vec4(vColor * diffuse, 1.0);
            }
        """
    }
}
