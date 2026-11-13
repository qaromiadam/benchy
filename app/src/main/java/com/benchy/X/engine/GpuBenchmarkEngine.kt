package com.benchy.X.engine

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

data class GpuBenchmarkResult(
    val score2dFps: Double,
    val instant3dFps: Double,
    val avg3dFps: Double,
    val onePercentLowFps: Double,
    val thermalDropPercent: Double,
    val rendererName: String
)

class BenchyGlStressRenderer(
    private val onStatsSampled: (
        instantFps: Double,
        avgFps: Double,
        onePercentLow: Double,
        dropPct: Double,
        renderer: String
    ) -> Unit
) : GLSurfaceView.Renderer {

    private var programId = 0
    private var mvpHandle = 0
    private var timeHandle = 0
    private var vertexBuffer: FloatBuffer? = null

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val frameTimesMs = ArrayList<Double>(256)
    private var lastFrameNs = 0L
    private var startTimeNs = 0L
    private var glRendererName = "OpenGL ES 3.0"
    private var initialWindowAvgFps = 0.0
    private var framesRendered = 0

    private val cubeVertices = floatArrayOf(
        // Front face
        -0.75f, -0.75f,  0.75f,  0.75f, -0.75f,  0.75f,  0.75f,  0.75f,  0.75f,
        -0.75f, -0.75f,  0.75f,  0.75f,  0.75f,  0.75f, -0.75f,  0.75f,  0.75f,
        // Back face
        -0.75f, -0.75f, -0.75f, -0.75f,  0.75f, -0.75f,  0.75f,  0.75f, -0.75f,
        -0.75f, -0.75f, -0.75f,  0.75f,  0.75f, -0.75f,  0.75f, -0.75f, -0.75f,
        // Left face
        -0.75f, -0.75f, -0.75f, -0.75f, -0.75f,  0.75f, -0.75f,  0.75f,  0.75f,
        -0.75f, -0.75f, -0.75f, -0.75f,  0.75f,  0.75f, -0.75f,  0.75f, -0.75f,
        // Right face
         0.75f, -0.75f, -0.75f,  0.75f,  0.75f,  0.75f,  0.75f, -0.75f,  0.75f,
         0.75f, -0.75f, -0.75f,  0.75f,  0.75f, -0.75f,  0.75f,  0.75f,  0.75f,
        // Top face
        -0.75f,  0.75f, -0.75f, -0.75f,  0.75f,  0.75f,  0.75f,  0.75f,  0.75f,
        -0.75f,  0.75f, -0.75f,  0.75f,  0.75f,  0.75f,  0.75f,  0.75f, -0.75f,
        // Bottom face
        -0.75f, -0.75f, -0.75f,  0.75f, -0.75f,  0.75f, -0.75f, -0.75f,  0.75f,
        -0.75f, -0.75f, -0.75f,  0.75f, -0.75f, -0.75f,  0.75f, -0.75f,  0.75f
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.055f, 0.067f, 0.09f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        glRendererName = GLES30.glGetString(GLES30.GL_RENDERER) ?: "OpenGL ES 3.0"

        vertexBuffer = ByteBuffer.allocateDirect(cubeVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(cubeVertices)
                position(0)
            }

        val vs = compileShader(
            GLES30.GL_VERTEX_SHADER,
            """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            uniform mat4 uMvpMatrix;
            out vec3 vPos;
            void main() {
                vPos = aPosition;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
            """.trimIndent()
        )
        val fs = compileShader(
            GLES30.GL_FRAGMENT_SHADER,
            """
            #version 300 es
            precision highp float;
            in vec3 vPos;
            uniform float uTime;
            out vec4 fragColor;
            void main() {
                vec3 p = vPos * 2.2;
                float acc = 0.0;
                for (int i = 0; i < 14; i++) {
                    float fi = float(i) * 0.45 + uTime;
                    acc += sin(p.x * 3.1 + fi) * cos(p.y * 2.7 - fi) + sin(p.z * 3.5 + uTime);
                    p = vec3(p.y * 0.92 - p.x * 0.31, p.z * 0.91 + p.y * 0.29, p.x * 0.89 + p.z * 0.25);
                }
                float pulse = 0.5 + 0.5 * sin(acc * 0.35);
                vec3 col = mix(vec3(0.04, 0.14, 0.24), vec3(0.0, 0.90, 1.0), pulse);
                fragColor = vec4(col, 1.0);
            }
            """.trimIndent()
        )
        programId = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vs)
            GLES30.glAttachShader(it, fs)
            GLES30.glLinkProgram(it)
        }
        mvpHandle = GLES30.glGetUniformLocation(programId, "uMvpMatrix")
        timeHandle = GLES30.glGetUniformLocation(programId, "uTime")
        startTimeNs = System.nanoTime()
        lastFrameNs = startTimeNs
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.frustumM(projMatrix, 0, -ratio, ratio, -1f, 1f, 1.5f, 10f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dtMs = ((now - lastFrameNs) / 1_000_000.0).coerceIn(0.5, 250.0)
        lastFrameNs = now
        framesRendered++
        frameTimesMs.add(dtMs)
        if (frameTimesMs.size > 180) frameTimesMs.removeAt(0)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(programId)

        val elapsedSec = ((now - startTimeNs) / 1_000_000_000.0).toFloat()
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3.3f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, elapsedSec * 52f, 0.6f, 1.0f, 0.25f)

        val temp = FloatArray(16)
        Matrix.multiplyMM(temp, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, temp, 0)

        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(timeHandle, elapsedSec)

        vertexBuffer?.let { buf ->
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, buf)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 36)
            GLES30.glDisableVertexAttribArray(0)
        }

        if (framesRendered >= 6 && framesRendered % 6 == 0) {
            val recentWindow = frameTimesMs.takeLast(12)
            val instantFps = (1000.0 / recentWindow.average().coerceAtLeast(1.0)).coerceIn(1.0, 240.0)
            val avgMs = frameTimesMs.average().coerceAtLeast(1.0)
            val avgFps = (1000.0 / avgMs).coerceIn(1.0, 240.0)
            val sortedSlowest = frameTimesMs.sortedDescending()
            val topSlowCount = (sortedSlowest.size * 0.05).roundToInt().coerceAtLeast(1)
            val p1Ms = sortedSlowest.take(topSlowCount).average().coerceAtLeast(avgMs)
            val onePercentLow = (1000.0 / p1Ms).coerceIn(1.0, avgFps)
            if (initialWindowAvgFps <= 0.0 && frameTimesMs.size >= 24) {
                initialWindowAvgFps = avgFps
            }
            val dropPct = if (initialWindowAvgFps > 0.0) {
                (((initialWindowAvgFps - avgFps) / initialWindowAvgFps) * 100.0).coerceIn(0.0, 60.0)
            } else {
                0.0
            }
            onStatsSampled(instantFps, avgFps, onePercentLow, dropPct, glRendererName)
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, code)
            GLES30.glCompileShader(shader)
        }
    }
}
