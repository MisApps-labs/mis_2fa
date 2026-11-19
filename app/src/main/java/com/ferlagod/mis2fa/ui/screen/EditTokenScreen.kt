// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ferlagod.mis2fa.R
import com.ferlagod.mis2fa.model.OtpAlgorithm
import com.ferlagod.mis2fa.model.OtpType
import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.otp.Base32
import com.ferlagod.mis2fa.otp.OtpGenerator
import com.ferlagod.mis2fa.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mis2fa.ui.components.TokenAvatar
import com.ferlagod.mis2fa.ui.theme.CodeTextStyle

/**
 * Alta manual o edición de una cuenta.
 *
 * @param existing la cuenta a editar, o null para una nueva.
 * @param onRevealSecret pide autenticación antes de mostrar la clave secreta de una cuenta existente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTokenScreen(
    existing: Token?,
    onBack: () -> Unit,
    onSave: (Token) -> Unit,
    onRevealSecret: (onGranted: () -> Unit) -> Unit
) {
    val isNew = existing == null
    var issuer by rememberSaveable { mutableStateOf(existing?.issuer.orEmpty()) }
    var account by rememberSaveable { mutableStateOf(existing?.account.orEmpty()) }
    var secret by rememberSaveable { mutableStateOf(existing?.secret.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: OtpType.TOTP) }
    var algorithm by rememberSaveable { mutableStateOf(existing?.algorithm ?: OtpAlgorithm.SHA1) }
    var digits by rememberSaveable { mutableStateOf((existing?.digits ?: Token.DEFAULT_DIGITS).toString()) }
    var period by rememberSaveable { mutableStateOf((existing?.period ?: Token.DEFAULT_PERIOD).toString()) }
    var counter by rememberSaveable { mutableStateOf((existing?.counter ?: 0L).toString()) }
    var secretVisible by rememberSaveable { mutableStateOf(isNew) }
    var showAdvanced by rememberSaveable {
        mutableStateOf(existing != null && (existing.type != OtpType.TOTP || existing.algorithm != OtpAlgorithm.SHA1 || existing.digits != 6 || existing.period != 30))
    }
    var triedSave by remember { mutableStateOf(false) }

    val secretValid = Base32.isValid(secret)
    val digitsValue = digits.toIntOrNull()
    val periodValue = period.toIntOrNull()
    val counterValue = counter.toLongOrNull()
    val digitsValid = type == OtpType.STEAM || (digitsValue != null && digitsValue in Token.DIGITS_RANGE)
    val periodValid = type != OtpType.TOTP || (periodValue != null && periodValue in Token.PERIOD_RANGE)
    val counterValid = type != OtpType.HOTP || (counterValue != null && counterValue >= 0)
    val nameValid = issuer.isNotBlank() || account.isNotBlank()
    val formValid = secretValid && digitsValid && periodValid && counterValid && nameValid

    fun buildToken(): Token {
        val base = existing ?: Token(secret = "")
        return base.copy(
            issuer = issuer.trim(),
            account = account.trim(),
            secret = Base32.normalize(secret),
            type = type,
            algorithm = if (type == OtpType.STEAM) OtpAlgorithm.SHA1 else algorithm,
            digits = if (type == OtpType.STEAM) Token.STEAM_DIGITS else digitsValue ?: Token.DEFAULT_DIGITS,
            period = periodValue?.takeIf { type == OtpType.TOTP } ?: Token.DEFAULT_PERIOD,
            counter = counterValue ?: 0L
        )
    }

    fun save() {
        triedSave = true
        if (formValid) onSave(buildToken())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.add_account else R.string.edit_account), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    TextButton(onClick = ::save) { Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // El teclado reduce el área visible (y no solo el contenido) para que el campo
                // que se está escribiendo se desplace a la vista.
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = READABLE_MAX_WIDTH)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text(stringResource(R.string.field_issuer)) },
                placeholder = { Text(stringResource(R.string.field_issuer_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                isError = triedSave && !nameValid,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text(stringResource(R.string.field_account)) },
                placeholder = { Text(stringResource(R.string.field_account_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                isError = triedSave && !nameValid,
                supportingText = if (triedSave && !nameValid) {
                    { Text(stringResource(R.string.error_name_required)) }
                } else null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { if (secretVisible || isNew) secret = it },
                label = { Text(stringResource(R.string.field_secret)) },
                placeholder = { Text("JBSW Y3DP EHPK 3PXP") },
                singleLine = true,
                readOnly = !secretVisible,
                visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                isError = (triedSave || secret.length > 4) && !secretValid,
                supportingText = {
                    Text(
                        stringResource(
                            if ((triedSave || secret.length > 4) && !secretValid) R.string.error_secret_invalid else R.string.field_secret_help
                        )
                    )
                },
                trailingIcon = {
                    IconButton(onClick = {
                        if (secretVisible) secretVisible = false
                        else if (isNew) secretVisible = true
                        else onRevealSecret { secretVisible = true }
                    }) {
                        Icon(
                            if (secretVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(if (secretVisible) R.string.cd_hide_password else R.string.cd_show_password)
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // --- Opciones avanzadas ---
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Icon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.advanced_options))
            }
            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label(stringResource(R.string.field_type))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val types = OtpType.entries
                        types.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = type == t,
                                onClick = { type = t },
                                shape = SegmentedButtonDefaults.itemShape(i, types.size)
                            ) {
                                Text(
                                    stringResource(
                                        when (t) {
                                            OtpType.TOTP -> R.string.type_totp
                                            OtpType.HOTP -> R.string.type_hotp
                                            OtpType.STEAM -> R.string.type_steam
                                        }
                                    )
                                )
                            }
                        }
                    }
                    if (type != OtpType.STEAM) {
                        Label(stringResource(R.string.field_algorithm))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val algorithms = OtpAlgorithm.entries
                            algorithms.forEachIndexed { i, a ->
                                SegmentedButton(
                                    selected = algorithm == a,
                                    onClick = { algorithm = a },
                                    shape = SegmentedButtonDefaults.itemShape(i, algorithms.size)
                                ) { Text(a.name.replace("SHA", "SHA-")) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NumberField(
                                value = digits,
                                onChange = { digits = it },
                                label = stringResource(R.string.field_digits),
                                isError = !digitsValid,
                                modifier = Modifier.weight(1f)
                            )
                            if (type == OtpType.TOTP) {
                                NumberField(
                                    value = period,
                                    onChange = { period = it },
                                    label = stringResource(R.string.field_period),
                                    isError = !periodValid,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                NumberField(
                                    value = counter,
                                    onChange = { counter = it },
                                    label = stringResource(R.string.field_counter),
                                    isError = !counterValid,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // --- Vista previa ---
            if (formValid) {
                val preview = remember(issuer, account, secret, type, algorithm, digits, period, counter) {
                    runCatching { OtpGenerator.code(buildToken(), System.currentTimeMillis()) }.getOrNull()
                }
                if (preview != null) {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            TokenAvatar(issuer.ifBlank { account })
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(OtpGenerator.format(preview), style = CodeTextStyle, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = ::save,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(if (isNew) R.string.add_account else R.string.save))
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(12)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}
