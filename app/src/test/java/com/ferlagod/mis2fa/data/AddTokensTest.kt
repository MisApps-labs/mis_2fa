// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.data

import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.model.VaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AddTokensTest {

    private val secret = "JBSWY3DPEHPK3PXP"

    @Test
    fun importedAccountWithSameIdButEditedNameGetsNewId() {
        // En el dispositivo: la cuenta se renombró después de hacer la copia de seguridad.
        val current = VaultData(listOf(Token(id = "x", issuer = "Renombrada", secret = secret, createdAt = 1, updatedAt = 5)))
        // En la copia: la misma cuenta (mismo id) con el nombre antiguo.
        val fromBackup = Token(id = "x", issuer = "Original", secret = secret, createdAt = 1, updatedAt = 1)

        val (next, result) = TokenRepository.addTokens(current, listOf(fromBackup), now = 100)

        assertEquals(1, result.added)
        assertEquals(2, next.tokens.size)
        assertEquals(2, next.tokens.map { it.id }.toSet().size)
        assertNotEquals("x", next.tokens.last().id)
    }

    @Test
    fun exactDuplicateIsNotAdded() {
        val current = VaultData(listOf(Token(id = "x", issuer = "A", secret = secret)))
        val (next, result) = TokenRepository.addTokens(current, listOf(Token(id = "y", issuer = "a ", secret = secret)), now = 100)
        assertEquals(0, result.added)
        assertEquals(1, result.duplicates)
        assertEquals(1, next.tokens.size)
    }

    @Test
    fun duplicateInTrashIsRestored() {
        val current = VaultData(listOf(Token(id = "x", issuer = "A", secret = secret, updatedAt = 50, trashedAt = 50)))
        val (next, result) = TokenRepository.addTokens(current, listOf(Token(issuer = "A", secret = secret)), now = 10)
        assertEquals(1, result.added)
        val restored = next.tokens.single()
        assertEquals("x", restored.id)
        assertFalse(restored.isTrashed)
        // Aunque el reloj vaya hacia atrás, la restauración es posterior a la última modificación.
        assertEquals(51, restored.updatedAt)
    }

    @Test
    fun sameAccountTwiceInOneImportIsAddedOnce() {
        val t = Token(issuer = "A", secret = secret)
        val (next, result) = TokenRepository.addTokens(VaultData(), listOf(t, t.copy()), now = 1)
        assertEquals(1, result.added)
        assertEquals(1, result.duplicates)
        assertEquals(1, next.tokens.size)
    }
}
