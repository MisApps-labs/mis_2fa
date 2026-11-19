// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.util

import java.text.Normalizer

/** Búsqueda que ignora mayúsculas y acentos ("cafe" encuentra "Café"). */
object TextSearch {
    private val marks = "\\p{Mn}+".toRegex()

    fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase()

    fun matches(query: String, vararg fields: String): Boolean {
        val q = fold(query.trim())
        if (q.isEmpty()) return true
        return fields.any { fold(it).contains(q) }
    }
}
