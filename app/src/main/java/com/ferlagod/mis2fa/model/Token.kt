// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.model

import com.ferlagod.mis2fa.otp.Base32
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Tipo de código. */
enum class OtpType {
    /** Basado en el tiempo (RFC 6238): el caso habitual. */
    TOTP,
    /** Basado en un contador (RFC 4226): cambia cada vez que se pide uno nuevo. */
    HOTP,
    /** Steam Guard: TOTP de 30 s con 5 caracteres alfanuméricos. */
    STEAM;

    companion object {
        fun fromName(name: String?): OtpType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TOTP
    }
}

/** Algoritmo HMAC. */
enum class OtpAlgorithm(val jcaName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512");

    companion object {
        fun fromName(name: String?): OtpAlgorithm {
            val n = name?.uppercase()?.replace("-", "")?.removePrefix("HMAC") ?: return SHA1
            return entries.firstOrNull { it.name == n } ?: SHA1
        }
    }
}

/**
 * Una cuenta con verificación en dos pasos.
 *
 * [secret] se guarda en Base32 normalizado (mayúsculas, sin espacios ni relleno).
 * [updatedAt] decide qué versión gana al sincronizar; [trashedAt] marca la papelera.
 */
data class Token(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String = "",
    val account: String = "",
    val secret: String,
    val type: OtpType = OtpType.TOTP,
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = DEFAULT_DIGITS,
    val period: Int = DEFAULT_PERIOD,
    val counter: Long = 0L,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val trashedAt: Long? = null
) {
    val isTrashed: Boolean get() = trashedAt != null

    /** Nombre principal que se muestra: el emisor o, si falta, la cuenta. */
    val displayName: String get() = issuer.ifBlank { account }.ifBlank { "?" }

    /** Clave para detectar duplicados al importar (mismo secreto y misma cuenta). */
    val identityKey: String get() = "${secret.uppercase()}|${issuer.trim().lowercase()}|${account.trim().lowercase()}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("issuer", issuer)
        put("account", account)
        put("secret", secret)
        put("type", type.name)
        put("algorithm", algorithm.name)
        put("digits", digits)
        put("period", period)
        put("counter", counter)
        put("pinned", pinned)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        if (trashedAt != null) put("trashedAt", trashedAt)
    }

    companion object {
        const val DEFAULT_DIGITS = 6
        const val DEFAULT_PERIOD = 30
        const val STEAM_DIGITS = 5
        val DIGITS_RANGE = 4..10
        val PERIOD_RANGE = 5..300

        /**
         * Lee una cuenta guardada. Los valores fuera de rango se corrigen en lugar de aceptarse:
         * un periodo 0 o 12 cifras harían fallar la generación del código cada vez que se dibuja
         * la pantalla de inicio, y la app no se podría ni abrir. Un secreto no válido se conserva
         * tal cual (no se pierde) y la interfaz lo muestra como erróneo.
         */
        fun fromJson(json: JSONObject): Token {
            val type = OtpType.fromName(json.optString("type"))
            return Token(
                id = json.getString("id"),
                issuer = json.optString("issuer"),
                account = json.optString("account"),
                secret = Base32.normalize(json.getString("secret")),
                type = type,
                algorithm = if (type == OtpType.STEAM) OtpAlgorithm.SHA1 else OtpAlgorithm.fromName(json.optString("algorithm")),
                digits = if (type == OtpType.STEAM) STEAM_DIGITS
                else json.optInt("digits", DEFAULT_DIGITS).coerceIn(DIGITS_RANGE),
                period = json.optInt("period", DEFAULT_PERIOD).coerceIn(PERIOD_RANGE),
                counter = json.optLong("counter", 0L).coerceAtLeast(0L),
                pinned = json.optBoolean("pinned", false),
                createdAt = json.optLong("createdAt", 0L),
                updatedAt = json.optLong("updatedAt", 0L),
                trashedAt = if (json.has("trashedAt") && !json.isNull("trashedAt")) json.getLong("trashedAt") else null
            )
        }
    }
}

/**
 * Contenido completo de la bóveda: las cuentas y las "lápidas" de las borradas definitivamente
 * (id → momento del borrado), que hacen falta para que el borrado llegue al resto de dispositivos.
 */
data class VaultData(
    val tokens: List<Token> = emptyList(),
    val tombstones: Map<String, Long> = emptyMap()
) {
    val active: List<Token> get() = tokens.filter { !it.isTrashed }
    val trashed: List<Token> get() = tokens.filter { it.isTrashed }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("tokens", JSONArray().apply { tokens.forEach { put(it.toJson()) } })
        put("tombstones", JSONObject().apply { tombstones.forEach { (id, at) -> put(id, at) } })
    }

    fun serialize(): String = toJson().toString()

    companion object {
        fun parse(raw: String): VaultData {
            val json = JSONObject(raw)
            val array = json.optJSONArray("tokens") ?: JSONArray()
            val tokens = (0 until array.length()).map { Token.fromJson(array.getJSONObject(it)) }
            val tomb = json.optJSONObject("tombstones")
            val tombstones = buildMap {
                tomb?.keys()?.forEach { key -> put(key, tomb.getLong(key)) }
            }
            return VaultData(tokens.distinctBy { it.id }, tombstones)
        }
    }
}
