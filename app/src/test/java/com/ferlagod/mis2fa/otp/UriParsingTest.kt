// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.otp

import com.ferlagod.mis2fa.model.OtpAlgorithm
import com.ferlagod.mis2fa.model.OtpType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64

class UriParsingTest {

    @Test
    fun base32RoundTripAndTolerance() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -128, 127)
        val encoded = Base32.encode(bytes)
        assertArrayEquals(bytes, Base32.decode(encoded))
        assertArrayEquals(Base32.decode("JBSWY3DPEHPK3PXP"), Base32.decode("jbsw y3dp-ehpk 3pxp===="))
        assertFalse(Base32.isValid("not base32!"))
        assertFalse(Base32.isValid(""))
    }

    @Test
    fun parsesStandardTotp() {
        val t = OtpAuthUri.parse("otpauth://totp/ACME%20Co:john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60")
        assertEquals("ACME Co", t.issuer)
        assertEquals("john.doe@email.com", t.account)
        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", t.secret)
        assertEquals(OtpType.TOTP, t.type)
        assertEquals(OtpAlgorithm.SHA256, t.algorithm)
        assertEquals(8, t.digits)
        assertEquals(60, t.period)
    }

    @Test
    fun issuerFromLabelAndDefaults() {
        val t = OtpAuthUri.parse("otpauth://totp/GitHub:octocat?secret=jbswy3dpehpk3pxp")
        assertEquals("GitHub", t.issuer)
        assertEquals("octocat", t.account)
        assertEquals("JBSWY3DPEHPK3PXP", t.secret)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)
        assertEquals(OtpAlgorithm.SHA1, t.algorithm)
    }

    @Test
    fun parsesHotpAndSteam() {
        val h = OtpAuthUri.parse("otpauth://hotp/Bank:me?secret=JBSWY3DPEHPK3PXP&counter=42")
        assertEquals(OtpType.HOTP, h.type)
        assertEquals(42, h.counter)
        val s = OtpAuthUri.parse("otpauth://totp/Steam:gamer?secret=JBSWY3DPEHPK3PXP&issuer=Steam&encoder=steam")
        assertEquals(OtpType.STEAM, s.type)
        assertEquals(5, s.digits)
        assertEquals(OtpType.STEAM, OtpAuthUri.parse("otpauth://steam/Steam:gamer?secret=JBSWY3DPEHPK3PXP").type)
    }

    @Test
    fun rejectsInvalid() {
        assertNull(OtpAuthUri.parseOrNull("https://example.com"))
        assertNull(OtpAuthUri.parseOrNull("otpauth://totp/x?issuer=a"))
        assertNull(OtpAuthUri.parseOrNull("otpauth://totp/x?secret=!!!"))
        assertNull(OtpAuthUri.parseOrNull("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=20"))
        assertNull(OtpAuthUri.parseOrNull("otpauth://yotp/x?secret=JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun buildRoundTrips() {
        for (uri in listOf(
            "otpauth://totp/Ex%C3%A1mple%20Inc:al%C3%ADce%40mail.com?secret=JBSWY3DPEHPK3PXP&issuer=Ex%C3%A1mple%20Inc&algorithm=SHA512&digits=8&period=45",
            "otpauth://hotp/Bank:me?secret=JBSWY3DPEHPK3PXP&counter=7",
            "otpauth://totp/Steam:gamer?secret=JBSWY3DPEHPK3PXP&encoder=steam",
            "otpauth://totp/solo?secret=JBSWY3DPEHPK3PXP"
        )) {
            val token = OtpAuthUri.parse(uri)
            val again = OtpAuthUri.parse(OtpAuthUri.build(token))
            assertEquals(token.copy(id = "", createdAt = 0, updatedAt = 0), again.copy(id = "", createdAt = 0, updatedAt = 0))
        }
    }

    // --- Google Authenticator ---

    private fun varint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            if (v and 0x7F.inv().toLong() == 0L) { out.write(v.toInt()); return }
            out.write(((v and 0x7F) or 0x80).toInt()); v = v ushr 7
        }
    }

    private fun field(out: ByteArrayOutputStream, number: Int, bytes: ByteArray) {
        varint(out, ((number shl 3) or 2).toLong()); varint(out, bytes.size.toLong()); out.write(bytes)
    }

    private fun intField(out: ByteArrayOutputStream, number: Int, value: Long) {
        varint(out, (number shl 3).toLong()); varint(out, value)
    }

    @Test
    fun parsesGoogleMigration() {
        val otp1 = ByteArrayOutputStream().apply {
            field(this, 1, "12345678901234567890".toByteArray())
            field(this, 2, "Example:alice@example.com".toByteArray())
            field(this, 3, "Example".toByteArray())
            intField(this, 4, 1); intField(this, 5, 1); intField(this, 6, 2)
        }.toByteArray()
        val otp2 = ByteArrayOutputStream().apply {
            field(this, 1, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
            field(this, 2, "bob".toByteArray())
            intField(this, 4, 2); intField(this, 5, 2); intField(this, 6, 1); intField(this, 7, 300)
        }.toByteArray()
        val payload = ByteArrayOutputStream().apply {
            field(this, 1, otp1); field(this, 1, otp2)
            intField(this, 2, 1); intField(this, 3, 2); intField(this, 4, 1); intField(this, 5, 12345)
        }.toByteArray()
        val uri = "otpauth-migration://offline?data=" + URLEncoder.encode(Base64.getEncoder().encodeToString(payload), "UTF-8")

        val batch = GoogleMigration.parse(uri)
        assertEquals(2, batch.size)
        assertEquals(1, batch.index)
        assertEquals(2, batch.tokens.size)
        val a = batch.tokens[0]
        assertEquals("Example", a.issuer)
        assertEquals("alice@example.com", a.account)
        assertArrayEquals("12345678901234567890".toByteArray(), Base32.decode(a.secret))
        assertEquals(OtpType.TOTP, a.type)
        assertEquals(6, a.digits)
        val b = batch.tokens[1]
        assertEquals("", b.issuer)
        assertEquals("bob", b.account)
        assertEquals(OtpType.HOTP, b.type)
        assertEquals(OtpAlgorithm.SHA256, b.algorithm)
        assertEquals(8, b.digits)
        assertEquals(300, b.counter)
        assertTrue(GoogleMigration.isMigration(uri))
    }
}
