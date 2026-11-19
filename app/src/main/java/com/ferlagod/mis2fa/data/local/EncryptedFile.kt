// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.data.local

import androidx.core.util.AtomicFile
import com.ferlagod.mis2fa.security.KeystoreCipher
import java.io.File
import java.io.FileNotFoundException

/**
 * Fichero cifrado con la clave del Keystore y escrito de forma atómica: si la app se cierra
 * a mitad de una escritura, se conserva la versión anterior completa.
 */
class EncryptedFile(
    val file: File,
    private val cipher: KeystoreCipher,
    private val aad: String
) {
    fun exists(): Boolean = file.exists()

    /** Devuelve null si no existe. Lanza excepción si existe pero no se puede descifrar. */
    fun read(): ByteArray? {
        val raw = try {
            AtomicFile(file).readFully()
        } catch (_: FileNotFoundException) {
            return null
        }
        return cipher.decrypt(raw, aad)
    }

    fun write(plain: ByteArray) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(cipher.encrypt(plain, aad))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    fun delete() {
        AtomicFile(file).delete()
    }

    /** Descifra otro fichero cifrado con la misma clave y AAD (p. ej. una instantánea). */
    fun decryptRaw(raw: ByteArray): ByteArray = cipher.decrypt(raw, aad)
}
