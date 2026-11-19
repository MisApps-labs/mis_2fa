// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.data

import android.content.Context
import android.util.Log
import com.ferlagod.mis2fa.data.local.VaultStore
import com.ferlagod.mis2fa.data.sync.SyncMerger
import com.ferlagod.mis2fa.model.OtpType
import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.model.VaultData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Resultado de añadir varias cuentas a la vez. */
data class AddResult(val added: Int, val duplicates: Int)

/**
 * Fuente única de verdad de las cuentas. Cada cambio se guarda cifrado en el dispositivo antes
 * de publicarse, y avisa a [onLocalChange] para subirlo a Nextcloud.
 */
class TokenRepository private constructor(context: Context) {

    private val store = VaultStore(context.applicationContext)
    private val mutex = Mutex()

    private val _vault = MutableStateFlow(VaultData())
    val vault: StateFlow<VaultData> = _vault.asStateFlow()

    /** La bóveda local no se pudo leer. Mientras sea true no se escribe nada (para no perder datos). */
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    /** Se llama tras cada cambio hecho por el usuario (no tras aplicar una sincronización). */
    @Volatile
    var onLocalChange: (() -> Unit)? = null

    init {
        reload()
    }

    fun reload() {
        try {
            _vault.value = store.load()
            _loadFailed.value = false
        } catch (e: Exception) {
            Log.e(TAG, "No se puede abrir la bóveda local", e)
            _loadFailed.value = true
        }
    }

    fun snapshot(): VaultData = _vault.value

    fun find(id: String): Token? = _vault.value.tokens.firstOrNull { it.id == id }

    /** Momento de modificación estrictamente posterior al anterior, aunque el reloj vaya hacia atrás. */
    private fun stamp(previous: Long) = maxOf(System.currentTimeMillis(), previous + 1)

    private suspend fun mutate(notify: Boolean = true, block: (VaultData) -> VaultData): VaultData = mutex.withLock {
        check(!_loadFailed.value) { "La bóveda local no se puede leer" }
        val current = _vault.value
        val next = block(current)
        if (next != current) {
            withContext(Dispatchers.IO) { store.save(next) }
            _vault.value = next
            if (notify) onLocalChange?.invoke()
        }
        next
    }

    /**
     * Añade cuentas evitando duplicados (mismo secreto, emisor y cuenta). Si el duplicado estaba en
     * la papelera, se restaura.
     */
    suspend fun add(tokens: List<Token>): AddResult {
        var added = 0
        var duplicates = 0
        if (tokens.size > 1) withContext(Dispatchers.IO) { store.snapshotNow() }
        mutate { current ->
            val list = current.tokens.toMutableList()
            val seen = list.associateBy { it.identityKey }.toMutableMap()
            for (incoming in tokens) {
                val existing = seen[incoming.identityKey]
                if (existing != null) {
                    if (existing.isTrashed) {
                        val restored = existing.copy(trashedAt = null, updatedAt = stamp(existing.updatedAt))
                        list[list.indexOfFirst { it.id == existing.id }] = restored
                        seen[incoming.identityKey] = restored
                        added++
                    } else {
                        duplicates++
                    }
                    continue
                }
                val now = System.currentTimeMillis()
                val token = incoming.copy(createdAt = now, updatedAt = now, trashedAt = null)
                list += token
                seen[token.identityKey] = token
                added++
            }
            current.copy(tokens = list)
        }
        return AddResult(added, duplicates)
    }

    suspend fun update(token: Token) = mutate { current ->
        current.copy(tokens = current.tokens.map { if (it.id == token.id) token.copy(updatedAt = stamp(it.updatedAt)) else it })
    }

    private suspend fun modify(ids: Collection<String>, change: (Token) -> Token) = mutate { current ->
        current.copy(tokens = current.tokens.map { if (it.id in ids) change(it).copy(updatedAt = stamp(it.updatedAt)) else it })
    }

    suspend fun setPinned(id: String, pinned: Boolean) = modify(listOf(id)) { it.copy(pinned = pinned) }

    suspend fun trash(ids: Collection<String>) = modify(ids) { it.copy(trashedAt = System.currentTimeMillis()) }

    suspend fun restore(ids: Collection<String>) = modify(ids) { it.copy(trashedAt = null) }

    /** HOTP: avanza el contador para obtener el siguiente código. */
    suspend fun incrementCounter(id: String) = modify(listOf(id)) {
        if (it.type == OtpType.HOTP) it.copy(counter = it.counter + 1) else it
    }

    /** Borra definitivamente (solo desde la papelera) y deja una lápida para el resto de dispositivos. */
    suspend fun deletePermanently(ids: Collection<String>) = mutate { current ->
        val now = System.currentTimeMillis()
        val targets = current.tokens.filter { it.id in ids && it.isTrashed }.map { it.id }.toSet()
        current.copy(
            tokens = current.tokens.filterNot { it.id in targets },
            tombstones = current.tombstones + targets.associateWith { now }
        )
    }

    suspend fun emptyTrash() = deletePermanently(_vault.value.trashed.map { it.id })

    /**
     * Integra lo que llega del servidor. Se vuelve a fusionar con el estado actual por si el usuario
     * cambió algo mientras se sincronizaba.
     *
     * @return el estado local resultante.
     */
    suspend fun mergeFromRemote(remote: VaultData): VaultData = mutate(notify = false) { current ->
        val merged = SyncMerger.merge(current, remote)
        if (SyncMerger.sameContent(merged, current)) current else merged
    }

    /** Copia de seguridad interna antes de una operación arriesgada. */
    suspend fun snapshotNow() = withContext(Dispatchers.IO) { store.snapshotNow() }

    companion object {
        private const val TAG = "TokenRepository"

        @Volatile
        private var INSTANCE: TokenRepository? = null

        fun getInstance(context: Context): TokenRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TokenRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
