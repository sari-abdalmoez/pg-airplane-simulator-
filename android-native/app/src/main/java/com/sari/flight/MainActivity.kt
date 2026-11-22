package com.sari.flight

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var game: FlightView
    private lateinit var hud: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val hudLoop = object : Runnable {
        override fun run() {
            if (::game.isInitialized && ::hud.isInitialized) {
                hud.text = game.flightStatus
                handler.postDelayed(this, 250)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val root = FrameLayout(this)
        game = FlightView(this)
        root.addView(game)

        hud = TextView(this).apply {
            text = "SARI FLIGHT SIMULATOR"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0xAA102334.toInt())
        }

        val hudParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        )
        root.addView(hud, hudParams)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(5, 3, 5, 3)
            setBackgroundColor(0x66304050)
        }

        addControlRow(controls, listOf(
            "PITCH +" to "PITCH_UP",
            "PITCH -" to "PITCH_DOWN",
            "BANK LEFT" to "ROLL_LEFT",
            "BANK RIGHT" to "ROLL_RIGHT"
        ))

        addControlRow(controls, listOf(
            "THROTTLE +" to "THROTTLE_UP",
            "THROTTLE -" to "THROTTLE_DOWN",
            "BRAKE" to "BRAKE",
            "GEAR" to "GEAR"
        ))

        val controlParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        root.addView(controls, controlParams)

        setContentView(root)
    }

    private fun addControlRow(
        parent: LinearLayout,
        items: List<Pair<String, String>>
    ) {
        val row = LinearLayout(this)
        for ((label, code) in items) {
            val button = Button(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                setPadding(2, 2, 2, 2)
            }

            val params = LinearLayout.LayoutParams(0, 48, 1f)
            params.setMargins(3, 2, 3, 2)
            row.addView(button, params)

            button.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (code == "GEAR") {
                            game.toggleGear()
                        } else {
                            game.setControl(code, true)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        game.setControl(code, false)
                    }
                }
                true
            }
        }
        parent.addView(row)
    }

    override fun onResume() {
        super.onResume()
        if (::game.isInitialized) game.onResume()
        handler.post(hudLoop)
    }

    override fun onPause() {
        handler.removeCallbacks(hudLoop)
        if (::game.isInitialized) game.onPause()
        super.onPause()
    }
}

class FlightView(context: android.content.Context) : GLSurfaceView(context) {
    private val flightRenderer = FlightRenderer()
    val flightStatus: String get() = flightRenderer.status

    init {
        setEGLContextClientVersion(2)
        setRenderer(flightRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setControl(name: String, active: Boolean) {
        flightRenderer.setControl(name, active)
    }

    fun toggleGear() {
        flightRenderer.toggleGear()
    }
}

class FlightRenderer : GLSurfaceView.Renderer {
    @Volatile private var pitchInput = 0f
    @Volatile private var rollInput = 0f
    @Volatile private var throttleUp = false
    @Volatile private var throttleDown = false
    @Volatile private var braking = false
    @Volatile private var gearDown = true

    @Volatile var status = "SARI FLIGHT SIMULATOR" 
        private set

    private var throttle = 0.75f
    private var speed = 0f
    private var altitude = 1.2f
    private var verticalSpeed = 0f
    private var pitchAngle = 0f
    private var rollAngle = 0f
    private var heading = 0f
    private var aircraftZ = 0f
    private var propAngle = 0f
    private var lastFrame = 0L

    private var program = 0
    private var positionHandle = 0
    private var matrixHandle = 0
    private var colorHandle = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val identity = FloatArray(16)
    private val local = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    private val cubeBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(CUBE.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(CUBE); position(0) }

    fun setControl(name: String, active: Boolean) {
        when (name) {
            "PITCH_UP" -> pitchInput = if (active) 1f else if (pitchInput > 0f) 0f else pitchInput
            "PITCH_DOWN" -> pitchInput = if (active) -1f else if (pitchInput < 0f) 0f else pitchInput
            "ROLL_LEFT" -> rollInput = if (active) -1f else if (rollInput < 0f) 0f else rollInput
            "ROLL_RIGHT" -> rollInput = if (active) 1f else if (rollInput > 0f) 0f else rollInput
            "THROTTLE_UP" -> throttleUp = active
            "THROTTLE_DOWN" -> throttleDown = active
            "BRAKE" -> braking = active
        }
    }

    fun toggleGear() {
        gearDown = !gearDown
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.48f, 0.70f, 0.88f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vertexShader = """
            attribute vec4 aPosition;
            uniform mat4 uMvp;
            void main() {
                gl_Position = uMvp * aPosition;
            }
        """.trimIndent()

        val fragmentShader = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """.trimIndent()

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        val result = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0)
        check(result[0] != 0) { "OpenGL shader linking failed" }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        matrixHandle = GLES20.glGetUniformLocation(program, "uMvp")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        Matrix.setIdentityM(identity, 0)
        lastFrame = System.nanoTime()
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val result = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0)
        check(result[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(
            projection, 0, 60f,
            width.toFloat() / height.coerceAtLeast(1),
            0.1f, 4000f
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrame = now

        updatePhysics(dt)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        Matrix.setLookAtM(
            view, 0,
            0f, altitude + 9f, aircraftZ + 23f,
            0f, altitude, aircraftZ - 12f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)

        drawAirport()
        drawAircraft(dt)

        status = String.format(
            Locale.US,
            "SARI FLIGHT SIMULATOR\nIAS  %03d KT\nALT  %05d FT\nV/S  %04d FT/MIN\nTHR  %02d%%\nGEAR %s\nBANK %02d°",
            (speed * 1.94384f).toInt(),
            ((altitude - 1.2f).coerceAtLeast(0f) * 3.28084f).toInt(),
            (verticalSpeed * 196.85f).toInt(),
            (throttle * 100).toInt(),
            if (gearDown) "DOWN" else "UP",
            rollAngle.toInt()
        )
    }

    private fun updatePhysics(dt: Float) {
        if (throttleUp) throttle = (throttle + 0.45f * dt).coerceAtMost(1f)
        if (throttleDown) throttle = (throttle - 0.45f * dt).coerceAtLeast(0f)

        val drag = if (gearDown) 0.25f else 0.20f
        val acceleration = throttle * 17f - speed * drag
        speed = (speed + acceleration * dt).coerceAtLeast(0f)

        if (braking && altitude <= 1.3f) {
            speed = (speed - 18f * dt).coerceAtLeast(0f)
        }

        pitchAngle += (pitchInput * 14f - pitchAngle) * (3f * dt).coerceAtMost(1f)
        rollAngle += (rollInput * 35f - rollAngle) * (3f * dt).coerceAtMost(1f)

        val liftCoefficient =
            (0.28f + pitchAngle / 18f * 0.85f).coerceIn(0.05f, 1.35f)

        val dynamicPressure = 0.5f * 1.225f * speed * speed
        val lift = dynamicPressure * 16.2f * liftCoefficient
        val verticalAcceleration = lift / 1050f - 9.81f

        verticalSpeed += verticalAcceleration * dt
        verticalSpeed = verticalSpeed.coerceIn(-25f, 20f)

        altitude += verticalSpeed * dt
        if (altitude <= 1.2f) {
            altitude = 1.2f
            if (verticalSpeed < 0f) verticalSpeed = 0f
        }

        val bankRadians = Math.toRadians(rollAngle.toDouble()).toFloat()
        heading += sin(bankRadians) * speed * 0.015f * dt
        aircraftZ -= speed * dt * 0.35f
        propAngle = (propAngle + speed * 20f * dt) % 360f
    }

    private fun drawAirport() {
        drawCube(identity, 0f, -1f, -250f, 2000f, 1f, 2000f, color(0.20f, 0.36f, 0.21f))

        drawCube(identity, 0f, -0.35f, -220f, 34f, 0.30f, 700f, color(0.15f, 0.16f, 0.18f))
        drawCube(identity, 0f, -0.17f, -220f, 0.6f, 0.03f, 690f, color(0.95f, 0.93f, 0.80f))

        drawCube(identity, -14.5f, -0.16f, -220f, 0.35f, 0.04f, 690f, color(0.92f, 0.92f, 0.88f))
        drawCube(identity, 14.5f, -0.16f, -220f, 0.35f, 0.04f, 690f, color(0.92f, 0.92f, 0.88f))

        for (i in 0..25) {
            val z = -560f + i * 26f
            drawCube(identity, 0f, -0.15f, z, 0.65f, 0.04f, 12f, color(1f, 1f, 1f))
        }

        for (i in 0..30) {
            val z = -550f + i * 22f
            drawCube(identity, -18f, -0.12f, z, 0.35f, 0.25f, 0.35f, color(0.95f, 0.88f, 0.55f))
            drawCube(identity, 18f, -0.12f, z, 0.35f, 0.25f, 0.35f, color(0.95f, 0.88f, 0.55f))
        }

        // Taxiway and apron
        drawCube(identity, 26f, -0.25f, -170f, 18f, 0.15f, 145f, color(0.25f, 0.26f, 0.27f))
        drawCube(identity, 48f, 2.8f, -150f, 32f, 5.5f, 24f, color(0.70f, 0.73f, 0.76f))
        drawCube(identity, 48f, 5.8f, -150f, 34f, 0.4f, 26f, color(0.18f, 0.28f, 0.38f))

        // Control tower
        drawCube(identity, 72f, 7f, -170f, 5f, 14f, 5f, color(0.76f, 0.78f, 0.80f))
        drawCube(identity, 72f, 15f, -170f, 11f, 3f, 11f, color(0.12f, 0.28f, 0.38f))
        drawCube(identity, 72f, 17f, -170f, 12f, 0.4f, 12f, color(0.84f, 0.86f, 0.88f))
    }

    private fun drawAircraft(dt: Float) {
        val plane = FloatArray(16)
        Matrix.setIdentityM(plane, 0)
        Matrix.translateM(plane, 0, 0f, altitude, aircraftZ)
        Matrix.rotateM(plane, 0, Math.toDegrees(heading.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(plane, 0, pitchAngle, 1f, 0f, 0f)
        Matrix.rotateM(plane, 0, rollAngle, 0f, 0f, 1f)

        val white = color(0.88f, 0.91f, 0.94f)
        val blue = color(0.08f, 0.28f, 0.50f)
        val dark = color(0.12f, 0.15f, 0.19f)

        // Fuselage, wings and tail
        drawCube(plane, 0f, 0f, 0f, 0.85f, 0.75f, 7f, white)
        drawCube(plane, 0f, -0.02f, -0.3f, 14f, 0.20f, 1.9f, blue)
        drawCube(plane, 0f, 0.04f, 2.8f, 4.2f, 0.16f, 1.1f, blue)
        drawCube(plane, 0f, 0.72f, 2.8f, 0.18f, 1.5f, 1.1f, blue)

        // Cockpit and engine
        drawCube(plane, 0f, 0.48f, -0.8f, 0.78f, 0.48f, 1.5f, color(0.10f, 0.24f, 0.34f))
        drawCube(plane, 0f, 0f, -3.7f, 0.55f, 0.52f, 0.55f, dark)

        // Propeller
        drawCube(plane, 0f, 0f, -4.05f, 2.6f, 0.10f, 0.12f, dark, 0f, 0f, propAngle)

        // Landing gear
        if (gearDown) {
            drawCube(plane, -0.55f, -0.65f, -1.2f, 0.12f, 0.85f, 0.12f, dark)
            drawCube(plane, 0.55f, -0.65f, -1.2f, 0.12f, 0.85f, 0.12f, dark)
            drawCube(plane, 0f, -0.65f, 2.5f, 0.12f, 0.8f, 0.12f, dark)
        }
    }

    private fun color(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b, 1f)

    private fun drawCube(
        parent: FloatArray,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        color: FloatArray,
        rx: Float = 0f, ry: Float = 0f, rz: Float = 0f
    ) {
        Matrix.setIdentityM(local, 0)
        Matrix.translateM(local, 0, x, y, z)
        if (rx != 0f) Matrix.rotateM(local, 0, rx, 1f, 0f, 0f)
        if (ry != 0f) Matrix.rotateM(local, 0, ry, 0f, 1f, 0f)
        if (rz != 0f) Matrix.rotateM(local, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(local, 0, sx, sy, sz)
        Matrix.multiplyMM(model, 0, parent, 0, local, 0)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)

        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        cubeBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, cubeBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)
    }

    companion object {
        private val CUBE = floatArrayOf(
            -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,  0.5f,0.5f,0.5f,
            -0.5f,-0.5f, 0.5f,  0.5f,0.5f,0.5f,  -0.5f,0.5f,0.5f,
            0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f,
            0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f,
            0.5f,-0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f,
            -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f,
            -0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f,
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f
        )
    }
}
