// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.otp

import com.ferlagod.mis2fa.model.OtpAlgorithm
import com.ferlagod.mis2fa.model.OtpType
import com.ferlagod.mis2fa.model.Token
import java.net.URLDecoder
import java.net.URLEncoder

/** El texto no es un enlace de verificación en dos pasos válido. */
class InvalidOtpUriException(message: String) : IllegalArgumentException(message)

/**
 * Enlaces `otpauth://` (formato de Google Authenticator, el que llevan los códigos QR):
 *
 * `otpauth://totp/Emisor:cuenta?secret=JBSWY3DPEHPK3PXP&issuer=Emisor&algorithm=SHA1&digits=6&period=30`
 *
 * También acepta `otpauth://hotp/…&counter=N` y Steam (`otpauth://steam/…` o `encoder=steam`).
 */
object OtpAuthUri {

    const val SCHEME = "otpauth://"

    fun isOtpAuth(text: String) = text.trim().startsWith(SCHEME, ignoreCase = true)

    fun parse(raw: String): Token {
        val text = raw.trim()
        if (!isOtpAuth(text)) throw InvalidOtpUriException("No es un enlace otpauth://")
        val rest = text.substring(SCHEME.length)
        val slash = rest.indexOf('/')
        if (slash <= 0) throw InvalidOtpUriException("Falta el tipo")
        val typeName = rest.substring(0, slash).lowercase()
        val afterType = rest.substring(slash + 1)
        val qIndex = afterType.indexOf('?')
        val rawLabel = if (qIndex >= 0) afterType.substring(0, qIndex) else afterType
        val params = if (qIndex >= 0) parseQuery(afterType.substring(qIndex + 1)) else emptyMap()

        val label = decode(rawLabel).trim()
        val labelIssuer: String
        val account: String
        val colon = label.indexOf(':')
        if (colon >= 0) {
            labelIssuer = label.substring(0, colon).trim()
            account = label.substring(colon + 1).trim()
        } else {
            labelIssuer = ""
            account = label
        }
        val issuer = params["issuer"]?.trim()?.takeIf { it.isNotEmpty() } ?: labelIssuer

        val secretParam = params["secret"] ?: throw InvalidOtpUriException("Falta la clave secreta")
        val secret = Base32.normalize(secretParam)
        if (!Base32.isValid(secret)) throw InvalidOtpUriException("La clave secreta no es Base32 válido")

        val isSteam = typeName == "steam" || params["encoder"].equals("steam", ignoreCase = true)
        val type = when {
            isSteam -> OtpType.STEAM
            typeName == "totp" -> OtpType.TOTP
            typeName == "hotp" -> OtpType.HOTP
            else -> throw InvalidOtpUriException("Tipo no soportado: $typeName")
        }

        val digits = if (type == OtpType.STEAM) Token.STEAM_DIGITS
        else params["digits"]?.toIntOrNull() ?: Token.DEFAULT_DIGITS
        if (type != OtpType.STEAM && digits !in Token.DIGITS_RANGE) throw InvalidOtpUriException("Número de dígitos no válido")

        val period = params["period"]?.toIntOrNull() ?: Token.DEFAULT_PERIOD
        if (period !in Token.PERIOD_RANGE) throw InvalidOtpUriException("Periodo no válido")

        // El contador es obligatorio en HOTP según la especificación, pero muchos servicios lo omiten.
        val counter = params["counter"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

        return Token(
            issuer = issuer,
            account = account,
            secret = secret,
            type = type,
            algorithm = if (type == OtpType.STEAM) OtpAlgorithm.SHA1 else OtpAlgorithm.fromName(params["algorithm"]),
            digits = digits,
            period = if (type == OtpType.HOTP) Token.DEFAULT_PERIOD else period,
            counter = counter
        )
    }

    fun parseOrNull(raw: String): Token? = try {
        parse(raw)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Construye el enlace de [token] (para mostrarlo como QR o exportarlo). */
    fun build(token: Token): String {
        val typeName = when (token.type) {
            OtpType.TOTP -> "totp"
            OtpType.HOTP -> "hotp"
            OtpType.STEAM -> "totp"
        }
        val label = buildString {
            if (token.issuer.isNotBlank()) append(encode(token.issuer)).append(':')
            append(encode(token.account))
        }
        val query = buildList {
            add("secret=${token.secret}")
            if (token.issuer.isNotBlank()) add("issuer=${encode(token.issuer)}")
            add("algorithm=${token.algorithm.name}")
            add("digits=${token.digits}")
            when (token.type) {
                OtpType.HOTP -> add("counter=${token.counter}")
                OtpType.TOTP -> add("period=${token.period}")
                OtpType.STEAM -> {
                    add("period=${token.period}")
                    add("encoder=steam")
                }
            }
        }.joinToString("&")
        return "$SCHEME$typeName/$label?$query"
    }

    private fun parseQuery(query: String): Map<String, String> = query.split('&')
        .mapNotNull { part ->
            if (part.isEmpty()) return@mapNotNull null
            val eq = part.indexOf('=')
            if (eq < 0) part.lowercase() to "" else part.substring(0, eq).lowercase() to decode(part.substring(eq + 1))
        }
        .toMap()

    private fun decode(value: String): String = try {
        // "+" se respeta literalmente: en las etiquetas es más habitual que un espacio mal codificado.
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (_: IllegalArgumentException) {
        value
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
