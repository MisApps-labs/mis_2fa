// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.model

import com.ferlagod.mis2fa.otp.OtpGenerator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenJsonTest {

    private fun parse(vararg fields: Pair<String, Any>): Token =
        Token.fromJson(JSONObject(mapOf("id" to "x", "secret" to "JBSWY3DPEHPK3PXP") + fields.toMap()))

    @Test
    fun outOfRangeValuesAreCorrectedSoCodesCanBeGenerated() {
        val token = parse("period" to 0, "digits" to 12, "counter" to -5)
        assertEquals(Token.PERIOD_RANGE.first, token.period)
        assertEquals(Token.DIGITS_RANGE.last, token.digits)
        assertEquals(0L, token.counter)
        // Antes esto lanzaba ArithmeticException (periodo 0) y cerraba la app al abrir el inicio.
        OtpGenerator.code(token, System.currentTimeMillis())
        OtpGenerator.nextCode(token, System.currentTimeMillis())
    }

    @Test
    fun steamAlwaysUsesFiveCharactersAndSha1() {
        val token = parse("type" to "STEAM", "digits" to 8, "algorithm" to "SHA512")
        assertEquals(Token.STEAM_DIGITS, token.digits)
        assertEquals(OtpAlgorithm.SHA1, token.algorithm)
    }

    @Test
    fun secretIsNormalized() {
        assertEquals("JBSWY3DPEHPK3PXP", parse("secret" to "jbsw y3dp-ehpk 3pxp==").secret)
    }

    @Test
    fun roundTripKeepsValidValues() {
        val original = Token(id = "x", issuer = "A", account = "b", secret = "JBSWY3DPEHPK3PXP", digits = 8, period = 60, type = OtpType.TOTP)
        assertEquals(original, Token.fromJson(original.toJson()))
    }

    @Test
    fun iconSurvivesRoundTripAndOversizedIconIsDropped() {
        val token = parse("icon" to "iVBORw0KGgo=")
        assertEquals("iVBORw0KGgo=", Token.fromJson(token.toJson()).icon)
        assertEquals(null, parse().icon)
        assertEquals(null, parse("icon" to "A".repeat(Token.MAX_ICON_CHARS + 1)).icon)
    }
}
