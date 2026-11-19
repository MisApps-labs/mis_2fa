// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copia códigos al portapapeles de forma discreta:
 * - Se marcan como sensibles: Android 13+ no los muestra en la vista previa ni en el teclado.
 * - Se borran solos pasados unos segundos (si el portapapeles no ha cambiado entretanto).
 */
object SecureClipboard {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    fun copy(context: Context, label: String, text: String, clearAfterSeconds: Int) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)

        pendingClear?.let(handler::removeCallbacks)
        if (clearAfterSeconds <= 0) return
        val appContext = context.applicationContext
        val task = Runnable {
            val cm = appContext.getSystemService(ClipboardManager::class.java) ?: return@Runnable
            try {
                // Solo se borra si sigue siendo nuestro código (no se toca lo que copió el usuario después).
                val current = cm.primaryClip
                val stillOurs = current == null ||
                    (current.itemCount > 0 && current.getItemAt(0).text?.toString() == text)
                if (stillOurs) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
                    else cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (_: Exception) {
                // Android 10+ no deja leer el portapapeles en segundo plano: se borra igualmente.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { cm.clearPrimaryClip() }
            }
        }
        pendingClear = task
        handler.postDelayed(task, clearAfterSeconds * 1000L)
    }

    /** Android 13+ ya muestra su propio aviso al copiar; antes hay que confirmarlo en la app. */
    val systemShowsCopyConfirmation: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
