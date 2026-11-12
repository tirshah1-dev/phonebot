package com.phonebot.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * اجرای دستورهای متنی (همان‌هایی که در اپ و هم از طریق سرور شبکه قابل استفاده‌اند).
 * هر فراخوانی یک پیام متنی برمی‌گرداند که نتیجه را توصیف می‌کند.
 */
object CommandRunner {

    val apps = mapOf(
        "telegram" to "org.telegram.messenger",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "chrome" to "com.android.chrome",
        "settings" to "com.android.settings",
        "youtube" to "com.google.android.youtube",
        "messages" to "com.google.android.apps.messaging",
    )

    fun run(ctx: Context, line: String): String {
        val parts = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "دستور خالی"
        val name = parts[0].lowercase()
        val args = parts.drop(1)
        val svc = ControlAccessibilityService.instance

        fun needService(): String? =
            if (svc == null) "اول Accessibility را در تنظیمات اپ فعال کن." else null

        return when (name) {
            "notifs" -> Store.readLog(ctx).takeLast(6000).ifBlank { "اعلانی ضبط نشده" }
            "ids" -> Store.readIds(ctx).joinToString("\n").ifBlank { "آیدی‌ای پیدا نشده" }
            "screen" -> needService() ?: svc!!.dumpScreenText()
            "shot" -> {
                needService()?.let { return it }
                val latch = CountDownLatch(1)
                var resultMsg = "اسکرین‌شات ناموفق بود"
                svc!!.takeScreenshotToFile(ctx) { file ->
                    resultMsg = if (file != null) "ذخیره شد: ${file.absolutePath}" else resultMsg
                    latch.countDown()
                }
                latch.await(8, TimeUnit.SECONDS)
                resultMsg
            }
            "tap" -> {
                val x = args.getOrNull(0)?.toFloatOrNull()
                val y = args.getOrNull(1)?.toFloatOrNull()
                if (x == null || y == null) "استفاده: tap X Y"
                else needService() ?: run { svc!!.tap(x, y); "زده شد ($x,$y)" }
            }
            "swipe" -> {
                val n = args.mapNotNull { it.toFloatOrNull() }
                if (n.size < 4) "استفاده: swipe x1 y1 x2 y2"
                else needService() ?: run { svc!!.swipe(n[0], n[1], n[2], n[3]); "سوایپ شد" }
            }
            "scroll" -> {
                needService()?.let { return it }
                val dm = ctx.resources.displayMetrics
                val cx = dm.widthPixels / 2f
                val (a, b) = if (args.getOrNull(0) == "up")
                    (dm.heightPixels * 0.3f) to (dm.heightPixels * 0.7f)
                else
                    (dm.heightPixels * 0.7f) to (dm.heightPixels * 0.3f)
                svc!!.swipe(cx, a, cx, b)
                "اسکرول شد"
            }
            "key" -> {
                needService()?.let { return it }
                when (args.getOrNull(0)) {
                    "back" -> { svc!!.pressBack(); "back" }
                    "home" -> { svc!!.pressHome(); "home" }
                    "recents" -> { svc!!.pressRecents(); "recents" }
                    else -> "استفاده: key back|home|recents"
                }
            }
            "type" -> {
                val text = args.joinToString(" ")
                if (text.isBlank()) "استفاده: type متن"
                else needService() ?: if (svc!!.typeInFocused(text)) "تایپ شد" else "فیلد متنی فعالی پیدا نشد"
            }
            "find" -> {
                val text = args.joinToString(" ")
                if (text.isBlank()) "استفاده: find متن"
                else needService() ?: if (svc!!.clickNodeWithText(text)) "زده شد: $text" else "پیدا نشد: $text"
            }
            "open" -> {
                val target = args.getOrNull(0) ?: return "استفاده: open نام‌اپ یا پکیج"
                val pkg = apps[target.lowercase()] ?: target
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                    "باز شد: $pkg"
                } else "اپ پیدا نشد: $pkg"
            }
            "url" -> {
                val url = args.getOrNull(0) ?: return "استفاده: url آدرس"
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "باز شد: $url"
            }
            "sms" -> {
                val number = args.getOrNull(0) ?: return "استفاده: sms شماره متن"
                val text = args.drop(1).joinToString(" ")
                if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    "مجوز پیامک داده نشده؛ یک‌بار از داخل اپ دستور sms را اجرا کن تا مجوز را بدهی."
                } else try {
                    val sms = ContextCompat.getSystemService(ctx, SmsManager::class.java) ?: SmsManager.getDefault()
                    sms.sendTextMessage(number, null, text, null, null)
                    "پیامک ارسال شد به $number"
                } catch (e: Exception) {
                    "خطا در ارسال پیامک: ${e.message}"
                }
            }
            else -> "دستور ناشناخته: $name"
        }
    }
}
