package com.phonebot.app

import android.content.Context
import java.io.File

/** ذخیره‌ی ساده‌ی فایلی برای آیدی‌های استخراج‌شده و لاگ اعلان‌ها. */
object Store {
    fun idsFile(ctx: Context): File = File(ctx.filesDir, "ids.txt")
    private fun logFile(ctx: Context): File = File(ctx.filesDir, "notifications.txt")

    @Synchronized
    fun saveIds(ctx: Context, ids: List<String>) {
        if (ids.isEmpty()) return
        val f = idsFile(ctx)
        val known = if (f.exists()) f.readLines().toMutableSet() else mutableSetOf()
        val newOnes = ids.filter { it !in known }
        if (newOnes.isNotEmpty()) {
            f.appendText(newOnes.joinToString("") { "$it\n" })
        }
    }

    @Synchronized
    fun appendLog(ctx: Context, line: String) {
        logFile(ctx).appendText(line + "\n---\n")
        // جلوگیری از رشد بی‌حد لاگ
        val f = logFile(ctx)
        if (f.length() > 300_000) {
            val text = f.readText()
            f.writeText(text.takeLast(200_000))
        }
    }

    fun readIds(ctx: Context): List<String> =
        if (idsFile(ctx).exists()) idsFile(ctx).readLines() else emptyList()

    fun readLog(ctx: Context): String =
        if (logFile(ctx).exists()) logFile(ctx).readText() else ""
}
