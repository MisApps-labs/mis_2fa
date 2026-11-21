// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.data.backup

import com.ferlagod.mis2fa.data.sync.VaultCodec
import com.ferlagod.mis2fa.data.sync.VaultEnvelope
import com.ferlagod.mis2fa.model.OtpAlgorithm
import com.ferlagod.mis2fa.model.OtpType
import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.otp.Base32
import com.ferlagod.mis2fa.otp.GoogleMigration
import com.ferlagod.mis2fa.otp.OtpAuthUri
import org.json.JSONArray
import org.json.JSONObject

/** Qué se ha encontrado en un fichero a importar. */
sealed interface ImportContent {
    /** Cuentas listas para añadir; [skipped] son entradas que no se han podido leer. */
    data class Tokens(val tokens: List<Token>, val skipped: Int) : ImportContent

    /** Copia de Mis 2FA cifrada: hace falta su contraseña. */
    data class Encrypted(val envelope: VaultEnvelope) : ImportContent

    /** Formato reconocido pero no soportado (p. ej. exportación cifrada de otra app). */
    data class Unsupported(val reason: Reason) : ImportContent {
        enum class Reason { AEGIS_ENCRYPTED, TWOFAS_ENCRYPTED, UNKNOWN }
    }
}

/**
 * Lee las exportaciones más habituales:
 * - Copia de seguridad de Mis 2FA (cifrada).
 * - Texto con un enlace `otpauth://` o `otpauth-migration://` por línea
 *   (Ente Auth "texto sin cifrar", Bitwarden, Proton Pass, Google Authenticator…).
 * - Aegis (JSON sin cifrar).
 * - 2FAS (`.2fas` sin cifrar).
 */
object ImportParser {

    /** Marca de orden de bytes que algunos editores añaden al principio de los ficheros de texto. */
    private const val BYTE_ORDER_MARK = 0xFEFF.toChar()

    fun parse(text: String): ImportContent {
        val trimmed = text.trim().removePrefix(BYTE_ORDER_MARK.toString())
        if (trimmed.startsWith("{")) {
            if (VaultCodec.looksLikeEnvelope(trimmed)) return ImportContent.Encrypted(VaultCodec.parse(trimmed))
            val json = try {
                JSONObject(trimmed)
            } catch (_: Exception) {
                return ImportContent.Unsupported(ImportContent.Unsupported.Reason.UNKNOWN)
            }
            parseAegis(json)?.let { return it }
            parseTwoFas(json)?.let { return it }
            return ImportContent.Unsupported(ImportContent.Unsupported.Reason.UNKNOWN)
        }
        return parseUriList(trimmed)
    }

    /** Un enlace por línea (las líneas vacías o que no son enlaces se ignoran). */
    fun parseUriList(text: String): ImportContent {
        val tokens = mutableListOf<Token>()
        var skipped = 0
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                OtpAuthUri.isOtpAuth(line) -> OtpAuthUri.parseOrNull(line)?.let(tokens::add) ?: skipped++
                GoogleMigration.isMigration(line) -> try {
                    tokens += GoogleMigration.parse(line).tokens
                } catch (_: IllegalArgumentException) {
                    skipped++
                }
            }
        }
        if (tokens.isEmpty() && skipped == 0) return ImportContent.Unsupported(ImportContent.Unsupported.Reason.UNKNOWN)
        return ImportContent.Tokens(tokens, skipped)
    }

    private fun parseAegis(json: JSONObject): ImportContent? {
        if (!json.has("db") || !json.has("header")) return null
        val db = json.opt("db")
        if (db is String) return ImportContent.Unsupported(ImportContent.Unsupported.Reason.AEGIS_ENCRYPTED)
        val entries = (db as? JSONObject)?.optJSONArray("entries") ?: return null
        return collect(entries) { entry ->
            val info = entry.getJSONObject("info")
            val type = when (entry.optString("type").lowercase()) {
                "totp" -> OtpType.TOTP
                "hotp" -> OtpType.HOTP
                "steam" -> OtpType.STEAM
                else -> return@collect null
            }
            token(
                issuer = entry.optString("issuer"),
                account = entry.optString("name"),
                secret = info.getString("secret"),
                type = type,
                algorithm = info.optString("algo"),
                digits = info.optInt("digits", Token.DEFAULT_DIGITS),
                period = info.optInt("period", Token.DEFAULT_PERIOD),
                counter = info.optLong("counter", 0L)
            )
        }
    }

    private fun parseTwoFas(json: JSONObject): ImportContent? {
        if (!json.has("services") && !json.has("servicesEncrypted")) return null
        val services = json.optJSONArray("services")
        if ((services == null || services.length() == 0) && json.optString("servicesEncrypted").isNotBlank()) {
            return ImportContent.Unsupported(ImportContent.Unsupported.Reason.TWOFAS_ENCRYPTED)
        }
        return collect(services ?: JSONArray()) { service ->
            val otp = service.optJSONObject("otp") ?: JSONObject()
            val type = when (otp.optString("tokenType").uppercase()) {
                "HOTP" -> OtpType.HOTP
                "STEAM" -> OtpType.STEAM
                else -> OtpType.TOTP
            }
            token(
                issuer = otp.optString("issuer").ifBlank { service.optString("name") },
                account = otp.optString("account"),
                secret = service.getString("secret"),
                type = type,
                algorithm = otp.optString("algorithm"),
                digits = otp.optInt("digits", Token.DEFAULT_DIGITS),
                period = otp.optInt("period", Token.DEFAULT_PERIOD),
                counter = otp.optLong("counter", 0L)
            )
        }
    }

    private fun collect(array: JSONArray, map: (JSONObject) -> Token?): ImportContent.Tokens {
        val tokens = mutableListOf<Token>()
        var skipped = 0
        for (i in 0 until array.length()) {
            val token = try {
                map(array.getJSONObject(i))
            } catch (_: Exception) {
                null
            }
            if (token != null) tokens += token else skipped++
        }
        return ImportContent.Tokens(tokens, skipped)
    }

    private fun token(
        issuer: String,
        account: String,
        secret: String,
        type: OtpType,
        algorithm: String?,
        digits: Int,
        period: Int,
        counter: Long
    ): Token? {
        val clean = Base32.normalize(secret)
        if (!Base32.isValid(clean)) return null
        return Token(
            issuer = issuer.trim(),
            account = account.trim(),
            secret = clean,
            type = type,
            algorithm = if (type == OtpType.STEAM) OtpAlgorithm.SHA1 else OtpAlgorithm.fromName(algorithm),
            digits = if (type == OtpType.STEAM) Token.STEAM_DIGITS else digits.coerceIn(Token.DIGITS_RANGE),
            period = period.coerceIn(Token.PERIOD_RANGE),
            counter = counter.coerceAtLeast(0L)
        )
    }

    /** Exportación sin cifrar: un enlace `otpauth://` por línea (compatible con casi cualquier app). */
    fun exportPlain(tokens: List<Token>): String = tokens.joinToString("\n", postfix = "\n") { OtpAuthUri.build(it) }
}
