// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ferlagod.mis2fa.R
import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.ui.components.EmptyState
import com.ferlagod.mis2fa.ui.components.HintCard
import com.ferlagod.mis2fa.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mis2fa.ui.components.SingleLine
import com.ferlagod.mis2fa.ui.components.TokenAvatar
import com.ferlagod.mis2fa.ui.components.relativeTime
import com.ferlagod.mis2fa.ui.theme.AccentRed

/**
 * Papelera: las cuentas borradas se quedan aquí hasta que el usuario las elimina a mano.
 * No hay vaciado automático, para que nunca se pierda un código por despiste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    tokens: List<Token>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEmpty: () -> Unit
) {
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    if (tokens.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (tokens.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.DeleteOutline,
                    title = stringResource(R.string.trash_empty_title),
                    message = stringResource(R.string.trash_empty_message)
                )
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                HintCard(
                    text = stringResource(R.string.trash_info),
                    icon = Icons.Rounded.Info,
                    modifier = Modifier.widthIn(max = READABLE_MAX_WIDTH)
                )
            }
            items(tokens, key = { it.id }) { token ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().widthIn(max = READABLE_MAX_WIDTH).animateItem()
                ) {
                    Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TokenAvatar(token.displayName, size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLine(token.displayName, MaterialTheme.typography.titleSmall, MaterialTheme.colorScheme.onSurface)
                            if (token.account.isNotBlank() && token.issuer.isNotBlank()) {
                                SingleLine(token.account, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            token.trashedAt?.let {
                                Text(
                                    stringResource(R.string.trash_deleted_when, relativeTime(it)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onRestore(token.id) }) {
                            Icon(Icons.Rounded.RestoreFromTrash, contentDescription = stringResource(R.string.action_restore), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { confirmDelete = token.id }) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = stringResource(R.string.action_delete_forever), tint = AccentRed)
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        val token = tokens.firstOrNull { it.id == id }
        if (token == null) {
            confirmDelete = null
        } else {
            ConfirmDialog(
                title = stringResource(R.string.delete_forever_title),
                message = stringResource(R.string.delete_forever_message, token.displayName),
                confirmLabel = stringResource(R.string.action_delete_forever),
                danger = true,
                onDismiss = { confirmDelete = null },
                onConfirm = {
                    confirmDelete = null
                    onDelete(id)
                }
            )
        }
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.trash_empty_confirm_title),
            message = stringResource(R.string.trash_empty_confirm_message),
            confirmLabel = stringResource(R.string.trash_empty),
            danger = true,
            onDismiss = { confirmEmpty = false },
            onConfirm = {
                confirmEmpty = false
                onEmpty()
            }
        )
    }
}
