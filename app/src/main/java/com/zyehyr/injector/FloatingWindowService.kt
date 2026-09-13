package com.zyehyr.injector

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var featuresContainer: LinearLayout

    private val activeFeatures = linkedSetOf<String>()

    companion object {
        private const val CHANNEL_ID = "hyr_floating_channel"
        private const val CHANNEL_NAME = "HYR Floating Window"
        private const val NOTIF_ID = 1101

        private var instance: FloatingWindowService? = null

        fun update(ctx: Context, features: List<String>) {
            val i = Intent(ctx, FloatingWindowService::class.java).apply {
                action = "UPDATE"
                putStringArrayListExtra("features", ArrayList(features))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, FloatingWindowService::class.java).apply {
                action = "STOP"
            }
            try { ctx.startService(i) } catch (_: Throwable) {}
        }

        fun getFeatures(): List<String> = instance?.activeFeatures?.toList() ?: emptyList()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Wajib start foreground dulu
        startForeground(NOTIF_ID, buildNotification())

        when (intent?.action) {
            "UPDATE" -> {
                val list = intent.getStringArrayListExtra("features") ?: arrayListOf()
                activeFeatures.clear()
                activeFeatures.addAll(list)
                if (activeFeatures.isEmpty()) {
                    removeOverlay()
                    stopSelfSafely()
                } else {
                    showOverlay()
                    renderFeatures()
                }
            }
            "STOP" -> {
                activeFeatures.clear()
                removeOverlay()
                stopSelfSafely()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        instance = null
        super.onDestroy()
    }

    /* ================= OVERLAY ================= */

    private fun showOverlay() {
        if (floatingView != null) return

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.floating_window, null)

        featuresContainer = view.findViewById(R.id.floating_features)

        // Tombol close
        view.findViewById<TextView>(R.id.floating_close).setOnClickListener {
            activeFeatures.clear()
            removeOverlay()
            stopSelfSafely()
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 200

        // Drag support
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = params.x
                        initY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initX + (event.rawX - touchX).toInt()
                        params.y = initY + (event.rawY - touchY).toInt()
                        try { windowManager.updateViewLayout(view, params) } catch (_: Throwable) {}
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(view, params)
            floatingView = view
        } catch (_: Throwable) {
            floatingView = null
        }
    }

    private fun removeOverlay() {
        val v = floatingView ?: return
        try {
            windowManager.removeView(v)
        } catch (_: Throwable) {}
        floatingView = null
    }

    private fun renderFeatures() {
        val container = if (::featuresContainer.isInitialized) featuresContainer else return
        container.removeAllViews()

        activeFeatures.forEach { name ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = (resources.displayMetrics.density * 4).toInt()
                setPadding(0, p, 0, p)
            }

            // Titik hijau
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                    rightMargin = dp(8)
                }
                background = ContextCompat.getDrawable(this@FloatingWindowService, R.drawable.floating_dot)
            }
            row.addView(dot)

            // Teks fitur
            val tv = TextView(this).apply {
                text = name
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 10f
                letterSpacing = 0.12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            row.addView(tv)

            container.addView(row)
        }
    }

    private fun dp(v: Int): Int =
        (resources.displayMetrics.density * v).toInt()

    /* ================= NOTIFIKASI ================= */

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HYR INJECTOR")
            .setContentText("Jendela mengambang aktif")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopSelfSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {}
        stopSelf()
    }
}
