// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.otp

import com.ferlagod.mis2fa.model.OtpAlgorithm
import com.ferlagod.mis2fa.model.OtpType
import com.ferlagod.mis2fa.model.Token
import java.net.URLDecoder
import java.util.Base64

/**
 * Códigos QR de "Transferir cuentas" de Google Authenticator:
 * `otpauth-migration://offline?data=<protobuf en Base64>`.
 *
 * El mensaje protobuf es pequeño y estable, así que se lee a mano (sin dependencias):
 *
 * ```
 * MigrationPayload { repeated OtpParameters otp_parameters = 1; int32 version = 2;
 *                    int32 batch_size = 3; int32 batch_index = 4; int32 batch_id = 5; }
 * OtpParameters    { bytes secret = 1; string name = 2; string issuer = 3;
 *                    Algorithm algorithm = 4; DigitCount digits = 5; OtpType type = 6; int64 counter = 7; }
 * ```
 */
object GoogleMigration {

    const val SCHEME = "otpauth-migration://"

    fun isMigration(text: String) = text.trim().startsWith(SCHEME, ignoreCase = true)

    /**
     * Resultado: las cuentas y, si la exportación tiene varios QR, cuál es este (1 de N).
     * [id] identifica la exportación: los QR de dos exportaciones distintas no se mezclan.
     */
    data class Batch(val tokens: List<Token>, val index: Int, val size: Int, val id: Long = 0)

    fun parse(uri: String): Batch {
        val text = uri.trim()
        if (!isMigration(text)) throw InvalidOtpUriException("No es un enlace otpauth-migration://")
        val query = text.substringAfter('?', "")
        val dataParam = query.split('&').firstOrNull { it.startsWith("data=") }?.substringAfter("data=")
            ?: throw InvalidOtpUriException("Falta el parámetro data")
        val base64 = URLDecoder.decode(dataParam, "UTF-8").replace(' ', '+')
        val bytes = try {
            Base64.getDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            throw InvalidOtpUriException("Datos de migración no válidos")
        }
        return parsePayload(bytes)
    }

    internal fun parsePayload(bytes: ByteArray): Batch {
        val reader = ProtoReader(bytes)
        val tokens = mutableListOf<Token>()
        var batchSize = 1
        var batchIndex = 0
        var batchId = 0L
        while (reader.hasMore()) {
            val (field, wire) = reader.readTag()
            when {
                field == 1 && wire == 2 -> parseOtp(reader.readBytes())?.let(tokens::add)
                field == 3 && wire == 0 -> batchSize = reader.readVarint().toInt()
                field == 4 && wire == 0 -> batchIndex = reader.readVarint().toInt()
                field == 5 && wire == 0 -> batchId = reader.readVarint()
                else -> reader.skip(wire)
            }
        }
        return Batch(tokens, batchIndex, batchSize.coerceAtLeast(1), batchId)
    }

    private fun parseOtp(bytes: ByteArray): Token? {
        val reader = ProtoReader(bytes)
        var secret = ByteArray(0)
        var name = ""
        var issuer = ""
        var algorithm = 0
        var digits = 0
        var type = 0
        var counter = 0L
        while (reader.hasMore()) {
            val (field, wire) = reader.readTag()
            when {
                field == 1 && wire == 2 -> secret = reader.readBytes()
                field == 2 && wire == 2 -> name = reader.readBytes().toString(Charsets.UTF_8)
                field == 3 && wire == 2 -> issuer = reader.readBytes().toString(Charsets.UTF_8)
                field == 4 && wire == 0 -> algorithm = reader.readVarint().toInt()
                field == 5 && wire == 0 -> digits = reader.readVarint().toInt()
                field == 6 && wire == 0 -> type = reader.readVarint().toInt()
                field == 7 && wire == 0 -> counter = reader.readVarint()
                else -> reader.skip(wire)
            }
        }
        if (secret.isEmpty()) return null
        // El nombre suele venir como "Emisor:cuenta".
        var account = name
        if (issuer.isNotBlank() && name.startsWith("$issuer:")) account = name.removePrefix("$issuer:")
        else if (issuer.isBlank() && name.contains(':')) {
            issuer = name.substringBefore(':')
            account = name.substringAfter(':')
        }
        return Token(
            issuer = issuer.trim(),
            account = account.trim(),
            secret = Base32.encode(secret),
            type = if (type == 1) OtpType.HOTP else OtpType.TOTP,
            algorithm = when (algorithm) {
                2 -> OtpAlgorithm.SHA256
                3 -> OtpAlgorithm.SHA512
                else -> OtpAlgorithm.SHA1
            },
            digits = if (digits == 2) 8 else 6,
            counter = counter
        )
    }

    /** Lector mínimo del formato binario de protobuf. */
    private class ProtoReader(private val data: ByteArray) {
        private var pos = 0

        fun hasMore() = pos < data.size

        fun readTag(): Pair<Int, Int> {
            val tag = readVarint().toInt()
            return (tag ushr 3) to (tag and 0x07)
        }

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                if (pos >= data.size) throw InvalidOtpUriException("Datos de migración truncados")
                val b = data[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            throw InvalidOtpUriException("Varint no válido")
        }

        fun readBytes(): ByteArray {
            val len = readVarint().toInt()
            if (len < 0 || pos + len > data.size) throw InvalidOtpUriException("Datos de migración truncados")
            return data.copyOfRange(pos, pos + len).also { pos += len }
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> readVarint()
                1 -> pos += 8
                2 -> readBytes()
                5 -> pos += 4
                else -> throw InvalidOtpUriException("Tipo protobuf no soportado")
            }
            if (pos > data.size) throw InvalidOtpUriException("Datos de migración truncados")
        }
    }
}
