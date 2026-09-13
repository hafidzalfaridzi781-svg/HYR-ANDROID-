package com.zyehyr.injector

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    companion object {
        private const val REQ_NOTIF = 2001
        private const val PREFS = "hyr_prefs"
        private const val KEY_PERMISSION_ASKED = "permission_asked"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        val s: WebSettings = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.databaseEnabled = false
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")

        // Cek & minta izin setelah UI siap
        webView.postDelayed({ askPermissionsOnStart() }, 1500)
    }

    /* ============================================================
       IZIN AWAL — Overlay + Notifikasi
       ============================================================ */
    private fun askPermissionsOnStart() {
        // 1) Izin tampil di atas aplikasi lain (SYSTEM_ALERT_WINDOW)
        if (!Settings.canDrawOverlays(this)) {
            showOverlayDialog()
            return
        }
        // 2) Izin notifikasi (Android 13+)
        requestNotificationPermissionIfNeeded()
    }

    private fun showOverlayDialog() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val askedBefore = prefs.getBoolean(KEY_PERMISSION_ASKED, false)

        AlertDialog.Builder(this)
            .setTitle("IZIN APLIKASI")
            .setMessage(
                "HYR INJECTOR membutuhkan:\n\n" +
                "• Izin 'Tampil di atas aplikasi lain' untuk menampilkan " +
                "jendela mengambang saat fitur diaktifkan.\n\n" +
                "• Izin 'Berjalan di latar belakang' agar aplikasi tetap " +
                "aktif meski kamu keluar sementara."
            )
            .setPositiveButton("IZINKAN") { _, _ ->
                prefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Throwable) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    } catch (_: Throwable) {}
                }
            }
            .setNegativeButton("NANTI") { _, _ ->
                prefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
            }
            .setCancelable(!askedBefore)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIF
                )
            }
        }
    }

    /* ============================================================
       Lifecycle
       ============================================================ */
    override fun onResume() {
        super.onResume()
        // Setelah balik dari Settings, cek lagi
        webView.postDelayed({
            if (::webView.isInitialized) {
                val granted = Settings.canDrawOverlays(this)
                webView.evaluateJavascript(
                    "window.onOverlayPermissionChange && " +
                    "window.onOverlayPermissionChange($granted);", null
                )
            }
        }, 300)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
