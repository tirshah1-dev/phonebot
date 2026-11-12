package com.phonebot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

/**
 * سرویس پیش‌زمینه‌ای که هر چند ثانیه یک‌بار:
 *  ۱) وضعیت فعلی (آیدی‌ها + لاگ اعلان‌ها) را رمزنگاری‌شده در مخزن گیت‌هاب می‌نویسد.
 *  ۲) دستورهای در انتظار (که لپ‌تاپ فرستاده) را می‌خواند، اجرا می‌کند و نتیجه را
 *     رمزنگاری‌شده برمی‌گرداند.
 * هیچ پورتی روی گوشی باز نمی‌شود؛ همه‌چیز از طریق مخزن خصوصی گیت‌هاب رد و بدل می‌شود.
 */
class CloudRelayService : Service() {

    private val running = AtomicBoolean(false)
    private var lastCmdId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("در حال شروع…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running.compareAndSet(false, true)) {
            Thread { loop() }.apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        super.onDestroy()
    }

    private fun loop() {
        val ctx = applicationContext
        while (running.get()) {
            try {
                if (Prefs.isConfigured(ctx)) {
                    val token = Prefs.getToken(ctx)!!
                    val owner = Prefs.getOwner(ctx)!!
                    val repo = Prefs.getRepo(ctx)!!
                    val device = Prefs.getDevice(ctx)!!
                    val pass = Prefs.getPass(ctx)!!
                    val relay = GithubRelay(token, owner, repo)
                    pushState(relay, device, pass)
                    pollCommand(relay, device, pass)
                    updateNotification("متصل — آخرین همگام‌سازی: ${nowHms()}")
                } else {
                    updateNotification("تنظیمات ابری کامل نیست")
                }
            } catch (e: Exception) {
                updateNotification("خطای موقت: ${e.message}")
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private fun pushState(relay: GithubRelay, device: String, pass: String) {
        val ctx = applicationContext
        val ids = Store.readIds(ctx)
        val log = Store.readLog(ctx).takeLast(4000)
        val obj = JSONObject().apply {
            put("ts", System.currentTimeMillis())
            put("ids", JSONArray(ids))
            put("log", log)
        }
        val enc = Crypto.encrypt(obj.toString(), pass)
        relay.putFile("relay/data/$device.json", enc, "state update: $device")
    }

    private fun pollCommand(relay: GithubRelay, device: String, pass: String) {
        val file = relay.getFile("relay/commands/$device.json") ?: return
        val decrypted = Crypto.decrypt(file.content, pass) ?: return
        val obj = JSONObject(decrypted)
        val id = obj.optString("id")
        val cmd = obj.optString("cmd")
        if (id.isBlank() || id == lastCmdId) return
        lastCmdId = id

        val result = try {
            CommandRunner.run(applicationContext, cmd)
        } catch (e: Exception) {
            "خطا: ${e.message}"
        }

        val resObj = JSONObject().apply {
            put("id", id)
            put("cmd", cmd)
            put("result", result)
            put("ts", System.currentTimeMillis())
        }
        val encRes = Crypto.encrypt(resObj.toString(), pass)
        relay.putFile("relay/results/$device/$id.json", encRes, "result: $id")
        relay.deleteFile("relay/commands/$device.json", "command consumed: $id")
    }

    private fun nowHms(): String {
        val c = Calendar.getInstance()
        return String.format(
            "%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
        )
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "phonebot_relay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(channelId, "PhoneBot Cloud", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("PhoneBot — اتصال ابری")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val POLL_INTERVAL_MS = 20_000L
    }
}
