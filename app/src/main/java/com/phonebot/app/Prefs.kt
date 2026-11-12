package com.phonebot.app

import android.content.Context

/** ذخیره‌ی تنظیمات اتصال ابری (توکن، ریپو، نام دستگاه، رمز عبور). */
object Prefs {
    private const val NAME = "phonebot_prefs"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getToken(ctx: Context): String? = sp(ctx).getString("token", null)
    fun setToken(ctx: Context, v: String) = sp(ctx).edit().putString("token", v).apply()

    fun getOwner(ctx: Context): String? = sp(ctx).getString("owner", null)
    fun setOwner(ctx: Context, v: String) = sp(ctx).edit().putString("owner", v).apply()

    fun getRepo(ctx: Context): String? = sp(ctx).getString("repo", null)
    fun setRepo(ctx: Context, v: String) = sp(ctx).edit().putString("repo", v).apply()

    fun getDevice(ctx: Context): String? = sp(ctx).getString("device", null)
    fun setDevice(ctx: Context, v: String) = sp(ctx).edit().putString("device", v).apply()

    fun getPass(ctx: Context): String? = sp(ctx).getString("pass", null)
    fun setPass(ctx: Context, v: String) = sp(ctx).edit().putString("pass", v).apply()

    fun isRelayEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("relay_enabled", false)
    fun setRelayEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("relay_enabled", v).apply()

    fun isConfigured(ctx: Context): Boolean =
        !getToken(ctx).isNullOrBlank() && !getOwner(ctx).isNullOrBlank() &&
            !getRepo(ctx).isNullOrBlank() && !getDevice(ctx).isNullOrBlank() && !getPass(ctx).isNullOrBlank()
}
