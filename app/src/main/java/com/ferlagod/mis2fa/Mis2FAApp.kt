// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa

import android.app.Application
import com.ferlagod.mis2fa.data.TokenRepository
import com.ferlagod.mis2fa.data.local.SecureSettings
import com.ferlagod.mis2fa.data.sync.SyncWorker

class Mis2FAApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val repository = TokenRepository.getInstance(this)
        val secure = SecureSettings.getInstance(this)
        // Cada cambio se sube a Nextcloud en cuanto haya conexión.
        repository.onLocalChange = {
            if (secure.isConfigured()) SyncWorker.requestSync(this)
        }
        if (secure.isConfigured()) SyncWorker.schedulePeriodic(this)
    }
}
