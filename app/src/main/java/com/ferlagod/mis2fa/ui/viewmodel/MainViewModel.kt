// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ferlagod.mis2fa.R
import com.ferlagod.mis2fa.data.AddResult
import com.ferlagod.mis2fa.data.TokenRepository
import com.ferlagod.mis2fa.data.backup.ImportContent
import com.ferlagod.mis2fa.data.backup.ImportParser
import com.ferlagod.mis2fa.data.local.AppSettings
import com.ferlagod.mis2fa.data.local.LockTimeout
import com.ferlagod.mis2fa.data.local.SortOrder
import com.ferlagod.mis2fa.data.local.ThemeMode
import com.ferlagod.mis2fa.data.sync.SyncError
import com.ferlagod.mis2fa.data.sync.SyncException
import com.ferlagod.mis2fa.data.sync.SyncManager
import com.ferlagod.mis2fa.data.sync.SyncState
import com.ferlagod.mis2fa.data.sync.SyncWorker
import com.ferlagod.mis2fa.data.sync.VaultCodec
import com.ferlagod.mis2fa.data.sync.NewerVaultVersionException
import com.ferlagod.mis2fa.data.sync.VaultEnvelope
import com.ferlagod.mis2fa.data.sync.WrongPasswordException
import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.otp.GoogleMigration
import com.ferlagod.mis2fa.otp.OtpAuthUri
import com.ferlagod.mis2fa.util.QrCodes
import com.ferlagod.mis2fa.util.TextSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Avisos puntuales para la interfaz (se muestran como snackbar). */
sealed interface UiEvent {
    data class Text(@param:StringRes val res: Int, val args: List<Any> = emptyList()) : UiEvent
    data class Plural(@param:PluralsRes val res: Int, val count: Int) : UiEvent
    /** Cuentas enviadas a la papelera (con "Deshacer"). */
    data class Trashed(val ids: List<String>) : UiEvent
}

/** Resultado de leer un QR o un enlace. */
sealed interface ScanOutcome {
    data class Added(val name: String) : ScanOutcome
    data class Duplicate(val name: String) : ScanOutcome
    /** Un QR de un lote de Google Authenticator (index empieza en 0). */
    data class Batch(val result: AddResult, val index: Int, val size: Int, val id: Long = 0) : ScanOutcome
    data object NotOtp : ScanOutcome
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val repository = TokenRepository.getInstance(application)
    private val syncManager = SyncManager.getInstance(application)
    private val appSettings = AppSettings.getInstance(application)

    val settings = appSettings.state
    val syncState: StateFlow<SyncState> = syncManager.state
    val loadFailed: StateFlow<Boolean> = repository.loadFailed

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val activeTokens: StateFlow<List<Token>> = combine(repository.vault, settings) { vault, s ->
        sortTokens(vault.active, s.sortOrder)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, sortTokens(repository.snapshot().active, settings.value.sortOrder))

    val visibleTokens: StateFlow<List<Token>> = combine(activeTokens, _searchQuery) { tokens, query ->
        if (query.isBlank()) tokens else tokens.filter { TextSearch.matches(query, it.issuer, it.account) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, activeTokens.value)

    val trashedTokens: StateFlow<List<Token>> = repository.vault
        .map { v -> v.trashed.sortedByDescending { it.trashedAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.snapshot().trashed)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    /** Operación larga en curso (importar, exportar, conectar…). */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Copia cifrada pendiente de contraseña para importarla. */
    private val _pendingEncryptedImport = MutableStateFlow<VaultEnvelope?>(null)
    val pendingEncryptedImport: StateFlow<VaultEnvelope?> = _pendingEncryptedImport.asStateFlow()

    init {
        // Quien actualiza desde una versión sin tutorial ya tiene cuentas: no se le enseña.
        if (!settings.value.onboardingDone && (repository.loadFailed.value || repository.snapshot().tokens.isNotEmpty())) {
            appSettings.setOnboardingDone()
        }
    }

    private fun emit(event: UiEvent) {
        _events.tryEmit(event)
    }

    private fun sortTokens(tokens: List<Token>, order: SortOrder): List<Token> {
        val byOrder = when (order) {
            SortOrder.ISSUER -> compareBy<Token>({ TextSearch.fold(it.displayName) }, { TextSearch.fold(it.account) })
            SortOrder.ACCOUNT -> compareBy<Token>({ TextSearch.fold(it.account.ifBlank { it.displayName }) }, { TextSearch.fold(it.issuer) })
            SortOrder.NEWEST -> compareByDescending { it.createdAt }
            SortOrder.OLDEST -> compareBy { it.createdAt }
        }
        return tokens.sortedWith(compareByDescending<Token> { it.pinned }.then(byOrder))
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun findToken(id: String): Token? = repository.find(id)

    // ------------------------------------------------------------------
    // Cuentas
    // ------------------------------------------------------------------

    fun saveToken(token: Token, isNew: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        guarded {
            if (isNew) {
                val result = repository.add(listOf(token))
                if (result.added == 0) emit(UiEvent.Text(R.string.account_duplicate, listOf(token.displayName)))
                else emit(UiEvent.Text(R.string.account_added, listOf(token.displayName)))
            } else {
                repository.update(token)
                emit(UiEvent.Text(R.string.account_saved))
            }
            onDone()
        }
    }

    fun trash(ids: List<String>) = viewModelScope.launch {
        guarded {
            repository.trash(ids)
            emit(UiEvent.Trashed(ids))
        }
    }

    fun restore(ids: List<String>) = viewModelScope.launch { guarded { repository.restore(ids) } }

    fun deletePermanently(ids: List<String>) = viewModelScope.launch {
        guarded {
            repository.deletePermanently(ids)
            emit(UiEvent.Plural(R.plurals.accounts_deleted, ids.size))
        }
    }

    fun emptyTrash() = viewModelScope.launch {
        guarded {
            val count = trashedTokens.value.size
            repository.emptyTrash()
            emit(UiEvent.Plural(R.plurals.accounts_deleted, count))
        }
    }

    fun togglePin(id: String) = viewModelScope.launch {
        guarded {
            val token = repository.find(id) ?: return@guarded
            repository.setPinned(id, !token.pinned)
        }
    }

    fun incrementCounter(id: String) = viewModelScope.launch { guarded { repository.incrementCounter(id) } }

    /** Ejecuta una operación de la bóveda mostrando un aviso si no se puede guardar. */
    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Operación fallida", e)
            emit(UiEvent.Text(if (repository.loadFailed.value) R.string.error_vault_unreadable else R.string.error_save_failed))
        }
    }

    // ------------------------------------------------------------------
    // Escanear / enlaces
    // ------------------------------------------------------------------

    /** Procesa el texto de un QR o de un enlace `otpauth://` y añade las cuentas. */
    suspend fun handleScanned(text: String): ScanOutcome {
        val trimmed = text.trim()
        return try {
            when {
                OtpAuthUri.isOtpAuth(trimmed) -> {
                    val token = OtpAuthUri.parse(trimmed)
                    val result = repository.add(listOf(token))
                    if (result.added > 0) ScanOutcome.Added(token.displayName) else ScanOutcome.Duplicate(token.displayName)
                }
                GoogleMigration.isMigration(trimmed) -> {
                    val batch = GoogleMigration.parse(trimmed)
                    ScanOutcome.Batch(repository.add(batch.tokens), batch.index, batch.size, batch.id)
                }
                else -> ScanOutcome.NotOtp
            }
        } catch (_: IllegalArgumentException) {
            ScanOutcome.NotOtp
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo guardar", e)
            emit(UiEvent.Text(R.string.error_save_failed))
            ScanOutcome.NotOtp
        }
    }

    /** Enlace abierto desde otra app: se añade y se avisa del resultado. */
    fun handleExternalLink(text: String) = viewModelScope.launch { reportScan(handleScanned(text)) }

    fun reportScan(outcome: ScanOutcome) {
        when (outcome) {
            is ScanOutcome.Added -> emit(UiEvent.Text(R.string.account_added, listOf(outcome.name)))
            is ScanOutcome.Duplicate -> emit(UiEvent.Text(R.string.account_duplicate, listOf(outcome.name)))
            is ScanOutcome.Batch -> reportAdd(outcome.result)
            ScanOutcome.NotOtp -> emit(UiEvent.Text(R.string.scan_not_otp))
        }
    }

    private fun reportAdd(result: AddResult, skipped: Int = 0) {
        when {
            result.added == 0 && result.duplicates == 0 && skipped == 0 -> emit(UiEvent.Text(R.string.import_nothing))
            result.duplicates == 0 && skipped == 0 -> emit(UiEvent.Plural(R.plurals.import_added, result.added))
            else -> emit(UiEvent.Text(R.string.import_summary, listOf(result.added, result.duplicates + skipped)))
        }
    }

    /** Lee el QR de una imagen de la galería. */
    fun importFromImage(uri: Uri, onAdded: () -> Unit = {}) = viewModelScope.launch {
        _busy.value = true
        val text = withContext(Dispatchers.Default) { QrCodes.decodeImage(app, uri) }
        _busy.value = false
        if (text == null) {
            emit(UiEvent.Text(R.string.scan_image_no_qr))
            return@launch
        }
        val outcome = handleScanned(text)
        reportScan(outcome)
        if (outcome !is ScanOutcome.NotOtp) onAdded()
    }

    // ------------------------------------------------------------------
    // Importar / exportar
    // ------------------------------------------------------------------

    fun importFile(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        try {
            val text = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
                    if (bytes.size > MAX_IMPORT_BYTES) throw IllegalArgumentException("Fichero demasiado grande")
                    String(bytes, Charsets.UTF_8)
                }
            } ?: throw IllegalArgumentException("Sin contenido")
            when (val content = ImportParser.parse(text)) {
                is ImportContent.Tokens -> reportAdd(repository.add(content.tokens), content.skipped)
                is ImportContent.Encrypted -> _pendingEncryptedImport.value = content.envelope
                is ImportContent.Unsupported -> emit(
                    UiEvent.Text(
                        when (content.reason) {
                            ImportContent.Unsupported.Reason.AEGIS_ENCRYPTED -> R.string.import_aegis_encrypted
                            ImportContent.Unsupported.Reason.TWOFAS_ENCRYPTED -> R.string.import_2fas_encrypted
                            ImportContent.Unsupported.Reason.UNKNOWN -> R.string.import_unknown_format
                        }
                    )
                )
            }
        } catch (_: NewerVaultVersionException) {
            emit(UiEvent.Text(R.string.import_newer_version))
        } catch (e: Exception) {
            Log.w(TAG, "Importación fallida", e)
            emit(UiEvent.Text(R.string.import_unknown_format))
        } finally {
            _busy.value = false
        }
    }

    fun cancelEncryptedImport() {
        _pendingEncryptedImport.value = null
    }

    /** Abre la copia cifrada pendiente. Llama a [onWrongPassword] si la contraseña no es correcta. */
    fun importEncrypted(password: String, onWrongPassword: () -> Unit) = viewModelScope.launch {
        val envelope = _pendingEncryptedImport.value ?: return@launch
        _busy.value = true
        try {
            val key = VaultCodec.unlock(envelope, password)
            val data = withContext(Dispatchers.Default) { envelope.decrypt(key) }
            _pendingEncryptedImport.value = null
            reportAdd(repository.add(data.active))
        } catch (_: WrongPasswordException) {
            onWrongPassword()
        } catch (e: Exception) {
            Log.w(TAG, "Copia cifrada ilegible", e)
            _pendingEncryptedImport.value = null
            emit(UiEvent.Text(R.string.import_unknown_format))
        } finally {
            _busy.value = false
        }
    }

    fun exportEncrypted(uri: Uri, password: String) = viewModelScope.launch {
        _busy.value = true
        try {
            val content = VaultCodec.exportBackup(repository.snapshot(), password)
            writeTo(uri, content)
            emit(UiEvent.Plural(R.plurals.export_done, repository.snapshot().active.size))
        } catch (e: Exception) {
            Log.w(TAG, "Exportación fallida", e)
            emit(UiEvent.Text(R.string.export_failed))
        } finally {
            _busy.value = false
        }
    }

    fun exportPlain(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        try {
            writeTo(uri, ImportParser.exportPlain(activeTokens.value))
            emit(UiEvent.Plural(R.plurals.export_done, activeTokens.value.size))
        } catch (e: Exception) {
            Log.w(TAG, "Exportación fallida", e)
            emit(UiEvent.Text(R.string.export_failed))
        } finally {
            _busy.value = false
        }
    }

    private suspend fun writeTo(uri: Uri, content: String) = withContext(Dispatchers.IO) {
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("No se puede escribir")
        out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    // ------------------------------------------------------------------
    // Sincronización
    // ------------------------------------------------------------------

    fun syncNow() = viewModelScope.launch {
        if (syncManager.isConfigured()) syncManager.sync()
    }

    /** Momento (reloj monotónico) de la última sincronización automática al volver a la app. */
    private var lastAutoSync = 0L

    /**
     * Al volver a la app se traen los cambios de otros dispositivos, pero no cada vez: volver del
     * selector de ficheros, de la cámara o de la huella también pasa por onStart.
     */
    fun syncOnReturn() {
        val now = SystemClock.elapsedRealtime()
        if (lastAutoSync != 0L && now - lastAutoSync < AUTO_SYNC_MIN_INTERVAL_MS) return
        lastAutoSync = now
        syncNow()
    }

    fun syncConfig() = syncManager.config()

    fun connect(server: String, user: String, appPassword: String, masterPassword: String, onResult: (SyncError?) -> Unit) =
        viewModelScope.launch {
            _busy.value = true
            try {
                syncManager.connect(server, user, appPassword, masterPassword)
                SyncWorker.schedulePeriodic(app)
                val state = syncManager.state.value
                onResult((state as? SyncState.Failed)?.error)
            } catch (e: SyncException) {
                onResult(e.error)
            } finally {
                _busy.value = false
            }
        }

    /** La contraseña maestra cambió en otro dispositivo: se vuelve a conectar con la nueva. */
    fun reenterMasterPassword(masterPassword: String, onResult: (SyncError?) -> Unit) {
        val creds = syncManager.config()?.credentials ?: return onResult(SyncError.UNKNOWN)
        connect(creds.serverUrl, creds.username, creds.appPassword, masterPassword, onResult)
    }

    fun changeMasterPassword(newPassword: String, onResult: (SyncError?) -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            syncManager.changePassword(newPassword)
            onResult(null)
        } catch (e: SyncException) {
            onResult(e.error)
        } finally {
            _busy.value = false
        }
    }

    fun disconnect() = viewModelScope.launch {
        syncManager.disconnect()
        SyncWorker.cancelAll(app)
        emit(UiEvent.Text(R.string.sync_disconnected))
    }

    // ------------------------------------------------------------------
    // Ajustes
    // ------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = appSettings.setThemeMode(mode)
    fun setAmoled(enabled: Boolean) = appSettings.setAmoled(enabled)
    fun setSortOrder(order: SortOrder) = appSettings.setSortOrder(order)
    fun setAppLock(enabled: Boolean) = appSettings.setAppLock(enabled)
    fun setLockTimeout(timeout: LockTimeout) = appSettings.setLockTimeout(timeout)
    fun setBlockScreenshots(enabled: Boolean) = appSettings.setBlockScreenshots(enabled)
    fun setHideCodes(enabled: Boolean) = appSettings.setHideCodes(enabled)
    fun setShowNextCode(enabled: Boolean) = appSettings.setShowNextCode(enabled)
    fun setShowAccountName(enabled: Boolean) = appSettings.setShowAccountName(enabled)
    fun setClipboardClearSeconds(seconds: Int) = appSettings.setClipboardClearSeconds(seconds)
    fun dismissHint(key: String) = appSettings.dismissHint(key)
    fun finishOnboarding() = appSettings.setOnboardingDone()

    companion object {
        private const val TAG = "MainViewModel"
        /** Ninguna exportación razonable pasa de esto; evita cargar ficheros enormes en memoria. */
        private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
        private const val AUTO_SYNC_MIN_INTERVAL_MS = 30_000L
    }
}

/** `readNBytes` solo existe en Android 13+. */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (total < max) {
        val n = read(buffer, 0, minOf(buffer.size, max - total))
        if (n < 0) break
        out.write(buffer, 0, n)
        total += n
    }
    return out.toByteArray()
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(application) as T
}
