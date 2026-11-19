// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.data.sync

import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.model.VaultData

/**
 * Reglas para combinar la bóveda del dispositivo con la del servidor.
 *
 * Están pensadas para que **nunca se pierda una cuenta**:
 * - Es una unión: que una cuenta falte en un lado no la borra del otro.
 * - Solo una lápida (borrado definitivo desde la papelera) elimina una cuenta, y solo si la
 *   cuenta no se ha modificado después del borrado.
 * - Si una cuenta cambió en los dos lados, gana la modificación más reciente.
 * - En HOTP se conserva siempre el contador más alto, para no repetir códigos ya usados.
 *
 * La fusión es conmutativa: los dos dispositivos llegan al mismo resultado.
 */
object SyncMerger {

    fun merge(a: VaultData, b: VaultData): VaultData {
        val tombstones = (a.tombstones.keys + b.tombstones.keys).associateWith { id ->
            maxOf(a.tombstones[id] ?: Long.MIN_VALUE, b.tombstones[id] ?: Long.MIN_VALUE)
        }
        val byIdA = a.tokens.associateBy { it.id }
        val byIdB = b.tokens.associateBy { it.id }
        val tokens = (byIdA.keys + byIdB.keys).mapNotNull { id ->
            val winner = pick(byIdA[id], byIdB[id]) ?: return@mapNotNull null
            val deletedAt = tombstones[id]
            if (deletedAt != null && deletedAt >= winner.updatedAt) null else winner
        }.sortedWith(compareBy<Token> { it.createdAt }.thenBy { it.id })
        return VaultData(tokens, tombstones)
    }

    private fun pick(x: Token?, y: Token?): Token? {
        if (x == null) return y
        if (y == null) return x
        val winner = when {
            x.updatedAt > y.updatedAt -> x
            y.updatedAt > x.updatedAt -> y
            // Empate: criterio determinista para que todos los dispositivos elijan lo mismo.
            else -> if (x.toJson().toString() >= y.toJson().toString()) x else y
        }
        val counter = maxOf(x.counter, y.counter)
        return if (winner.counter == counter) winner else winner.copy(counter = counter)
    }

    /** Mismo contenido aunque el orden de la lista sea distinto. */
    fun sameContent(x: VaultData, y: VaultData): Boolean =
        x.tombstones == y.tombstones && x.tokens.size == y.tokens.size && x.tokens.toSet() == y.tokens.toSet()
}
