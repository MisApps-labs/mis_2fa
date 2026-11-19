// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.security

import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado con contraseña (sincronización y copias de seguridad), igual que en Mis Notas.
 *
 * - Derivación de clave: PBKDF2WithHmacSHA256 (600.000 iteraciones, salt aleatorio, 256 bits).
 * - Cifrado autenticado: AES-256/GCM/NoPadding con IV aleatorio de 12 bytes por operación.
 * - Formato binario: [IV (12 bytes) | CipherText | AuthTag (16 bytes)]. En texto se codifica en Base64.
 * - AAD: vincula cada bloque cifrado a su uso, para que no se pueda reutilizar en otro sitio.
 */
object CryptoManager {

    private const val ALGORITHM_AES = "AES"
    private const val TRANSFORMATION_AES_GCM = "AES/GCM/NoPadding"
    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"

    /** Recomendación OWASP para PBKDF2-HMAC-SHA256: >= 600.000. */
    const val DEFAULT_PBKDF2_ITERATIONS = 600_000

    const val KEY_LENGTH_BITS = 256
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val DEFAULT_SALT_LENGTH_BYTES = 16

    /** Límites aceptados al leer parámetros de un fichero (evita bloqueos con valores absurdos). */
    const val MIN_PBKDF2_ITERATIONS = 100_000
    const val MAX_PBKDF2_ITERATIONS = 10_000_000
    const val MIN_SALT_LENGTH_BYTES = 16

    /** Longitud mínima de una contraseña nueva. */
    const val MIN_PASSWORD_LENGTH = 8

    private val secureRandom = SecureRandom()

    /**
     * Formas equivalentes de una misma contraseña, de más a menos probable.
     *
     * Distintos teclados escriben "ñ" o "á" como un solo carácter (NFC) o como letra + tilde (NFD),
     * y el autocompletado a veces añade un espacio final. Al desbloquear se prueba cada variante
     * contra el verificador cifrado, así que una contraseña correcta nunca deja al usuario fuera.
     */
    fun passwordVariants(password: String): List<String> {
        val nfc = Normalizer.normalize(password, Normalizer.Form.NFC)
        val nfd = Normalizer.normalize(password, Normalizer.Form.NFD)
        return listOf(nfc, password, nfd, nfc.trim(), password.trim(), nfd.trim())
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /** Forma canónica con la que se cifra todo lo nuevo. */
    fun canonicalPassword(password: String): String = Normalizer.normalize(password, Normalizer.Form.NFC)

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    fun generateSalt(): ByteArray = randomBytes(DEFAULT_SALT_LENGTH_BYTES)

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        require(password.isNotEmpty()) { "La contraseña no puede estar vacía" }
        require(salt.size >= MIN_SALT_LENGTH_BYTES) { "Salt demasiado corto" }
        require(iterations in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) { "Número de iteraciones fuera de rango" }

        val keySpec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        try {
            val keyBytes = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(keySpec).encoded
            return SecretKeySpec(keyBytes, ALGORITHM_AES)
        } finally {
            keySpec.clearPassword()
        }
    }

    fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKey =
        deriveKey(password.toCharArray(), salt, iterations)

    /** Cifra bytes y devuelve [IV | CipherText + Tag]. */
    fun encryptBytes(plain: ByteArray, key: SecretKey, aad: String): ByteArray {
        val iv = randomBytes(GCM_IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return iv + cipher.doFinal(plain)
    }

    /** Descifra [IV | CipherText + Tag]. Lanza excepción si la clave o el AAD no coinciden. */
    fun decryptBytes(combined: ByteArray, key: SecretKey, aad: String): ByteArray {
        require(combined.size >= GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8) { "Datos cifrados demasiado cortos" }
        val cipher = Cipher.getInstance(TRANSFORMATION_AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE, key,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, combined, 0, GCM_IV_LENGTH_BYTES)
        )
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(combined, GCM_IV_LENGTH_BYTES, combined.size - GCM_IV_LENGTH_BYTES)
    }

    fun encrypt(plainText: String, key: SecretKey, aad: String): String =
        b64(encryptBytes(plainText.toByteArray(Charsets.UTF_8), key, aad))

    fun decrypt(cipherText: String, key: SecretKey, aad: String): String =
        String(decryptBytes(unb64(cipherText), key, aad), Charsets.UTF_8)

    /** true si [cipherText] se descifra con [key] y su contenido es [expected]. */
    fun verify(cipherText: String, expected: String, key: SecretKey, aad: String): Boolean = try {
        decrypt(cipherText, key, aad) == expected
    } catch (_: Exception) {
        false
    }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text.trim())

    fun keyFromBytes(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, ALGORITHM_AES)
}
