package com.phonebot.app

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * یک کانال ساده و امن روی مخزن خصوصی گیت‌هاب: خواندن/نوشتن فایل‌های
 * رمزنگاری‌شده از طریق GitHub Contents API. به عنوان رله‌ی از راه دور بین
 * گوشی و لپ‌تاپ استفاده می‌شود (هیچ پورتی روی گوشی باز نمی‌شود).
 */
class GithubRelay(private val token: String, private val owner: String, private val repo: String) {

    data class FileResult(val content: String, val sha: String)

    private fun apiUrl(path: String) = "https://api.github.com/repos/$owner/$repo/contents/$path"

    fun getFile(path: String): FileResult? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(apiUrl(path)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = conn.responseCode
            if (code !in 200..299) return null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val contentB64 = json.getString("content").replace("\n", "")
            val sha = json.getString("sha")
            val decoded = String(Base64.decode(contentB64, Base64.DEFAULT), Charsets.UTF_8)
            FileResult(decoded, sha)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun putFile(path: String, content: String, message: String): Boolean {
        val existing = getFile(path)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(apiUrl(path)).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            val bodyJson = JSONObject().apply {
                put("message", message)
                put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                if (existing != null) put("sha", existing.sha)
            }
            conn.outputStream.use { it.write(bodyJson.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    fun deleteFile(path: String, message: String): Boolean {
        val existing = getFile(path) ?: return true
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(apiUrl(path)).openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            val bodyJson = JSONObject().apply {
                put("message", message)
                put("sha", existing.sha)
            }
            conn.outputStream.use { it.write(bodyJson.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
