package com.phonebot.app

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * رمزنگاری سرتاسری (end-to-end) با AES-256-GCM و کلید مشتق‌شده از رمز عبور
 * (PBKDF2-HMAC-SHA256، ۱۰۰٬۰۰۰ تکرار). فرمت خروجی:
 * base64( salt[16] || iv[12] || ciphertext+tag ).
 *
 * این رمز عبور هیچ‌وقت در گیت‌هاب ذخیره نمی‌شود؛ فقط روی گوشی و لپ‌تاپ خودت
 * نگه داشته می‌شود، بنابراین حتی با دسترسی به ریپو یا توکن، بدون رمز عبور
 * محتوا خوانده نمی‌شود.
 */
object Crypto {
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    fun encrypt(plaintext: String, passphrase: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val blob = salt + iv + ct
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    fun decrypt(blobB64: String, passphrase: String): String? {
        return try {
            val blob = Base64.decode(blobB64, Base64.NO_WRAP or Base64.DEFAULT)
            if (blob.size < SALT_LEN + IV_LEN) return null
            val salt = blob.copyOfRange(0, SALT_LEN)
            val iv = blob.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
            val ct = blob.copyOfRange(SALT_LEN + IV_LEN, blob.size)
            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
