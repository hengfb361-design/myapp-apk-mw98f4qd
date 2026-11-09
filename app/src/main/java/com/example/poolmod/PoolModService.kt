package com.example.myapp
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class PoolModService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayCanvasView: DynamicCanvasView
    private lateinit var floatingModButton: Button
    
    private var isModActive = true
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "POOL_MOD_CHANNEL")
            .setContentTitle("8 Ball Pool MOD Active")
            .setContentText("Floating MOD Button & Overlay Active...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Fullscreen Canvas Overlay Params
        val canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        canvasParams.gravity = Gravity.TOP or Gravity.START

        overlayCanvasView = DynamicCanvasView(this)
        windowManager.addView(overlayCanvasView, canvasParams)

        // Floating MOD Button Params
        val buttonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        buttonParams.gravity = Gravity.TOP or Gravity.START
        buttonParams.x = 50
        buttonParams.y = 200

        floatingModButton = Button(this).apply {
            text = "MOD: ON"
            setBackgroundColor(Color.parseColor("#CC000000"))
            setTextColor(Color.GREEN)
            setPadding(30, 20, 30, 20)
        }

        // Touch listener for dragging floating MOD button & toggling MOD
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingModButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = buttonParams.x
                    initialY = buttonParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    buttonParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    buttonParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingModButton, buttonParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        // Click event: Toggle MOD state
                        isModActive = !isModActive
                        overlayCanvasView.visibility = if (isModActive) View.VISIBLE else View.GONE
                        floatingModButton.text = if (isModActive) "MOD: ON" else "MOD: OFF"
                        floatingModButton.setTextColor(if (isModActive) Color.GREEN else Color.RED)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingModButton, buttonParams)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
            val dataIntent = intent.getParcelableExtra<Intent>("DATA_INTENT")
            if (resultCode == Activity.RESULT_OK && dataIntent != null) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, dataIntent)
                setupVirtualDisplay()
            }
        }
        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PoolScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        overlayCanvasView.setScreenMetrics(screenWidth, screenHeight)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "POOL_MOD_CHANNEL",
                "Pool Mod Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        if (::overlayCanvasView.isInitialized) {
            windowManager.removeView(overlayCanvasView)
        }
        if (::floatingModButton.isInitialized) {
            windowManager.removeView(floatingModButton)
        }
    }
}

class DynamicCanvasView(context: Context) : View(context) {
    private var screenWidth = 1920f
    private var screenHeight = 1080f

    private val mainAimPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val reflectionPaint = Paint().apply {
        color = Color.rgb(255, 140, 0)
        strokeWidth = 5f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val cushionPaint = Paint().apply {
        color = Color.rgb(186, 85, 211)
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val targetHitPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    init {
        try {
            System.loadLibrary("pool-physics-engine")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setScreenMetrics(width: Int, height: Int) {
        this.screenWidth = width.toFloat()
        this.screenHeight = height.toFloat()
    }

    private external fun calculateDynamicLines(w: Float, h: Float): FloatArray

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = if (width > 0) width.toFloat() else screenWidth
        val h = if (height > 0) height.toFloat() else screenHeight

        try {
            val coords = calculateDynamicLines(w, h)
            if (coords.size >= 12) {
                // Main Aim Line
                canvas.drawLine(coords[0], coords[1], coords[2], coords[3], mainAimPaint)
                // Collision Hit Point Circle
                canvas.drawCircle(coords[2], coords[3], h * 0.025f, targetHitPaint)
                // Target Ball Extended Reflection Path
                canvas.drawLine(coords[2], coords[3], coords[4], coords[5], reflectionPaint)
                // Cue Ball Cushion Reflection Path
                canvas.drawLine(coords[2], coords[3], coords[6], coords[7], cushionPaint)
                canvas.drawLine(coords[6], coords[7], coords[8], coords[9], cushionPaint)

                invalidate()
                return
            }
        } catch (e: Exception) {
            // Native fallback
        }

        val startX = w * 0.35f
        val startY = h * 0.52f
        val hitX = w * 0.72f
        val hitY = h * 0.52f

        canvas.drawLine(startX, startY, hitX, hitY, mainAimPaint)
        canvas.drawCircle(hitX, hitY, h * 0.025f, targetHitPaint)
        canvas.drawLine(hitX, hitY, w * 0.85f, h * 0.35f, reflectionPaint)
        canvas.drawLine(hitX, hitY, w * 0.85f, h * 0.75f, cushionPaint)

        invalidate()
    }
}
