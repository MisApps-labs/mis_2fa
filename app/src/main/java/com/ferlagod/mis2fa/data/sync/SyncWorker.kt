// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Sincronización en segundo plano: justo después de cada cambio (con reintentos si no hay red)
 * y cada pocas horas para traer lo que se haya cambiado en otros dispositivos.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = SyncManager.getInstance(applicationContext)
        if (!manager.isConfigured()) return Result.success()
        return when (val state = manager.sync()) {
            is SyncState.Failed -> if (state.error == SyncError.NETWORK || state.error == SyncError.SERVER) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        private const val ONE_TIME = "mis2fa_sync_now"
        private const val PERIODIC = "mis2fa_sync_periodic"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Sube los cambios en cuanto haya red (agrupa los cambios hechos en pocos segundos). */
        fun requestSync(context: Context, delaySeconds: Long = 2) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraint)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
