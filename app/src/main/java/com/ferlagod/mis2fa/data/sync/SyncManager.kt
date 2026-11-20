// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.data.sync

import android.content.Context
import android.util.Log
import com.ferlagod.mis2fa.data.TokenRepository
import com.ferlagod.mis2fa.data.local.NextcloudCredentials
import com.ferlagod.mis2fa.data.local.SecureSettings
import com.ferlagod.mis2fa.data.local.SyncConfig
import com.ferlagod.mis2fa.data.remote.RemoteFile
import com.ferlagod.mis2fa.data.remote.WebDavAuthException
import com.ferlagod.mis2fa.data.remote.WebDavClient
import com.ferlagod.mis2fa.data.remote.WebDavException
import com.ferlagod.mis2fa.data.remote.WebDavInvalidUrlException
import com.ferlagod.mis2fa.data.remote.WebDavPathNotFoundException
import com.ferlagod.mis2fa.data.remote.WebDavPreconditionFailedException
import com.ferlagod.mis2fa.data.remote.WebDavTooLargeException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.crypto.SecretKey

/** Motivo de un fallo de sincronización (la interfaz lo traduce a un texto). */
enum class SyncError {
    /** Usuario o contraseña de aplicación incorrectos. */
    AUTH,
    /** Sin conexión o el servidor no responde. */
    NETWORK,
    /** La dirección del servidor o el usuario no son correctos. */
    PATH_NOT_FOUND,
    /** La contraseña maestra no abre la bóveda del servidor. */
    WRONG_PASSWORD,
    /** La contraseña maestra se cambió en otro dispositivo: hay que volver a escribirla. */
    PASSWORD_CHANGED,
    /** El fichero del servidor está dañado o es de otra versión (no se toca). */
    REMOTE_CORRUPT,
    /** Solo se admiten servidores HTTPS. */
    HTTPS_REQUIRED,
    /** La bóveda del dispositivo no se puede leer. */
    LOCAL_UNREADABLE,
    SERVER,
    UNKNOWN
}

class SyncException(val error: SyncError, cause: Throwable? = null) : Exception(error.name, cause)

sealed interface SyncState {
    data object NotConfigured : SyncState
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val timestamp: Long) : SyncState
    data class Failed(val error: SyncError) : SyncState
}

/**
 * Sincronización con Nextcloud: un único fichero cifrado `Mis2FA/vault.json`.
 *
 * Cada sincronización descarga el fichero, lo fusiona con lo local ([SyncMerger]) y sube el
 * resultado con `If-Match`. Si otro dispositivo subió algo entretanto, el servidor responde 412
 * y se repite el ciclo, así que ningún cambio se pisa.
 */
class SyncManager private constructor(context: Context) {

    private val secure = SecureSettings.getInstance(context)
    private val repository = TokenRepository.getInstance(context)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SyncState>(initialState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private fun initialState(): SyncState {
        val config = secure.get() ?: return SyncState.NotConfigured
        return if (config.key == null) SyncState.Failed(SyncError.PASSWORD_CHANGED) else SyncState.Idle
    }

    fun config(): SyncConfig? = secure.get()

    fun isConfigured(): Boolean = secure.isConfigured()

    private fun client(creds: NextcloudCredentials) =
        WebDavClient(SecureSettings.webDavRootUrl(creds), creds.username.trim(), creds.appPassword.trim())

    /**
     * Conecta una cuenta (o vuelve a escribir la contraseña maestra si cambió en otro dispositivo).
     * Si el servidor ya tiene una bóveda, la contraseña debe abrirla; si no, se crea una nueva.
     * Las cuentas del dispositivo se suben y se unen a las del servidor.
     *
     * @throws SyncException
     */
    suspend fun connect(serverUrl: String, username: String, appPassword: String, masterPassword: String) {
        val server = SecureSettings.normalizeServerUrl(serverUrl) ?: throw SyncException(SyncError.HTTPS_REQUIRED)
        mutex.withLock {
            _state.value = SyncState.Syncing
            try {
                val user = username.trim()
                val pass = appPassword.trim()
                val userId = if (server.contains("/remote.php/dav/files/")) null
                else WebDavClient.fetchUserId(server, user, pass)?.takeIf { it != user }
                val creds = NextcloudCredentials(server, user, pass, userId)
                val client = client(creds)
                client.ensureRootFolder()
                val remote = client.download(FILE_NAME)
                if (remote != null) {
                    val envelope = VaultCodec.parse(String(remote.bytes, Charsets.UTF_8))
                    val key = VaultCodec.unlock(envelope, masterPassword)
                    secure.save(creds, key, envelope.salt, envelope.iterations)
                } else {
                    val (key, salt, iterations) = VaultCodec.newKey(masterPassword)
                    secure.save(creds, key, salt, iterations)
                }
                // Copia interna antes de mezclar con el servidor por primera vez.
                repository.snapshotNow()
            } catch (e: Exception) {
                _state.value = if (secure.isConfigured()) SyncState.Failed(map(e)) else SyncState.NotConfigured
                throw SyncException(map(e), e)
            }
        }
        sync()
    }

    /** Sincroniza ahora. Nunca lanza excepciones: el resultado queda en [state]. */
    suspend fun sync(): SyncState = mutex.withLock {
        val config = secure.get() ?: return@withLock SyncState.NotConfigured.also { _state.value = it }
        val key = config.key ?: return@withLock SyncState.Failed(SyncError.PASSWORD_CHANGED).also { _state.value = it }
        if (repository.loadFailed.value) return@withLock SyncState.Failed(SyncError.LOCAL_UNREADABLE).also { _state.value = it }
        _state.value = SyncState.Syncing
        val result = try {
            syncOnce(client(config.credentials), config, key)
            val now = System.currentTimeMillis()
            secure.setLastSync(now)
            SyncState.Success(now)
        } catch (e: Exception) {
            Log.w(TAG, "Sincronización fallida", e)
            val error = map(e)
            if (error == SyncError.PASSWORD_CHANGED) secure.clearKey()
            SyncState.Failed(error)
        }
        _state.value = result
        result
    }

    private suspend fun syncOnce(client: WebDavClient, config: SyncConfig, key: SecretKey) {
        client.ensureRootFolder()
        repeat(MAX_ATTEMPTS) {
            val remote: RemoteFile? = client.download(FILE_NAME)
            try {
                if (remote == null) {
                    // Primera subida, o el fichero desapareció del servidor: se recrea con lo local.
                    val salt = config.salt ?: throw SyncException(SyncError.PASSWORD_CHANGED)
                    val content = VaultCodec.encode(VaultCodec.FORMAT_SYNC, repository.snapshot(), key, salt, config.iterations)
                    client.upload(FILE_NAME, content, ifNoneMatch = true)
                    return
                }
                val envelope = VaultCodec.parse(String(remote.bytes, Charsets.UTF_8))
                if (envelope.format != VaultCodec.FORMAT_SYNC) throw SyncException(SyncError.REMOTE_CORRUPT)
                if (!envelope.sameParams(config.salt, config.iterations) || !envelope.verify(key)) {
                    throw SyncException(SyncError.PASSWORD_CHANGED)
                }
                val remoteData = envelope.decrypt(key)
                val local = repository.mergeFromRemote(remoteData)
                if (!SyncMerger.sameContent(local, remoteData)) {
                    val content = VaultCodec.encode(VaultCodec.FORMAT_SYNC, local, key, envelope.salt, envelope.iterations)
                    replace(client, remote, content)
                }
                return
            } catch (_: WebDavPreconditionFailedException) {
                // Otro dispositivo subió cambios mientras tanto: se vuelve a descargar y fusionar.
            }
        }
        throw SyncException(SyncError.SERVER)
    }

    /**
     * Cambia la contraseña maestra: vuelve a cifrar la bóveda del servidor con una clave nueva.
     * El resto de dispositivos pedirán la contraseña nueva en su próxima sincronización.
     *
     * @throws SyncException
     */
    suspend fun changePassword(newPassword: String) {
        val first = sync()
        if (first !is SyncState.Success) throw SyncException((first as? SyncState.Failed)?.error ?: SyncError.UNKNOWN)
        mutex.withLock {
            val config = secure.get() ?: throw SyncException(SyncError.UNKNOWN)
            val key = config.key ?: throw SyncException(SyncError.PASSWORD_CHANGED)
            try {
                val client = client(config.credentials)
                val remote = client.download(FILE_NAME) ?: throw SyncException(SyncError.SERVER)
                val envelope = VaultCodec.parse(String(remote.bytes, Charsets.UTF_8))
                if (!envelope.verify(key)) throw SyncException(SyncError.PASSWORD_CHANGED)
                val merged = repository.mergeFromRemote(envelope.decrypt(key))
                val (newKey, salt, iterations) = VaultCodec.newKey(newPassword)
                val content = VaultCodec.encode(VaultCodec.FORMAT_SYNC, merged, newKey, salt, iterations)
                replace(client, remote, content)
                secure.save(config.credentials, newKey, salt, iterations)
                secure.setLastSync(System.currentTimeMillis())
                _state.value = SyncState.Success(System.currentTimeMillis())
            } catch (e: Exception) {
                throw SyncException(map(e), e)
            }
        }
    }

    /**
     * Sustituye el fichero remoto solo si sigue siendo [remote].
     *
     * Con ETag se usa `If-Match`. Algunos servidores o proxies no lo envían: antes se mandaba
     * `If-None-Match: *` sobre un fichero que ya existe, que siempre da 412, y la sincronización
     * no terminaba nunca. Sin ETag se vuelve a descargar justo antes y se compara el contenido
     * (queda una ventana mínima, inevitable sin ayuda del servidor).
     *
     * @throws WebDavPreconditionFailedException si el fichero cambió entretanto.
     */
    private suspend fun replace(client: WebDavClient, remote: RemoteFile, content: String) {
        if (remote.etag != null) {
            client.upload(FILE_NAME, content, ifMatch = remote.etag)
            return
        }
        Log.w(TAG, "El servidor no envía ETag: se comprueba el contenido antes de subir")
        val current = client.download(FILE_NAME)
        if (current == null || !current.bytes.contentEquals(remote.bytes)) throw WebDavPreconditionFailedException()
        client.upload(FILE_NAME, content)
    }

    /** Desconecta la cuenta. Las cuentas del dispositivo y las del servidor no se tocan. */
    suspend fun disconnect() = mutex.withLock {
        secure.clear()
        _state.value = SyncState.NotConfigured
    }

    private fun map(e: Throwable): SyncError = when (e) {
        is SyncException -> e.error
        is WrongPasswordException -> SyncError.WRONG_PASSWORD
        is InvalidVaultFileException -> SyncError.REMOTE_CORRUPT
        is WebDavAuthException -> SyncError.AUTH
        is WebDavPathNotFoundException -> SyncError.PATH_NOT_FOUND
        is WebDavInvalidUrlException -> SyncError.PATH_NOT_FOUND
        is WebDavPreconditionFailedException -> SyncError.SERVER
        is WebDavTooLargeException -> SyncError.REMOTE_CORRUPT
        is WebDavException -> if (e.code == 404) SyncError.PATH_NOT_FOUND else SyncError.SERVER
        is IOException -> SyncError.NETWORK
        // El check() de TokenRepository cuando la bóveda local no se puede leer.
        is IllegalStateException -> if (repository.loadFailed.value) SyncError.LOCAL_UNREADABLE else SyncError.UNKNOWN
        else -> SyncError.UNKNOWN
    }

    companion object {
        private const val TAG = "SyncManager"
        const val FILE_NAME = "vault.json"
        private const val MAX_ATTEMPTS = 4

        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
