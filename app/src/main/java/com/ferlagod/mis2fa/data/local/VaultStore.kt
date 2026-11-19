// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.data.local

import android.content.Context
import android.util.Log
import com.ferlagod.mis2fa.model.VaultData
import com.ferlagod.mis2fa.security.KeystoreCipher
import java.io.File

/** La bóveda del dispositivo existe pero no se puede leer (y no hay instantánea legible). */
class VaultUnreadableException(cause: Throwable?) : Exception("Vault unreadable", cause)

/**
 * Bóveda del dispositivo: todas las cuentas en un único fichero cifrado con la clave del Keystore.
 *
 * Para no perder nunca un código:
 * - Escritura atómica (ver [EncryptedFile]).
 * - Antes de sobrescribir se guarda una instantánea cifrada de la versión anterior
 *   (como mucho una por hora, se conservan las [MAX_SNAPSHOTS] más recientes).
 * - Si el fichero principal no se puede leer, se recupera la instantánea legible más reciente.
 */
class VaultStore(context: Context) {

    private companion object {
        const val TAG = "VaultStore"
        const val AAD = "mis2fa:local-vault"
        const val MAX_SNAPSHOTS = 15
        const val SNAPSHOT_INTERVAL_MS = 60 * 60 * 1000L
        const val SNAPSHOT_PREFIX = "vault-"
        const val SNAPSHOT_SUFFIX = ".bin"
    }

    private val dir = File(context.filesDir, "vault")
    private val snapshotsDir = File(dir, "snapshots")
    private val main = EncryptedFile(File(dir, "vault.bin"), KeystoreCipher(context, LOCAL_KEY_ALIAS), AAD)

    /** Datos guardados; bóveda vacía si es el primer arranque. */
    fun load(): VaultData {
        val primaryError = try {
            return main.read()?.let { VaultData.parse(String(it, Charsets.UTF_8)) } ?: VaultData()
        } catch (e: Exception) {
            Log.e(TAG, "No se puede leer la bóveda; se buscan instantáneas", e)
            e
        }
        for (snapshot in snapshots()) {
            try {
                val data = VaultData.parse(String(main.decryptRaw(snapshot.readBytes()), Charsets.UTF_8))
                Log.w(TAG, "Bóveda recuperada de ${snapshot.name}")
                // El fichero dañado no se pierde: se aparta por si algún día se puede analizar.
                runCatching { main.file.copyTo(File(dir, "vault.bin.damaged-${System.currentTimeMillis()}"), overwrite = false) }
                save(data, snapshotPrevious = false)
                return data
            } catch (e: Exception) {
                Log.w(TAG, "Instantánea ilegible: ${snapshot.name}", e)
            }
        }
        throw VaultUnreadableException(primaryError)
    }

    fun save(data: VaultData, snapshotPrevious: Boolean = true) {
        if (snapshotPrevious) snapshotIfDue()
        main.write(data.serialize().toByteArray(Charsets.UTF_8))
    }

    /** Fuerza una instantánea (antes de operaciones masivas como importar o sincronizar por primera vez). */
    fun snapshotNow() = takeSnapshot()

    private fun snapshotIfDue() {
        val latest = snapshots().firstOrNull()
        if (latest == null || System.currentTimeMillis() - latest.lastModified() > SNAPSHOT_INTERVAL_MS) takeSnapshot()
    }

    private fun takeSnapshot() {
        try {
            if (!main.file.exists()) return
            snapshotsDir.mkdirs()
            main.file.copyTo(File(snapshotsDir, "$SNAPSHOT_PREFIX${System.currentTimeMillis()}$SNAPSHOT_SUFFIX"), overwrite = true)
            snapshots().drop(MAX_SNAPSHOTS).forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear la instantánea", e)
        }
    }

    /** Instantáneas de la más reciente a la más antigua. */
    private fun snapshots(): List<File> =
        snapshotsDir.listFiles { f -> f.name.startsWith(SNAPSHOT_PREFIX) && f.name.endsWith(SNAPSHOT_SUFFIX) }
            ?.sortedByDescending { it.name.removePrefix(SNAPSHOT_PREFIX).removeSuffix(SNAPSHOT_SUFFIX).toLongOrNull() ?: 0L }
            .orEmpty()
}

/** Alias de la clave del Keystore que cifra todo lo guardado en el dispositivo. */
const val LOCAL_KEY_ALIAS = "mis2fa_local_key"
