/* ============================================================
   DPI & RESOLUTION CHANGER (via Shizuku)
   ============================================================ */

private fun runShizukuCommand(cmd: String): String {
    return try {
        if (!Shizuku.pingBinder() ||
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) return "SHIZUKU_NOT_READY"
        val proc = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        out.ifBlank { "OK" }
    } catch (e: Throwable) {
        "ERROR: ${e.message}"
    }
}

@JavascriptInterface
fun setDpi(dpi: String): String {
    val d = dpi.toIntOrNull() ?: return "INVALID"
    if (d < 120 || d > 700) return "OUT_OF_RANGE"
    return runShizukuCommand("wm density $d")
}

@JavascriptInterface
fun resetDpi(): String {
    return runShizukuCommand("wm density reset")
}

@JavascriptInterface
fun getCurrentDpi(): String {
    return try {
        val am = activity.resources.displayMetrics
        val dpi = (am.density * 160).toInt()
        dpi.toString()
    } catch (_: Throwable) { "0" }
}

@JavascriptInterface
fun setResolution(w: String, h: String): String {
    val ww = w.toIntOrNull() ?: return "INVALID"
    val hh = h.toIntOrNull() ?: return "INVALID"
    if (ww < 320 || ww > 4000 || hh < 480 || hh > 4000) return "OUT_OF_RANGE"
    return runShizukuCommand("wm size ${ww}x${hh}")
}

@JavascriptInterface
fun resetResolution(): String {
    return runShizukuCommand("wm size reset")
}

@JavascriptInterface
fun getCurrentResolution(): String {
    return try {
        val dm = activity.resources.displayMetrics
        "${dm.widthPixels}x${dm.heightPixels}"
    } catch (_: Throwable) { "0x0" }
}

/* ============================================================
   CROSSHAIR OVERLAY
   ============================================================ */

@JavascriptInterface
fun showCrosshair(shape: String, color: String, size: Int): Boolean {
    val canOverlay = try {
        Settings.canDrawOverlays(activity)
    } catch (_: Throwable) { false }

    if (!canOverlay) {
        activity.runOnUiThread {
            try {
                activity.startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                ))
            } catch (_: Throwable) {}
        }
        return false
    }
    CrosshairService.show(activity, shape, color, size)
    return true
}

@JavascriptInterface
fun hideCrosshair() {
    CrosshairService.hide(activity)
}
