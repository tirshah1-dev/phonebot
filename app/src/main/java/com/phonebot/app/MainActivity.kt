package com.phonebot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.util.DisplayMetrics
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {

    private lateinit var output: TextView
    private lateinit var statusView: TextView
    private lateinit var cmdInput: EditText

    private val apps = mapOf(
        "telegram" to "org.telegram.messenger",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "chrome" to "com.android.chrome",
        "settings" to "com.android.settings",
        "youtube" to "com.google.android.youtube",
        "messages" to "com.google.android.apps.messaging",
    )

    private var pendingSms: Pair<String, String>? = null

    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        output = findViewById(R.id.output)
        statusView = findViewById(R.id.status)
        cmdInput = findViewById(R.id.cmdInput)

        findViewById<Button>(R.id.btnNotifAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnRun).setOnClickListener {
            val cmd = cmdInput.text.toString().trim()
            if (cmd.isNotEmpty()) runCommand(cmd)
        }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshStatus() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareIds() }

        refreshStatus()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, notifReceiver, IntentFilter(NotifListenerService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(notifReceiver) } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            pendingSms?.let { (number, text) -> sendSms(number, text) }
        }
        pendingSms = null
    }

    // ---------------------------------------------------------------- status

    private fun isNotifAccessEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${ControlAccessibilityService::class.java.name}")
    }

    private fun refreshStatus() {
        val notifOk = if (isNotifAccessEnabled()) "✅ فعال" else "❌ غیرفعال"
        val accOk = if (isAccessibilityEnabled()) "✅ فعال" else "❌ غیرفعال"
        statusView.text = "دسترسی اعلان‌ها: $notifOk\nAccessibility: $accOk"
        val ids = Store.readIds(this)
        output.text = "آیدی‌های ذخیره‌شده (${ids.size}):\n" + ids.joinToString("\n") +
                "\n\n---\n\n" + Store.readLog(this).takeLast(4000)
    }

    // ---------------------------------------------------------------- commands

    private fun runCommand(line: String) {
        val parts = line.trim().split(Regex("\\s+"))
        val name = parts.getOrNull(0)?.lowercase() ?: return
        val args = parts.drop(1)
        val svc = ControlAccessibilityService.instance

        fun needService(): Boolean {
            if (svc == null) {
                toast("اول Accessibility را در تنظیمات فعال کن.")
                return false
            }
            return true
        }

        when (name) {
            "notifs" -> output.text = Store.readLog(this).takeLast(6000)
            "ids" -> output.text = Store.readIds(this).joinToString("\n")
            "screen" -> { if (needService()) output.text = svc!!.dumpScreenText() }
            "shot" -> {
                if (needService()) svc!!.takeScreenshotToFile(this) { file ->
                    toast(if (file != null) "ذخیره شد: ${file.name}" else "اسکرین‌شات ناموفق بود")
                }
            }
            "tap" -> {
                val x = args.getOrNull(0)?.toFloatOrNull()
                val y = args.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null && needService()) svc!!.tap(x, y) else toast("tap X Y")
            }
            "swipe" -> {
                val n = args.mapNotNull { it.toFloatOrNull() }
                if (n.size >= 4 && needService()) svc!!.swipe(n[0], n[1], n[2], n[3])
                else toast("swipe x1 y1 x2 y2")
            }
            "scroll" -> {
                if (!needService()) return
                val dm: DisplayMetrics = resources.displayMetrics
                val cx = dm.widthPixels / 2f
                val (a, b) = if (args.getOrNull(0) == "up")
                    (dm.heightPixels * 0.3f) to (dm.heightPixels * 0.7f)
                else
                    (dm.heightPixels * 0.7f) to (dm.heightPixels * 0.3f)
                svc!!.swipe(cx, a, cx, b)
            }
            "key" -> {
                if (!needService()) return
                when (args.getOrNull(0)) {
                    "back" -> svc!!.pressBack()
                    "home" -> svc!!.pressHome()
                    "recents" -> svc!!.pressRecents()
                    else -> toast("key back|home|recents")
                }
            }
            "type" -> {
                val text = args.joinToString(" ")
                if (text.isNotBlank() && needService()) {
                    if (!svc!!.typeInFocused(text)) toast("فیلد متنی فعالی پیدا نشد")
                } else toast("type متن")
            }
            "find" -> {
                val text = args.joinToString(" ")
                if (text.isNotBlank() && needService()) {
                    if (!svc!!.clickNodeWithText(text)) toast("پیدا نشد: $text")
                } else toast("find متن")
            }
            "open" -> {
                val target = args.getOrNull(0) ?: return toast("open نام‌اپ یا پکیج")
                val pkg = apps[target.lowercase()] ?: target
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) startActivity(intent) else toast("اپ پیدا نشد: $pkg")
            }
            "url" -> {
                val url = args.getOrNull(0) ?: return toast("url آدرس")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            "sms" -> {
                val number = args.getOrNull(0) ?: return toast("sms شماره متن")
                val text = args.drop(1).joinToString(" ")
                sendSms(number, text)
            }
            else -> toast("دستور ناشناخته: $name")
        }
    }

    private fun sendSms(number: String, text: String) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSms = number to text
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE
            )
            return
        }
        try {
            val sms = getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            sms.sendTextMessage(number, null, text, null, null)
            toast("پیامک ارسال شد")
        } catch (e: Exception) {
            toast("خطا در ارسال پیامک: ${e.message}")
        }
    }

    private fun shareIds() {
        val file = Store.idsFile(this)
        if (!file.exists()) { toast("هنوز آیدی‌ای ذخیره نشده"); return }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "اشتراک‌گذاری ids.txt"))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val SMS_PERMISSION_CODE = 101
    }
}
