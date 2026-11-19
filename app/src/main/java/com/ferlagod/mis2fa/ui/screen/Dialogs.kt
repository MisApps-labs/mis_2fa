// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.ui.screen

import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.ferlagod.mis2fa.R
import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.otp.OtpAuthUri
import com.ferlagod.mis2fa.security.CryptoManager
import com.ferlagod.mis2fa.ui.components.ExcludeWindowFromAutofill
import com.ferlagod.mis2fa.ui.components.PasswordField
import com.ferlagod.mis2fa.ui.theme.AccentRed
import com.ferlagod.mis2fa.util.QrCodes

/**
 * Pide una contraseña.
 *
 * @param confirm pide escribirla dos veces y exige una longitud mínima (para contraseñas nuevas).
 * @param error mensaje de error a mostrar (p. ej. "Contraseña incorrecta").
 */
@Composable
fun PasswordDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    confirm: Boolean = false,
    busy: Boolean = false,
    error: String? = null,
    warning: String? = null
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    val tooShort = confirm && password.length < CryptoManager.MIN_PASSWORD_LENGTH
    val mismatch = confirm && password != repeat
    val valid = password.isNotEmpty() && !tooShort && !mismatch

    fun submit() {
        tried = true
        if (valid && !busy) onConfirm(password)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            ExcludeWindowFromAutofill()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
                if (warning != null) {
                    Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.password),
                    isError = (tried && (password.isEmpty() || tooShort)) || error != null,
                    supportingText = when {
                        error != null -> error
                        tried && tooShort -> pluralStringResource(R.plurals.error_password_short, CryptoManager.MIN_PASSWORD_LENGTH, CryptoManager.MIN_PASSWORD_LENGTH)
                        else -> null
                    },
                    imeAction = if (confirm) ImeAction.Next else ImeAction.Done,
                    onImeAction = { if (!confirm) submit() },
                    enabled = !busy
                )
                if (confirm) {
                    PasswordField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = stringResource(R.string.password_repeat),
                        isError = tried && mismatch,
                        supportingText = if (tried && mismatch) stringResource(R.string.error_password_mismatch) else null,
                        onImeAction = ::submit,
                        enabled = !busy
                    )
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !busy) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Confirmación simple; [danger] pinta el botón en rojo. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = if (danger) {
            { Icon(Icons.Rounded.Warning, contentDescription = null, tint = AccentRed) }
        } else null,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** QR de una cuenta, para pasarla a otro dispositivo o a otra app. */
@Composable
fun TokenQrDialog(token: Token, onDismiss: () -> Unit) {
    val bitmap = remember(token) { QrCodes.encode(OtpAuthUri.build(token), 720).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(token.displayName, fontWeight = FontWeight.Bold) },
        text = {
            // Brillo al máximo para que la otra cámara lo lea bien.
            val view = LocalView.current
            DisposableEffect(view) {
                val window = (view.parent as? DialogWindowProvider)?.window ?: (view as? DialogWindowProvider)?.window
                window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
                onDispose { }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.action_show_qr),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.qr_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
