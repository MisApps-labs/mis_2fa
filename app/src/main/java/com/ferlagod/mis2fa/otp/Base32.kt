// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.otp

/**
 * Base32 (RFC 4648), el formato en que los servicios entregan la clave secreta.
 *
 * La decodificación es tolerante con lo que suele escribir un humano: minúsculas, espacios,
 * guiones y relleno "=" se ignoran.
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Quita espacios, guiones y relleno, y pasa a mayúsculas. */
    fun normalize(input: String): String =
        input.filterNot { it.isWhitespace() || it == '-' || it == '=' }.uppercase()

    fun isValid(input: String): Boolean {
        val n = normalize(input)
        return n.isNotEmpty() && n.all { it in ALPHABET } && decodeOrNull(n)?.isNotEmpty() == true
    }

    fun decodeOrNull(input: String): ByteArray? = try {
        decode(input)
    } catch (_: IllegalArgumentException) {
        null
    }

    fun decode(input: String): ByteArray {
        val clean = normalize(input)
        require(clean.isNotEmpty()) { "Clave vacía" }
        val out = java.io.ByteArrayOutputStream(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            require(value >= 0) { "Carácter no válido en Base32: $c" }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer shr bits) and 0x1F])
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }
}
