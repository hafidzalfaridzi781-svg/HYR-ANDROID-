package com.zyehyr.injector

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.JavascriptInterface
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class AndroidBridge(private val activity: AppCompatActivity) {

    companion object {
        private const val REQ = 1001
    }

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
}
