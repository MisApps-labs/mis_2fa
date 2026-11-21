// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.data

import com.ferlagod.mis2fa.data.backup.ImportContent
import com.ferlagod.mis2fa.data.backup.ImportParser
import com.ferlagod.mis2fa.data.sync.SyncMerger
import com.ferlagod.mis2fa.data.sync.VaultCodec
import com.ferlagod.mis2fa.data.sync.WrongPasswordException
import com.ferlagod.mis2fa.model.OtpAlgorithm
import com.ferlagod.mis2fa.model.OtpType
import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.model.VaultData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncAndBackupTest {

    private fun token(id: String, updatedAt: Long, issuer: String = id, counter: Long = 0, trashedAt: Long? = null) =
        Token(id = id, issuer = issuer, secret = "JBSWY3DPEHPK3PXP", createdAt = 1, updatedAt = updatedAt, counter = counter, trashedAt = trashedAt)

    @Test
    fun mergeIsUnionAndNewestWins() {
        val local = VaultData(listOf(token("a", 10, "A-local"), token("b", 5)))
        val remote = VaultData(listOf(token("a", 20, "A-remote"), token("c", 7)))
        val merged = SyncMerger.merge(local, remote)
        assertEquals(setOf("a", "b", "c"), merged.tokens.map { it.id }.toSet())
        assertEquals("A-remote", merged.tokens.first { it.id == "a" }.issuer)
        assertTrue(SyncMerger.sameContent(merged, SyncMerger.merge(remote, local)))
    }

    @Test
    fun missingOnServerNeverDeletes() {
        val local = VaultData(listOf(token("a", 10), token("b", 10)))
        val merged = SyncMerger.merge(local, VaultData())
        assertEquals(2, merged.tokens.size)
    }

    @Test
    fun tombstoneDeletesOnlyOlderVersions() {
        val local = VaultData(listOf(token("a", 10), token("b", 50)))
        val remote = VaultData(tombstones = mapOf("a" to 20L, "b" to 20L))
        val merged = SyncMerger.merge(local, remote)
        assertEquals(listOf("b"), merged.tokens.map { it.id })
        assertEquals(setOf("a", "b"), merged.tombstones.keys)
    }

    @Test
    fun trashSyncsAsAnUpdate() {
        val local = VaultData(listOf(token("a", 10)))
        val remote = VaultData(listOf(token("a", 30, trashedAt = 30)))
        assertTrue(SyncMerger.merge(local, remote).tokens.single().isTrashed)
    }

    @Test
    fun hotpKeepsHighestCounter() {
        val local = VaultData(listOf(token("a", 100, counter = 3)))
        val remote = VaultData(listOf(token("a", 50, counter = 9)))
        val merged = SyncMerger.merge(local, remote).tokens.single()
        assertEquals(9, merged.counter)
        assertEquals(100, merged.updatedAt)
    }

    @Test
    fun vaultJsonRoundTrip() {
        val data = VaultData(
            listOf(token("a", 10), Token(id = "z", issuer = "Ñandú", account = "yo", secret = "ABCD", type = OtpType.HOTP, algorithm = OtpAlgorithm.SHA512, digits = 8, counter = 4, pinned = true, createdAt = 3, updatedAt = 4, trashedAt = 5)),
            mapOf("x" to 99L)
        )
        assertEquals(data, VaultData.parse(data.serialize()))
    }

    @Test
    fun encryptedBackupRoundTrip() = runBlocking {
        val data = VaultData(listOf(token("a", 10), token("b", 10, trashedAt = 11)))
        val file = VaultCodec.exportBackup(data, "contraseña segura")
        assertFalse("El secreto no debe aparecer en claro", file.contains("JBSWY3DPEHPK3PXP"))
        val content = ImportParser.parse(file)
        assertTrue(content is ImportContent.Encrypted)
        val envelope = (content as ImportContent.Encrypted).envelope
        // NFD equivalente: debe abrir igual.
        val key = VaultCodec.unlock(envelope, java.text.Normalizer.normalize("contraseña segura", java.text.Normalizer.Form.NFD))
        val restored = envelope.decrypt(key)
        assertEquals(listOf("a"), restored.tokens.map { it.id }) // la papelera no se exporta
        try {
            VaultCodec.unlock(envelope, "otra")
            fail("Debería rechazar la contraseña")
        } catch (_: WrongPasswordException) {
        }
    }

    @Test
    fun tamperedBackupIsRejected() = runBlocking {
        val file = VaultCodec.exportBackup(VaultData(listOf(token("a", 10))), "contraseña segura")
        val envelope = VaultCodec.parse(file)
        val key = VaultCodec.unlock(envelope, "contraseña segura")
        val bytes = java.util.Base64.getDecoder().decode(envelope.data)
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        val tampered = envelope.copy(data = java.util.Base64.getEncoder().encodeToString(bytes))
        try {
            tampered.decrypt(key)
            fail("Debería detectar la manipulación")
        } catch (_: Exception) {
        }
    }

    @Test
    fun importsUriListAegisAndTwoFas() {
        val list = ImportParser.parse(
            """
            otpauth://totp/A:a?secret=JBSWY3DPEHPK3PXP
            basura
            otpauth://totp/B:b?secret=!!!
            """.trimIndent()
        ) as ImportContent.Tokens
        assertEquals(1, list.tokens.size)
        assertEquals(1, list.skipped)

        val aegis = ImportParser.parse(
            """{"version":1,"header":{"slots":null,"params":null},"db":{"version":2,"entries":[
              {"type":"totp","name":"me@x.com","issuer":"X","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA256","digits":8,"period":30}},
              {"type":"steam","name":"g","issuer":"Steam","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":5,"period":30}},
              {"type":"yandex","name":"y","issuer":"Y","info":{"secret":"JBSWY3DPEHPK3PXP"}}]}}"""
        ) as ImportContent.Tokens
        assertEquals(2, aegis.tokens.size)
        assertEquals(1, aegis.skipped)
        assertEquals(OtpAlgorithm.SHA256, aegis.tokens[0].algorithm)
        assertEquals(OtpType.STEAM, aegis.tokens[1].type)

        val aegisEncrypted = ImportParser.parse("""{"version":1,"header":{"slots":[]},"db":"AAAA"}""")
        assertTrue(aegisEncrypted is ImportContent.Unsupported)

        val twofas = ImportParser.parse(
            """{"schemaVersion":4,"services":[{"name":"GitHub","secret":"JBSWY3DPEHPK3PXP","otp":{"account":"octo","issuer":"GitHub","digits":6,"period":30,"algorithm":"SHA1","tokenType":"TOTP"}}]}"""
        ) as ImportContent.Tokens
        assertEquals("GitHub", twofas.tokens.single().issuer)
        assertEquals("octo", twofas.tokens.single().account)
    }

    @Test
    fun plainExportReimports() {
        val tokens = listOf(
            Token(issuer = "A", account = "a", secret = "JBSWY3DPEHPK3PXP"),
            Token(issuer = "B", account = "b", secret = "JBSWY3DPEHPK3PXQ", type = OtpType.HOTP, counter = 5)
        )
        val back = (ImportParser.parse(ImportParser.exportPlain(tokens)) as ImportContent.Tokens).tokens
        assertEquals(tokens.map { it.identityKey }, back.map { it.identityKey })
        assertEquals(5, back[1].counter)
    }

    @Test
    fun importIgnoresByteOrderMark() {
        val bom = 0xFEFF.toChar()
        val content = ImportParser.parse("${bom}otpauth://totp/A:a?secret=JBSWY3DPEHPK3PXP\r\n") as ImportContent.Tokens
        assertEquals(1, content.tokens.size)
        val json = ImportParser.parse(bom + "{\"schemaVersion\":4,\"services\":[{\"name\":\"X\",\"secret\":\"JBSWY3DPEHPK3PXP\",\"otp\":{}}]}")
        assertTrue(json is ImportContent.Tokens)
    }

    @Test
    fun oldTombstonesArePurgedRecentOnesKept() {
        val now = 1_000L * 24 * 60 * 60 * 1000
        val old = now - SyncMerger.TOMBSTONE_TTL_MS - 1
        val recent = now - 1000
        val vault = VaultData(listOf(token("a", 10)), mapOf("old" to old, "recent" to recent))
        val purged = SyncMerger.purgeTombstones(vault, now)
        assertEquals(setOf("recent"), purged.tombstones.keys)
        assertEquals(vault.tokens, purged.tokens)
    }

    @Test
    fun recentTombstoneStillDeletesAfterPurge() {
        val now = 1_000L * 24 * 60 * 60 * 1000
        val local = VaultData(listOf(token("a", now - 5000)))
        val remote = VaultData(tombstones = mapOf("a" to now - 1000))
        val merged = SyncMerger.purgeTombstones(SyncMerger.merge(local, remote), now)
        assertTrue(merged.tokens.isEmpty())
        assertEquals(setOf("a"), merged.tombstones.keys)
    }

    @Test
    fun tombstoneAtSameMomentAsEditDeletes() {
        // Empate exacto: el borrado gana (deletedAt >= updatedAt).
        val merged = SyncMerger.merge(VaultData(listOf(token("a", 100))), VaultData(tombstones = mapOf("a" to 100L)))
        assertTrue(merged.tokens.isEmpty())
    }

    @Test
    fun editOneMillisecondAfterDeleteSurvivesOnBothDevices() {
        val edited = VaultData(listOf(token("a", 101, "Editada")))
        val deleted = VaultData(tombstones = mapOf("a" to 100L))
        val x = SyncMerger.merge(edited, deleted)
        val y = SyncMerger.merge(deleted, edited)
        assertEquals("Editada", x.tokens.single().issuer)
        assertTrue(SyncMerger.sameContent(x, y))
    }

    @Test
    fun hotpCounterNeverGoesBackEvenIfOlderVersionWins() {
        val newerEdit = token("a", 200, "Nuevo nombre", counter = 3)
        val olderButAdvanced = token("a", 150, counter = 9)
        val merged = SyncMerger.merge(VaultData(listOf(newerEdit)), VaultData(listOf(olderButAdvanced)))
        val t = merged.tokens.single()
        assertEquals("Nuevo nombre", t.issuer)
        assertEquals(9L, t.counter)
    }

    @Test
    fun newerFileVersionIsReportedAsSuchNotAsCorrupt() {
        val (key, salt, iterations) = runBlocking { VaultCodec.newKey("contraseña larga") }
        val newer = org.json.JSONObject(VaultCodec.encode(VaultCodec.FORMAT_BACKUP, VaultData(), key, salt, iterations))
            .put("version", VaultCodec.VERSION + 1).toString()
        try {
            VaultCodec.parse(newer)
            fail("Debería rechazar una versión futura")
        } catch (_: com.ferlagod.mis2fa.data.sync.NewerVaultVersionException) {
        }
    }
}
