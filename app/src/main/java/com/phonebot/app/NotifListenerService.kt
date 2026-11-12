package com.phonebot.app

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * با فعال‌سازی «Notification access» در تنظیمات گوشی، این سرویس تمام اعلان‌های
 * سیستم را می‌بیند، آن‌ها را در notifications.txt لاگ می‌کند و آیدی/نام‌کاربری
 * موجود در متنشان را در ids.txt ذخیره می‌کند.
 */
class NotifListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val body = if (bigText.isNotBlank()) bigText else text
        if (title.isBlank() && body.isBlank()) return

        val pkg = sbn.packageName
        val head = "[$pkg] $title" + if (sub.isNotBlank()) " ($sub)" else ""
        Store.appendLog(this, if (body.isNotBlank()) "$head\n    $body" else head)

        val ids = IdExtractor.extract("$title $body $sub")
        Store.saveIds(this, ids)

        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName))
    }

    companion object {
        const val ACTION_UPDATE = "com.phonebot.app.NOTIF_UPDATE"
    }
}
