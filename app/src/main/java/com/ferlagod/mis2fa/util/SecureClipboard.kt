// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Copia códigos al portapapeles de forma discreta:
 * - Se marcan como sensibles: Android 13+ no los muestra en la vista previa ni en el teclado.
 * - Se borran solos pasados unos segundos.
 *
 * El borrado se programa dos veces: con un [Handler] (puntual mientras la app sigue viva) y con
 * WorkManager como respaldo, por si el sistema cierra el proceso antes. En WorkManager nunca se
 * guarda el código, solo su huella SHA-256.
 *
 * Limitación de Android 10+: en segundo plano la app no puede leer el portapapeles, así que no
 * puede comprobar si el usuario copió otra cosa después; en ese caso se borra igualmente
 * (la privacidad del código prevalece).
 */
object SecureClipboard {

    private const val WORK_NAME = "mis2fa_clipboard_clear"
    private const val KEY_HASH = "hash"

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

        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        pendingClear?.let(handler::removeCallbacks)
        if (clearAfterSeconds <= 0) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val hash = sha256(text)
        val task = Runnable {
            clearIfOurs(appContext, hash)
            workManager.cancelUniqueWork(WORK_NAME)
        }
        pendingClear = task
        handler.postDelayed(task, clearAfterSeconds * 1000L)

        val backup = OneTimeWorkRequestBuilder<ClearWorker>()
            // Un poco después que el Handler: si el proceso sigue vivo, el Handler llega antes y lo cancela.
            .setInitialDelay(clearAfterSeconds + 5L, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_HASH to hash))
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, backup)
    }

    /** Borra el portapapeles salvo que se pueda comprobar que ya contiene otra cosa. */
    internal fun clearIfOurs(context: Context, hash: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        try {
            // null también cuando Android 10+ no deja leerlo en segundo plano: se borra.
            val current = cm.primaryClip
            val other = current != null && current.itemCount > 0 &&
                current.getItemAt(0).text?.toString()?.let(::sha256) != hash
            if (other) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
            else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { cm.clearPrimaryClip() }
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Android 13+ ya muestra su propio aviso al copiar; antes hay que confirmarlo en la app. */
    val systemShowsCopyConfirmation: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Respaldo del borrado si el proceso se cerró antes de que llegara el [Handler]. */
    class ClearWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            val hash = inputData.getString(KEY_HASH) ?: return Result.success()
            // ClipboardManager necesita el hilo principal en algunas versiones.
            Handler(Looper.getMainLooper()).post { clearIfOurs(applicationContext, hash) }
            return Result.success()
        }
    }
}
