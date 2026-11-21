// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.data.sync

import com.ferlagod.mis2fa.model.VaultData
import com.ferlagod.mis2fa.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.crypto.SecretKey

/** El fichero no tiene el formato esperado (dañado, de otra app o de una versión futura). */
open class InvalidVaultFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** El fichero es de una versión más nueva de la app: hay que actualizar, no está dañado. */
class NewerVaultVersionException : InvalidVaultFileException("Versión no soportada")

/** La contraseña no abre el fichero. */
class WrongPasswordException : Exception("Wrong password")

/**
 * Fichero cifrado con contraseña. Es el mismo formato para la bóveda de Nextcloud
 * (`Mis2FA/vault.json`, [FORMAT_SYNC]) y para las copias de seguridad exportadas ([FORMAT_BACKUP]):
 *
 * ```json
 * {
 *   "format": "mis2fa-vault",
 *   "version": 1,
 *   "kdf": { "name": "PBKDF2WithHmacSHA256", "iterations": 600000, "salt": "<Base64>" },
 *   "check": "<Base64: AES-GCM('mis2fa-key-check-v1'), AAD 'mis2fa-vault:check'>",
 *   "data":  "<Base64: AES-GCM(JSON de las cuentas), AAD 'mis2fa-vault:data'>"
 * }
 * ```
 *
 * Todo lo que describe las cuentas (emisor, nombre, secreto, fechas…) va dentro de `data`.
 */
data class VaultEnvelope(
    val format: String,
    val salt: ByteArray,
    val iterations: Int,
    val check: String,
    val data: String
) {
    fun verify(key: SecretKey): Boolean = CryptoManager.verify(check, VaultCodec.CHECK_PLAINTEXT, key, "$format:check")

    fun decrypt(key: SecretKey): VaultData = try {
        VaultData.parse(CryptoManager.decrypt(data, key, "$format:data"))
    } catch (e: Exception) {
        throw InvalidVaultFileException("No se pueden leer los datos cifrados", e)
    }

    fun sameParams(salt: ByteArray?, iterations: Int): Boolean =
        salt != null && this.salt.contentEquals(salt) && this.iterations == iterations

    override fun equals(other: Any?): Boolean = other is VaultEnvelope && format == other.format &&
        salt.contentEquals(other.salt) && iterations == other.iterations && check == other.check && data == other.data

    override fun hashCode(): Int = data.hashCode()
}

object VaultCodec {
    const val FORMAT_SYNC = "mis2fa-vault"
    const val FORMAT_BACKUP = "mis2fa-backup"
    const val VERSION = 1
    const val CHECK_PLAINTEXT = "mis2fa-key-check-v1"

    fun looksLikeEnvelope(raw: String): Boolean = try {
        JSONObject(raw).optString("format").let { it == FORMAT_SYNC || it == FORMAT_BACKUP }
    } catch (_: Exception) {
        false
    }

    fun parse(raw: String): VaultEnvelope {
        try {
            val json = JSONObject(raw)
            val format = json.getString("format")
            if (format != FORMAT_SYNC && format != FORMAT_BACKUP) throw InvalidVaultFileException("Formato desconocido: $format")
            if (json.optInt("version", 0) > VERSION) throw NewerVaultVersionException()
            val kdf = json.getJSONObject("kdf")
            if (kdf.optString("name") != CryptoManager.KDF_ALGORITHM) throw InvalidVaultFileException("Derivación no soportada")
            val salt = CryptoManager.unb64(kdf.getString("salt"))
            val iterations = kdf.getInt("iterations")
            if (salt.size < CryptoManager.MIN_SALT_LENGTH_BYTES) throw InvalidVaultFileException("Salt demasiado corto")
            if (iterations !in CryptoManager.MIN_PBKDF2_ITERATIONS..CryptoManager.MAX_PBKDF2_ITERATIONS) {
                throw InvalidVaultFileException("Iteraciones fuera de rango")
            }
            return VaultEnvelope(format, salt, iterations, json.getString("check"), json.getString("data"))
        } catch (e: InvalidVaultFileException) {
            throw e
        } catch (e: Exception) {
            throw InvalidVaultFileException("Fichero no válido", e)
        }
    }

    fun encode(format: String, vault: VaultData, key: SecretKey, salt: ByteArray, iterations: Int): String =
        JSONObject().apply {
            put("format", format)
            put("version", VERSION)
            put("kdf", JSONObject().apply {
                put("name", CryptoManager.KDF_ALGORITHM)
                put("iterations", iterations)
                put("salt", CryptoManager.b64(salt))
            })
            put("check", CryptoManager.encrypt(CHECK_PLAINTEXT, key, "$format:check"))
            put("data", CryptoManager.encrypt(vault.serialize(), key, "$format:data"))
        }.toString(2)

    /** Deriva la clave probando las formas equivalentes de la contraseña. */
    suspend fun unlock(envelope: VaultEnvelope, password: String): SecretKey = withContext(Dispatchers.Default) {
        for (variant in CryptoManager.passwordVariants(password)) {
            val key = CryptoManager.deriveKey(variant, envelope.salt, envelope.iterations)
            if (envelope.verify(key)) return@withContext key
        }
        throw WrongPasswordException()
    }

    /** Clave nueva (salt aleatorio) para cifrar con [password]. */
    suspend fun newKey(password: String): Triple<SecretKey, ByteArray, Int> = withContext(Dispatchers.Default) {
        val salt = CryptoManager.generateSalt()
        val iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
        Triple(CryptoManager.deriveKey(CryptoManager.canonicalPassword(password), salt, iterations), salt, iterations)
    }

    /** Copia de seguridad cifrada con contraseña (solo las cuentas activas). */
    suspend fun exportBackup(vault: VaultData, password: String): String {
        val (key, salt, iterations) = newKey(password)
        return encode(FORMAT_BACKUP, VaultData(vault.active), key, salt, iterations)
    }
}
