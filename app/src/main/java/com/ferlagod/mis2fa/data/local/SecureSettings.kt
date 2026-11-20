// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.data.local

import android.content.Context
import android.util.Log
import com.ferlagod.mis2fa.security.CryptoManager
import com.ferlagod.mis2fa.security.KeystoreCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import javax.crypto.SecretKey

/** Credenciales de Nextcloud. */
data class NextcloudCredentials(
    val serverUrl: String,
    val username: String,
    val appPassword: String
)

/** Todo lo secreto de la sincronización. */
data class SyncConfig(
    val credentials: NextcloudCredentials,
    /** Clave derivada de la contraseña maestra (nunca se guarda la contraseña). */
    val key: SecretKey?,
    val salt: ByteArray?,
    val iterations: Int,
    val lastSync: Long
)

/**
 * Credenciales de Nextcloud y clave de sincronización, en un fichero cifrado con el Keystore.
 */
class SecureSettings private constructor(context: Context) {

    private val file = EncryptedFile(
        File(context.filesDir, "secure/sync.bin"),
        KeystoreCipher(context.applicationContext, LOCAL_KEY_ALIAS),
        "mis2fa:sync-config"
    )

    @Volatile
    private var cache: JSONObject = read()

    private fun read(): JSONObject = try {
        file.read()?.let { JSONObject(String(it, Charsets.UTF_8)) } ?: JSONObject()
    } catch (e: Exception) {
        // Sin las credenciales solo hay que volver a conectar: los códigos no dependen de este fichero.
        Log.w(TAG, "Configuración de sincronización ilegible; se reinicia", e)
        JSONObject()
    }

    /**
     * Cifrar con el Keystore puede tardar cientos de milisegundos con StrongBox: nunca en el hilo
     * principal (provocaría tirones o un "La app no responde").
     */
    private suspend fun edit(block: JSONObject.() -> Unit) = withContext(Dispatchers.IO) {
        synchronized(this@SecureSettings) {
            val next = JSONObject(cache.toString()).apply(block)
            file.write(next.toString().toByteArray(Charsets.UTF_8))
            cache = next
        }
    }

    fun get(): SyncConfig? {
        val json = cache
        val server = json.optString(KEY_SERVER).takeIf { it.isNotBlank() } ?: return null
        val user = json.optString(KEY_USER).takeIf { it.isNotBlank() } ?: return null
        val pass = json.optString(KEY_APP_PASSWORD).takeIf { it.isNotBlank() } ?: return null
        return SyncConfig(
            credentials = NextcloudCredentials(server, user, pass),
            key = json.optString(KEY_SYNC_KEY).takeIf { it.isNotBlank() }?.let { CryptoManager.keyFromBytes(CryptoManager.unb64(it)) },
            salt = json.optString(KEY_SALT).takeIf { it.isNotBlank() }?.let { CryptoManager.unb64(it) },
            iterations = json.optInt(KEY_ITERATIONS, CryptoManager.DEFAULT_PBKDF2_ITERATIONS),
            lastSync = json.optLong(KEY_LAST_SYNC, 0L)
        )
    }

    fun isConfigured(): Boolean = get() != null

    suspend fun save(credentials: NextcloudCredentials, key: SecretKey, salt: ByteArray, iterations: Int) = edit {
        put(KEY_SERVER, credentials.serverUrl.trim().trimEnd('/'))
        put(KEY_USER, credentials.username.trim())
        put(KEY_APP_PASSWORD, credentials.appPassword.trim())
        put(KEY_SYNC_KEY, CryptoManager.b64(key.encoded))
        put(KEY_SALT, CryptoManager.b64(salt))
        put(KEY_ITERATIONS, iterations)
    }

    /** La contraseña maestra cambió en otro dispositivo: hay que pedirla de nuevo. */
    suspend fun clearKey() = edit { remove(KEY_SYNC_KEY) }

    suspend fun setLastSync(time: Long) = edit { put(KEY_LAST_SYNC, time) }

    suspend fun clear() = edit {
        keys().asSequence().toList().forEach { remove(it) }
    }

    companion object {
        private const val TAG = "SecureSettings"
        private const val KEY_SERVER = "server"
        private const val KEY_USER = "user"
        private const val KEY_APP_PASSWORD = "app_password"
        private const val KEY_SYNC_KEY = "sync_key"
        private const val KEY_SALT = "salt"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_LAST_SYNC = "last_sync"

        /** Carpeta de la app dentro de Nextcloud. */
        const val REMOTE_FOLDER = "Mis2FA"

        @Volatile
        private var INSTANCE: SecureSettings? = null

        fun getInstance(context: Context): SecureSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureSettings(context.applicationContext).also { INSTANCE = it }
            }

        /**
         * URL WebDAV de la carpeta de la app: https://servidor/remote.php/dav/files/<usuario>/Mis2FA/
         * Si el usuario pega una URL WebDAV completa, se usa tal cual.
         */
        fun webDavRootUrl(creds: NextcloudCredentials): String {
            val server = creds.serverUrl.trim().trimEnd('/')
            if (server.contains("/remote.php/dav/files/")) return "$server/"
            val user = URLEncoder.encode(creds.username.trim(), "UTF-8").replace("+", "%20")
            return "$server/remote.php/dav/files/$user/$REMOTE_FOLDER/"
        }

        /**
         * Normaliza la dirección del servidor. Solo se admite HTTPS: devuelve null si es HTTP.
         */
        fun normalizeServerUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith("http://", ignoreCase = true)) return null
            return if (trimmed.startsWith("https://", ignoreCase = true)) trimmed else "https://$trimmed"
        }
    }
}
