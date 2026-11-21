// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.otp

import com.ferlagod.mis2fa.model.OtpAlgorithm
import com.ferlagod.mis2fa.model.OtpType
import com.ferlagod.mis2fa.model.Token
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generación de códigos HOTP (RFC 4226), TOTP (RFC 6238) y Steam Guard.
 */
object OtpGenerator {

    private const val STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"
    private val POWERS = LongArray(11).also { p -> p[0] = 1; for (i in 1..10) p[i] = p[i - 1] * 10 }

    /** Truncado dinámico de RFC 4226: devuelve un entero de 31 bits. */
    private fun truncatedHash(key: ByteArray, counter: Long, algorithm: OtpAlgorithm): Int {
        val mac = Mac.getInstance(algorithm.jcaName)
        // Una clave vacía no es válida para SecretKeySpec; nunca debería llegar aquí.
        mac.init(SecretKeySpec(if (key.isEmpty()) ByteArray(1) else key, "RAW"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash[hash.size - 1].toInt() and 0x0F
        return ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
    }

    fun hotp(key: ByteArray, counter: Long, digits: Int, algorithm: OtpAlgorithm = OtpAlgorithm.SHA1): String {
        require(digits in Token.DIGITS_RANGE) { "Dígitos fuera de rango" }
        val value = truncatedHash(key, counter, algorithm).toLong() % POWERS[digits]
        return value.toString().padStart(digits, '0')
    }

    fun steam(key: ByteArray, counter: Long): String {
        var value = truncatedHash(key, counter, OtpAlgorithm.SHA1)
        val sb = StringBuilder(Token.STEAM_DIGITS)
        repeat(Token.STEAM_DIGITS) {
            sb.append(STEAM_ALPHABET[value % STEAM_ALPHABET.length])
            value /= STEAM_ALPHABET.length
        }
        return sb.toString()
    }

    /** Paso de tiempo TOTP para [timeMillis]. */
    fun timeStep(timeMillis: Long, period: Int): Long = Math.floorDiv(timeMillis / 1000, period.toLong())

    /** Milisegundos que le quedan al código actual. */
    fun millisRemaining(timeMillis: Long, period: Int): Long {
        val periodMs = period * 1000L
        return periodMs - Math.floorMod(timeMillis, periodMs)
    }

    /** Código de [token] en [timeMillis] (para HOTP se usa su contador actual). */
    fun code(token: Token, timeMillis: Long, key: ByteArray = Base32.decode(token.secret)): String =
        codeForStep(token, if (token.type == OtpType.HOTP) token.counter else timeStep(timeMillis, token.period), key)

    /** Código del siguiente periodo (solo tiene sentido para TOTP y Steam). */
    fun nextCode(token: Token, timeMillis: Long, key: ByteArray = Base32.decode(token.secret)): String =
        codeForStep(token, timeStep(timeMillis, token.period) + 1, key)

    private fun codeForStep(token: Token, step: Long, key: ByteArray): String = when (token.type) {
        OtpType.STEAM -> steam(key, step)
        else -> hotp(key, step, token.digits, token.algorithm)
    }

    /** "123456" → "123 456"; "12345678" → "1234 5678"; "1234567" → "123 4567". */
    fun format(code: String): String = when {
        code.length < 6 -> code
        else -> {
            val half = code.length / 2
            code.substring(0, half) + " " + code.substring(half)
        }
    }
}
