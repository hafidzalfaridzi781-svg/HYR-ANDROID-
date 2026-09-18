package com.zyecitedz.tools

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat

class CrosshairService : Service() {

    private lateinit var windowManager: WindowManager
    private var crosshairView: View? = null

    companion object {
        private const val CHANNEL_ID = "zye_crosshair_channel"
        private const val NOTIF_ID = 1002

        fun show(ctx: Context, shape: String, color: String, size: Int) {
            val i = Intent(ctx, CrosshairService::class.java).apply {
                action = "SHOW"
                putExtra("shape", shape)
                putExtra("color", color)
                putExtra("size", size)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun hide(ctx: Context) {
            val i = Intent(ctx, CrosshairService::class.java).apply { action = "HIDE" }
            try { ctx.startService(i) } catch (_: Throwable) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        when (intent?.action) {
            "SHOW" -> {
                val shape = intent.getStringExtra("shape") ?: "cross"
                val color = intent.getStringExtra("color") ?: "#00FF00"
                val size = intent.getIntExtra("size", 60)
                removeCrosshair()
                addCrosshair(shape, color, size)
            }
            "HIDE" -> {
                removeCrosshair()
                stopSelfSafely()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeCrosshair()
        super.onDestroy()
    }

    private fun addCrosshair(shape: String, colorHex: String, sizeDp: Int) {
        val view = CrosshairView(this, shape, colorHex, sizeDp)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager.addView(view, params)
            crosshairView = view
        } catch (_: Throwable) { crosshairView = null }
    }

    private fun removeCrosshair() {
        val v = crosshairView ?: return
        try { windowManager.removeView(v) } catch (_: Throwable) {}
        crosshairView = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "ZYE Crosshair", NotificationManager.IMPORTANCE_LOW
                ))
            }
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZYE TOOLS")
            .setContentText("Crosshair aktif")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopSelfSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        } catch (_: Throwable) {}
        stopSelf()
    }

    /* ============================================================
       CUSTOM VIEW untuk menggambar crosshair
       ============================================================ */
    private class CrosshairView(
        context: Context,
        val shape: String,
        val colorHex: String,
        val sizeDp: Int
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            style = Paint.Style.FILL
        }

        private val sizePx: Int = (sizeDp * context.resources.displayMetrics.density).toInt()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(sizePx, sizePx)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = width / 2f - 6f

            when (shape) {
                "cross" -> drawCross(canvas, cx, cy, r)
                "dot" -> drawDot(canvas, cx, cy, r)
                "circle" -> drawCircle(canvas, cx, cy, r)
                "square" -> drawSquare(canvas, cx, cy, r)
                "triangle" -> drawTriangle(canvas, cx, cy, r)
                "diamond" -> drawDiamond(canvas, cx, cy, r)
                "x" -> drawX(canvas, cx, cy, r)
                "tcross" -> drawTCross(canvas, cx, cy, r)
                "full_dot" -> drawFullDot(canvas, cx, cy, r)
                else -> drawCross(canvas, cx, cy, r)
            }
        }

        private fun drawCross(c: Canvas, cx: Float, cy: Float, r: Float) {
            val gap = r * 0.3f
            c.drawLine(cx - r, cy, cx - gap, cy, paint)
            c.drawLine(cx + gap, cy, cx + r, cy, paint)
            c.drawLine(cx, cy - r, cx, cy - gap, paint)
            c.drawLine(cx, cy + gap, cx, cy + r, paint)
        }

        private fun drawDot(c: Canvas, cx: Float, cy: Float, r: Float) {
            c.drawCircle(cx, cy, r * 0.15f, fillPaint)
        }

        private fun drawCircle(c: Canvas, cx: Float, cy: Float, r: Float) {
            c.drawCircle(cx, cy, r * 0.6f, paint)
            c.drawCircle(cx, cy, r * 0.08f, fillPaint)
        }

        private fun drawSquare(c: Canvas, cx: Float, cy: Float, r: Float) {
            val s = r * 0.6f
            c.drawRect(cx - s, cy - s, cx + s, cy + s, paint)
        }

        private fun drawTriangle(c: Canvas, cx: Float, cy: Float, r: Float) {
            val path = Path().apply {
                moveTo(cx, cy - r * 0.7f)
                lineTo(cx - r * 0.6f, cy + r * 0.5f)
                lineTo(cx + r * 0.6f, cy + r * 0.5f)
                close()
            }
            c.drawPath(path, paint)
        }

        private fun drawDiamond(c: Canvas, cx: Float, cy: Float, r: Float) {
            val path = Path().apply {
                moveTo(cx, cy - r * 0.7f)
                lineTo(cx + r * 0.7f, cy)
                lineTo(cx, cy + r * 0.7f)
                lineTo(cx - r * 0.7f, cy)
                close()
            }
            c.drawPath(path, paint)
        }

        private fun drawX(c: Canvas, cx: Float, cy: Float, r: Float) {
            val d = r * 0.5f
            c.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
            c.drawLine(cx - d, cy + d, cx + d, cy - d, paint)
        }

        private fun drawTCross(c: Canvas, cx: Float, cy: Float, r: Float) {
            c.drawLine(cx - r, cy, cx + r, cy, paint)
            c.drawLine(cx, cy, cx, cy + r, paint)
        }

        private fun drawFullDot(c: Canvas, cx: Float, cy: Float, r: Float) {
            c.drawCircle(cx, cy, r * 0.25f, fillPaint)
        }
    }
}
