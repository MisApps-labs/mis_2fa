// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.otp

import com.ferlagod.mis2fa.model.OtpAlgorithm
import com.ferlagod.mis2fa.model.OtpType
import com.ferlagod.mis2fa.model.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpGeneratorTest {

    private val sha1Key = "12345678901234567890".toByteArray()
    private val sha256Key = "12345678901234567890123456789012".toByteArray()
    private val sha512Key = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    @Test
    fun hotpMatchesRfc4226Vectors() {
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { counter, code ->
            assertEquals(code, OtpGenerator.hotp(sha1Key, counter.toLong(), 6))
        }
    }

    @Test
    fun totpMatchesRfc6238Vectors() {
        val vectors = listOf(
            59L to listOf("94287082", "46119246", "90693936"),
            1111111109L to listOf("07081804", "68084774", "25091201"),
            1111111111L to listOf("14050471", "67062674", "99943326"),
            1234567890L to listOf("89005924", "91819424", "93441116"),
            2000000000L to listOf("69279037", "90698825", "38618901"),
            20000000000L to listOf("65353130", "77737706", "47863826")
        )
        for ((seconds, codes) in vectors) {
            val time = seconds * 1000
            val step = OtpGenerator.timeStep(time, 30)
            assertEquals(codes[0], OtpGenerator.hotp(sha1Key, step, 8, OtpAlgorithm.SHA1))
            assertEquals(codes[1], OtpGenerator.hotp(sha256Key, step, 8, OtpAlgorithm.SHA256))
            assertEquals(codes[2], OtpGenerator.hotp(sha512Key, step, 8, OtpAlgorithm.SHA512))
        }
    }

    @Test
    fun tokenCodeUsesBase32Secret() {
        val token = Token(secret = Base32.encode(sha1Key), digits = 8)
        assertEquals("94287082", OtpGenerator.code(token, 59_000))
        assertEquals(OtpGenerator.code(token, 89_000), OtpGenerator.nextCode(token, 59_000))
    }

    @Test
    fun hotpTokenUsesCounter() {
        val token = Token(secret = Base32.encode(sha1Key), type = OtpType.HOTP, counter = 3)
        assertEquals("969429", OtpGenerator.code(token, 123_456_789))
    }

    @Test
    fun steamCodesHaveFiveCharsFromAlphabet() {
        val token = Token(secret = "JBSWY3DPEHPK3PXP", type = OtpType.STEAM, digits = 5)
        val code = OtpGenerator.code(token, 1_700_000_000_000)
        assertEquals(5, code.length)
        assertTrue(code.all { it in "23456789BCDFGHJKMNPQRTVWXY" })
    }

    @Test
    fun remainingTime() {
        assertEquals(30_000, OtpGenerator.millisRemaining(60_000, 30))
        assertEquals(1_000, OtpGenerator.millisRemaining(59_000, 30))
    }

    @Test
    fun formatGroupsDigits() {
        assertEquals("123 456", OtpGenerator.format("123456"))
        assertEquals("1234 5678", OtpGenerator.format("12345678"))
        assertEquals("ABCDE", OtpGenerator.format("ABCDE"))
    }
}
