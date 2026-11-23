package com.semiovert.app.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class RefreshCookie(val header: String, val expiresAt: Long?) {
    fun usable(now: Long = System.currentTimeMillis()) = expiresAt == null || expiresAt > now

    companion object {
        fun from(headers: List<String>, now: Long = System.currentTimeMillis()): RefreshCookie? {
            for (header in headers) {
                val parts = header.split(';').map(String::trim)
                val value = parts.first().takeIf { it.matches(Regex("[a-zA-Z0-9_.-]+=[a-zA-Z0-9_-]+")) } ?: continue
                if (parts.drop(1).none { it.equals("Path=/api", true) } || parts.none { it.equals("HttpOnly", true) }) continue
                val age = parts.firstOrNull { it.startsWith("Max-Age=", true) }?.substringAfter('=')?.toLongOrNull()
                if (age != null && age <= 0) continue
                return RefreshCookie(value, age?.coerceAtMost(365L * 86_400)?.let { now + it * 1000 })
            }
            return null
        }
    }
}

internal class SessionStore(context: Context, filename: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, filename))
    private val alias = "semi-overt-device-session"

    suspend fun read(): RefreshCookie? = withContext(Dispatchers.IO) {
        try {
            val bytes = file.readFully()
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val value = JSONObject(cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8))
            RefreshCookie(value.getString("cookie"), value.getLong("expires")).takeIf { it.usable() }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    suspend fun write(cookie: RefreshCookie?) = withContext(Dispatchers.IO) {
        if (cookie?.expiresAt == null) {
            file.delete()
            return@withContext
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        val bytes = json("cookie" to cookie.header, "expires" to cookie.expiresAt).toString().toByteArray()
        val stream = file.startWrite()
        try {
            stream.write(cipher.iv)
            stream.write(cipher.doFinal(bytes))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, store.type).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}
