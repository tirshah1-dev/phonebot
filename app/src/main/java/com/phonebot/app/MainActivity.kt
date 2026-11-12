package com.phonebot.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    private lateinit var relayStatus: TextView
    private lateinit var cmdInput: EditText
    private lateinit var inToken: EditText
    private lateinit var inOwner: EditText
    private lateinit var inRepo: EditText
    private lateinit var inDevice: EditText
    private lateinit var inPass: EditText
    private lateinit var btnToggleRelay: Button
    private var pendingCmd: String? = null

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
        relayStatus = findViewById(R.id.relayStatus)
        cmdInput = findViewById(R.id.cmdInput)
        inToken = findViewById(R.id.inToken)
        inOwner = findViewById(R.id.inOwner)
        inRepo = findViewById(R.id.inRepo)
        inDevice = findViewById(R.id.inDevice)
        inPass = findViewById(R.id.inPass)
        btnToggleRelay = findViewById(R.id.btnToggleRelay)

        loadCloudFields()

        findViewById<Button>(R.id.btnNotifAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnRun).setOnClickListener {
            val cmd = cmdInput.text.toString().trim()
            if (cmd.isEmpty()) return@setOnClickListener
            if (cmd.lowercase().startsWith("sms") &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                pendingCmd = cmd
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
                return@setOnClickListener
            }
            output.text = CommandRunner.run(this, cmd)
        }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshStatus() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareIds() }
        findViewById<Button>(R.id.btnSaveCloud).setOnClickListener { saveCloudFields() }
        btnToggleRelay.setOnClickListener { toggleRelay() }

        requestNotifPermissionIfNeeded()
        refreshStatus()
        updateRelayButtonLabel()
        if (Prefs.isRelayEnabled(this) && Prefs.isConfigured(this)) {
            ContextCompat.startForegroundService(this, Intent(this, CloudRelayService::class.java))
        }
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
            pendingCmd?.let { output.text = CommandRunner.run(this, it) }
        }
        pendingCmd = null
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
        val notifOk = if (isNotifAccessEnabled()) "✅ فعال" else "❌ غیرفعال — دکمه‌ی بالا را بزن"
        val accOk = if (isAccessibilityEnabled()) "✅ فعال" else "❌ غیرفعال — دکمه‌ی بالا را بزن"
        statusView.text = "دسترسی اعلان‌ها: $notifOk\nAccessibility: $accOk"
        val ids = Store.readIds(this)
        output.text = "آیدی‌های ذخیره‌شده (${ids.size}):\n" + ids.joinToString("\n") +
            "\n\n---\n\n" + Store.readLog(this).takeLast(4000)
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    // ---------------------------------------------------------------- cloud

    private fun loadCloudFields() {
        inToken.setText(Prefs.getToken(this) ?: "")
        inOwner.setText(Prefs.getOwner(this) ?: "")
        inRepo.setText(Prefs.getRepo(this) ?: "")
        inDevice.setText(Prefs.getDevice(this) ?: Build.MODEL.replace(" ", "-"))
        inPass.setText(Prefs.getPass(this) ?: "")
    }

    private fun saveCloudFields() {
        Prefs.setToken(this, inToken.text.toString().trim())
        Prefs.setOwner(this, inOwner.text.toString().trim())
        Prefs.setRepo(this, inRepo.text.toString().trim())
        Prefs.setDevice(this, inDevice.text.toString().trim().ifBlank { Build.MODEL })
        Prefs.setPass(this, inPass.text.toString())
        toast("تنظیمات ابری ذخیره شد")
        relayStatus.text = if (Prefs.isConfigured(this)) "آماده‌ی اتصال" else "تنظیمات ناقص است"
    }

    private fun toggleRelay() {
        if (!Prefs.isConfigured(this)) {
            saveCloudFields()
            if (!Prefs.isConfigured(this)) {
                toast("همه‌ی فیلدهای اتصال ابری را پر کن")
                return
            }
        }
        val enabled = !Prefs.isRelayEnabled(this)
        Prefs.setRelayEnabled(this, enabled)
        val intent = Intent(this, CloudRelayService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(this, intent)
            relayStatus.text = "اتصال ابری فعال است — هر ۲۰ ثانیه همگام‌سازی می‌شود"
        } else {
            stopService(intent)
            relayStatus.text = "اتصال ابری متوقف شد"
        }
        updateRelayButtonLabel()
    }

    private fun updateRelayButtonLabel() {
        btnToggleRelay.text = if (Prefs.isRelayEnabled(this)) "توقف اتصال" else "شروع اتصال"
    }

    // ---------------------------------------------------------------- misc

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
