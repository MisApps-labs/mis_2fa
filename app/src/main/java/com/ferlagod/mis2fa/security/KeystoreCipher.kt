// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.security

import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado de los datos del dispositivo con una clave AES-256 del Android Keystore.
 *
 * La clave se genera dentro del hardware seguro (StrongBox si el móvil lo tiene, si no el TEE)
 * y **nunca sale de él**: ni la app puede leerla, así que copiar los ficheros de la app a otro
 * dispositivo no sirve para descifrarlos.
 */
class KeystoreCipher(private val context: Context, private val alias: String) {

    private companion object {
        const val TAG = "KeystoreCipher"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128

        /**
         * Compartido por todas las instancias: la bóveda y los ajustes de sincronización usan el
         * mismo alias con instancias distintas. Con un bloqueo por instancia, dos primeras
         * escrituras simultáneas podrían generar la clave dos veces; la segunda sustituiría a la
         * primera y lo cifrado con ella quedaría ilegible.
         */
        val KEY_LOCK = Any()
    }

    private fun key(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey) ?: generate(strongBox = hasStrongBox())
    }

    private fun hasStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private fun generate(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        return try {
            generator.init(builder.build())
            generator.generateKey()
        } catch (e: Exception) {
            // Algunos StrongBox no admiten AES-256 o fallan al generar: se usa el TEE.
            if (!strongBox) throw e
            Log.w(TAG, "StrongBox no disponible; se usa el Keystore normal", e)
            generate(strongBox = false)
        }
    }

    /** Devuelve [IV | CipherText + Tag]. */
    fun encrypt(plain: ByteArray, aad: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val body = cipher.doFinal(plain)
        val iv = cipher.iv
        check(iv.size == IV_LENGTH) { "IV inesperado" }
        return iv + body
    }

    fun decrypt(combined: ByteArray, aad: String): ByteArray {
        require(combined.size > IV_LENGTH) { "Datos cifrados demasiado cortos" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, combined, 0, IV_LENGTH))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(combined, IV_LENGTH, combined.size - IV_LENGTH)
    }
}
