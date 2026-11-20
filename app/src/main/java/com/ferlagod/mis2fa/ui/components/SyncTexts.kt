// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.ui.components

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ferlagod.mis2fa.R
import com.ferlagod.mis2fa.data.sync.SyncError

@Composable
fun syncErrorText(error: SyncError): String = stringResource(syncErrorRes(error))

fun syncErrorRes(error: SyncError): Int =
    when (error) {
        SyncError.AUTH -> R.string.sync_error_auth
        SyncError.NETWORK -> R.string.sync_error_network
        SyncError.PATH_NOT_FOUND -> R.string.sync_error_path
        SyncError.WRONG_PASSWORD -> R.string.sync_error_wrong_password
        SyncError.PASSWORD_CHANGED -> R.string.sync_error_password_changed
        SyncError.REMOTE_CORRUPT -> R.string.sync_error_remote_corrupt
        SyncError.REMOTE_NEWER -> R.string.sync_error_remote_newer
        SyncError.HTTPS_REQUIRED -> R.string.sync_error_https
        SyncError.LOCAL_UNREADABLE -> R.string.error_vault_unreadable
        SyncError.SERVER -> R.string.sync_error_server
        SyncError.UNKNOWN -> R.string.sync_error_unknown
    }

/** "Ahora mismo", "Hace 5 minutos", "Ayer"… en el idioma de la app. */
@Composable
fun relativeTime(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    if (elapsed in 0 until DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.time_just_now)
    return DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}
