package com.zyehyr.injector

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class AndroidBridge(private val activity: AppCompatActivity) {

    companion object {
        private const val REQ = 1001
        private const val FFMAX_PKG = "com.dts.freefiremax"
        private const val FFMAX_ACT = "com.dts.freefiremax.FFMainActivity"
    }

    /* ================= SHIZUKU ================= */

    @JavascriptInterface
    fun getShizukuStatus(): String = try {
        when {
            !Shizuku.pingBinder() -> "NOT_INSTALLED"
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "CONNECTED"
            else -> "NOT_CONNECTED"
        }
    } catch (_: Throwable) { "NOT_INSTALLED" }

    @JavascriptInterface
    fun requestShizukuPermission() {
        activity.runOnUiThread {
            try {
                if (!Shizuku.isPreV11() &&
                    Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
                ) Shizuku.requestPermission(REQ)
            } catch (_: Throwable) {}
        }
    }

    /* ================= BUKA URL ================= */

    @JavascriptInterface
    fun openUrl(url: String) {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return
        activity.runOnUiThread {
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(i)
            } catch (_: Throwable) {}
        }
    }

    /* ============================================================
       LAUNCH FREE FIRE MAX
       Cara: Intent eksplisit ke FFMainActivity
       Return: "INTENT_OK" | "NOT_INSTALLED" | "FAILED"
       ============================================================ */
    @JavascriptInterface
    fun launchFFMax(): String {
        // Cek game terinstall
        val installed = try {
            activity.packageManager.getPackageInfo(FFMAX_PKG, 0)
            true
        } catch (_: Throwable) { false }

        if (!installed) return "NOT_INSTALLED"

        // Cara 1: Intent eksplisit ke FFMainActivity
        try {
            val intent = Intent().apply {
                setClassName(FFMAX_PKG, FFMAX_ACT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            return "INTENT_OK"
        } catch (_: Throwable) {}

        // Cara 2: Fallback ke launcher intent
        try {
            val intent = activity.packageManager.getLaunchIntentForPackage(FFMAX_PKG)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
                return "INTENT_OK"
            }
        } catch (_: Throwable) {}

        return "FAILED"
    }

    /* ============================================================
       IZIN OVERLAY
       ============================================================ */

    @JavascriptInterface
    fun hasOverlayPermission(): Boolean {
        return try {
            Settings.canDrawOverlays(activity)
        } catch (_: Throwable) { false }
    }

    @JavascriptInterface
    fun requestOverlayPermission() {
        activity.runOnUiThread {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
            } catch (_: Throwable) {}
        }
    }

    /* ============================================================
       FLOATING WINDOW
       ============================================================ */

    @JavascriptInterface
    fun updateFloatingWindow(featuresCsv: String): Boolean {
        if (!Settings.canDrawOverlays(activity)) return false

        val features = featuresCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (features.isEmpty()) {
            FloatingWindowService.stop(activity)
        } else {
            FloatingWindowService.update(activity, features)
        }
        return true
    }

    @JavascriptInterface
    fun getActiveFeatures(): String {
        return FloatingWindowService.getFeatures().joinToString(",")
    }

    /* ================= Info ================= */

    @JavascriptInterface
    fun getSdkInt(): Int = Build.VERSION.SDK_INT

    @JavascriptInterface
    fun getAppVersion(): String {
        return try {
            activity.packageManager
                .getPackageInfo(activity.packageName, 0).versionName ?: "1.0"
        } catch (_: Throwable) { "1.0" }
    }
}
