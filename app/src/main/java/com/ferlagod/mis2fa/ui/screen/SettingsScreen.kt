// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.mis2fa.BuildConfig
import com.ferlagod.mis2fa.R
import com.ferlagod.mis2fa.data.local.AppSettings
import com.ferlagod.mis2fa.data.local.AppSettingsState
import com.ferlagod.mis2fa.data.local.LockTimeout
import com.ferlagod.mis2fa.data.local.SortOrder
import com.ferlagod.mis2fa.data.local.SyncConfig
import com.ferlagod.mis2fa.data.local.ThemeMode
import com.ferlagod.mis2fa.data.sync.SyncError
import com.ferlagod.mis2fa.data.sync.SyncState
import com.ferlagod.mis2fa.security.CryptoManager
import com.ferlagod.mis2fa.ui.components.ActionRow
import com.ferlagod.mis2fa.ui.components.ChoiceRow
import com.ferlagod.mis2fa.ui.components.HintCard
import com.ferlagod.mis2fa.ui.components.PasswordField
import com.ferlagod.mis2fa.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mis2fa.ui.components.SectionTitle
import com.ferlagod.mis2fa.ui.components.SettingIcon
import com.ferlagod.mis2fa.ui.components.SettingsCard
import com.ferlagod.mis2fa.ui.components.SettingsDivider
import com.ferlagod.mis2fa.ui.components.SwitchRow
import com.ferlagod.mis2fa.ui.components.relativeTime
import com.ferlagod.mis2fa.ui.components.syncErrorText
import com.ferlagod.mis2fa.ui.theme.AccentGreen
import com.ferlagod.mis2fa.ui.theme.AccentOrange
import com.ferlagod.mis2fa.ui.theme.AccentRed
import com.ferlagod.mis2fa.ui.theme.warningTextColor
import com.ferlagod.mis2fa.util.AppLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Acciones de la pantalla de ajustes. */
class SettingsActions(
    val onBack: () -> Unit,
    val onThemeChange: (ThemeMode) -> Unit,
    val onAmoledChange: (Boolean) -> Unit,
    val onSortOrderChange: (SortOrder) -> Unit,
    val onShowAccountNameChange: (Boolean) -> Unit,
    val onShowNextCodeChange: (Boolean) -> Unit,
    val onAppLockChange: (Boolean) -> Unit,
    val onLockTimeoutChange: (LockTimeout) -> Unit,
    val onBlockScreenshotsChange: (Boolean) -> Unit,
    val onHideCodesChange: (Boolean) -> Unit,
    val onClipboardClearChange: (Int) -> Unit,
    val onConnect: (server: String, user: String, appPassword: String, masterPassword: String, onResult: (SyncError?) -> Unit) -> Unit,
    val onReenterPassword: () -> Unit,
    val onChangePassword: () -> Unit,
    val onDisconnect: () -> Unit,
    val onSyncNow: () -> Unit,
    /** Exportar (cifrada o en texto); la pantalla pide la contraseña y el fichero. */
    val onExportEncrypted: (android.net.Uri) -> Unit,
    val onExportPlain: (android.net.Uri) -> Unit,
    /** Pide autenticación antes de una exportación. */
    val requireAuth: (onGranted: () -> Unit) -> Unit,
    val onImport: () -> Unit,
    val onOpenTrash: () -> Unit,
    val onOpenTutorial: () -> Unit,
    val onLanguageSelected: (AppLanguage) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettingsState,
    syncState: SyncState,
    syncConfig: SyncConfig?,
    trashCount: Int,
    accountCount: Int,
    busy: Boolean,
    isAppLockAvailable: Boolean,
    currentLanguage: AppLanguage,
    snackbarHostState: SnackbarHostState,
    actions: SettingsActions
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showPlainWarning by rememberSaveable { mutableStateOf(false) }

    val exportEncryptedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.onExportEncrypted(uri)
    }
    val exportPlainLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) actions.onExportPlain(uri)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
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
                .padding(horizontal = 16.dp)
        ) {
            // --- Sincronización ---
            SectionTitle(stringResource(R.string.section_sync))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    NextcloudSection(syncState, syncConfig, busy, actions)
                }
            }

            // --- Seguridad ---
            SectionTitle(stringResource(R.string.section_security))
            SettingsCard {
                SwitchRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = stringResource(R.string.app_lock),
                    subtitle = stringResource(if (isAppLockAvailable) R.string.app_lock_desc else R.string.app_lock_unavailable),
                    checked = settings.appLock && isAppLockAvailable,
                    enabled = isAppLockAvailable,
                    onCheckedChange = actions.onAppLockChange
                )
                AnimatedVisibility(visible = settings.appLock && isAppLockAvailable) {
                    Column {
                        SettingsDivider()
                        ChoiceRow(
                            icon = Icons.Rounded.Schedule,
                            title = stringResource(R.string.lock_timeout),
                            options = LockTimeout.entries,
                            selected = settings.lockTimeout,
                            label = { lockTimeoutLabel(it) },
                            onSelect = actions.onLockTimeoutChange
                        )
                    }
                }
                SettingsDivider()
                SwitchRow(
                    icon = Icons.Rounded.ScreenLockPortrait,
                    title = stringResource(R.string.block_screenshots),
                    subtitle = stringResource(R.string.block_screenshots_desc),
                    checked = settings.blockScreenshots,
                    onCheckedChange = actions.onBlockScreenshotsChange
                )
                SettingsDivider()
                SwitchRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.hide_codes),
                    subtitle = stringResource(R.string.hide_codes_desc),
                    checked = settings.hideCodes,
                    onCheckedChange = actions.onHideCodesChange
                )
                SettingsDivider()
                ChoiceRow(
                    icon = Icons.Rounded.ContentPaste,
                    title = stringResource(R.string.clipboard_clear),
                    options = AppSettings.CLIPBOARD_CLEAR_OPTIONS,
                    selected = settings.clipboardClearSeconds,
                    label = { clipboardLabel(it) },
                    onSelect = actions.onClipboardClearChange
                )
            }

            // --- Apariencia ---
            SectionTitle(stringResource(R.string.section_appearance))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingIcon(Icons.Rounded.DarkMode)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = ThemeMode.entries
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { actions.onThemeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                            ) {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> R.string.theme_system
                                            ThemeMode.LIGHT -> R.string.theme_light
                                            ThemeMode.DARK -> R.string.theme_dark
                                        }
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                SettingsDivider()
                SwitchRow(
                    icon = Icons.Rounded.Contrast,
                    title = stringResource(R.string.amoled_mode),
                    subtitle = stringResource(R.string.amoled_mode_desc),
                    checked = settings.amoled,
                    onCheckedChange = actions.onAmoledChange
                )
                SettingsDivider()
                SwitchRow(
                    icon = Icons.Rounded.Badge,
                    title = stringResource(R.string.show_account_name),
                    subtitle = null,
                    checked = settings.showAccountName,
                    onCheckedChange = actions.onShowAccountNameChange
                )
                SettingsDivider()
                SwitchRow(
                    icon = Icons.Rounded.Upcoming,
                    title = stringResource(R.string.show_next_code),
                    subtitle = stringResource(R.string.show_next_code_desc),
                    checked = settings.showNextCode,
                    onCheckedChange = actions.onShowNextCodeChange
                )
                SettingsDivider()
                ChoiceRow(
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    title = stringResource(R.string.sort_by),
                    options = SortOrder.entries,
                    selected = settings.sortOrder,
                    label = { sortLabel(it) },
                    onSelect = actions.onSortOrderChange
                )
            }

            // --- Copias de seguridad ---
            SectionTitle(stringResource(R.string.section_backup))
            SettingsCard {
                Text(
                    text = stringResource(R.string.backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)
                )
                ActionRow(
                    icon = Icons.Rounded.EnhancedEncryption,
                    title = stringResource(R.string.export_encrypted),
                    subtitle = stringResource(R.string.export_encrypted_desc),
                    enabled = !busy && accountCount > 0,
                    onClick = { actions.requireAuth { exportEncryptedLauncher.launch(backupFileName("json")) } }
                )
                SettingsDivider()
                ActionRow(
                    icon = Icons.Rounded.Description,
                    title = stringResource(R.string.export_plain),
                    subtitle = stringResource(R.string.export_plain_desc),
                    enabled = !busy && accountCount > 0,
                    onClick = { showPlainWarning = true }
                )
                SettingsDivider()
                ActionRow(
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.import_title),
                    subtitle = stringResource(R.string.import_desc),
                    enabled = !busy,
                    onClick = actions.onImport
                )
                SettingsDivider()
                ActionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = stringResource(R.string.trash_title),
                    subtitle = if (trashCount == 0) stringResource(R.string.trash_empty_title)
                    else pluralStringResource(R.plurals.trash_count, trashCount, trashCount),
                    onClick = actions.onOpenTrash
                )
                AnimatedVisibility(visible = busy) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.working), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // --- Idioma ---
            SectionTitle(stringResource(R.string.section_language))
            SettingsCard { LanguageSection(currentLanguage, actions.onLanguageSelected) }

            // --- Acerca de ---
            SectionTitle(stringResource(R.string.section_about))
            SettingsCard {
                ActionRow(
                    icon = Icons.Rounded.School,
                    title = stringResource(R.string.settings_tutorial),
                    subtitle = stringResource(R.string.settings_tutorial_desc),
                    onClick = actions.onOpenTutorial
                )
                SettingsDivider()
                AboutSection()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showPlainWarning) {
        ConfirmDialog(
            title = stringResource(R.string.export_plain_warning_title),
            message = stringResource(R.string.export_plain_warning),
            confirmLabel = stringResource(R.string.export_plain_confirm),
            danger = true,
            onDismiss = { showPlainWarning = false },
            onConfirm = {
                showPlainWarning = false
                actions.requireAuth { exportPlainLauncher.launch(backupFileName("txt")) }
            }
        )
    }
}

private fun backupFileName(extension: String): String =
    "Mis2FA_${SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(Date())}.$extension"

@Composable
private fun lockTimeoutLabel(timeout: LockTimeout): String = stringResource(
    when (timeout) {
        LockTimeout.IMMEDIATELY -> R.string.lock_immediately
        LockTimeout.ONE_MINUTE -> R.string.lock_one_minute
        LockTimeout.FIVE_MINUTES -> R.string.lock_five_minutes
    }
)

@Composable
private fun clipboardLabel(seconds: Int): String =
    if (seconds <= 0) stringResource(R.string.clipboard_never) else pluralStringResource(R.plurals.clipboard_seconds, seconds, seconds)

@Composable
private fun sortLabel(order: SortOrder): String = stringResource(
    when (order) {
        SortOrder.ISSUER -> R.string.sort_issuer
        SortOrder.ACCOUNT -> R.string.sort_account
        SortOrder.NEWEST -> R.string.sort_newest
        SortOrder.OLDEST -> R.string.sort_oldest
    }
)

// ----------------------------------------------------------------------
// Nextcloud
// ----------------------------------------------------------------------

@Composable
private fun NextcloudSection(syncState: SyncState, config: SyncConfig?, busy: Boolean, actions: SettingsActions) {
    val configured = config != null
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        val ok = configured && syncState !is SyncState.Failed
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !configured -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        ok -> AccentGreen.copy(alpha = 0.2f)
                        else -> AccentOrange.copy(alpha = 0.2f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (configured) Icons.Rounded.CloudSync else Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = when {
                    !configured -> MaterialTheme.colorScheme.onSurfaceVariant
                    ok -> AccentGreen
                    else -> AccentOrange
                },
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(if (configured) R.string.nextcloud_connected else R.string.local_mode),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = if (config != null) "${config.credentials.username} • ${config.credentials.serverUrl.removePrefix("https://")}"
                else stringResource(R.string.local_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (configured) {
                val status = when (syncState) {
                    SyncState.Syncing -> stringResource(R.string.status_syncing)
                    is SyncState.Failed -> syncErrorText(syncState.error)
                    is SyncState.Success -> stringResource(R.string.status_synced, relativeTime(syncState.timestamp))
                    else -> config.lastSync.takeIf { it > 0 }?.let { stringResource(R.string.status_synced, relativeTime(it)) }
                        ?: stringResource(R.string.status_pending)
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (syncState is SyncState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (!configured) {
        Spacer(modifier = Modifier.height(14.dp))
        ConnectForm(busy, actions)
        return
    }

    Spacer(modifier = Modifier.height(10.dp))
    if (syncState is SyncState.Failed && syncState.error == SyncError.PASSWORD_CHANGED) {
        Button(onClick = actions.onReenterPassword, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.enter_master_password))
        }
    }
    Column {
        TextButton(onClick = actions.onSyncNow, enabled = syncState !is SyncState.Syncing && !busy) {
            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sync_now))
        }
        TextButton(onClick = actions.onChangePassword, enabled = !busy && config.key != null) {
            Icon(Icons.Rounded.Password, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.change_master_password))
        }
        TextButton(onClick = { confirmDisconnect = true }, enabled = !busy) {
            Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = AccentRed)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = stringResource(R.string.disconnect_title),
            message = stringResource(R.string.disconnect_message),
            confirmLabel = stringResource(R.string.disconnect),
            danger = true,
            onDismiss = { confirmDisconnect = false },
            onConfirm = {
                confirmDisconnect = false
                actions.onDisconnect()
            }
        )
    }
}

@Composable
private fun ConnectForm(busy: Boolean, actions: SettingsActions) {
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var appPassword by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<SyncError?>(null) }
    var tried by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val missing = server.isBlank() || user.isBlank() || appPassword.isBlank() || masterPassword.isBlank()
    val masterTooShort = masterPassword.isNotEmpty() && masterPassword.length < CryptoManager.MIN_PASSWORD_LENGTH

    fun connect() {
        tried = true
        if (missing || busy) return
        error = null
        actions.onConnect(server, user, appPassword, masterPassword) { result ->
            error = result
            if (result == null) masterPassword = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HintCard(
            text = stringResource(R.string.sync_explanation),
            icon = Icons.Rounded.Lock
        )
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text(stringResource(R.string.field_server)) },
            placeholder = { Text("https://nube.ejemplo.com") },
            singleLine = true,
            enabled = !busy,
            isError = tried && server.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text(stringResource(R.string.field_user)) },
            singleLine = true,
            enabled = !busy,
            isError = tried && user.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(
            value = appPassword,
            onValueChange = { appPassword = it },
            label = stringResource(R.string.field_app_password),
            supportingText = stringResource(R.string.field_app_password_help),
            isError = tried && appPassword.isBlank(),
            imeAction = ImeAction.Next,
            enabled = !busy
        )
        PasswordField(
            value = masterPassword,
            onValueChange = { masterPassword = it },
            label = stringResource(R.string.field_master_password),
            supportingText = if (masterTooShort) pluralStringResource(R.plurals.error_password_short, CryptoManager.MIN_PASSWORD_LENGTH, CryptoManager.MIN_PASSWORD_LENGTH)
            else stringResource(R.string.field_master_password_help),
            isError = tried && masterPassword.isBlank(),
            onImeAction = ::connect,
            enabled = !busy
        )
        Text(
            stringResource(R.string.master_password_warning),
            style = MaterialTheme.typography.bodySmall,
            color = warningTextColor()
        )
        error?.let {
            Text(syncErrorText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (tried && missing) {
            Text(stringResource(R.string.error_required_fields), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = ::connect, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.connecting))
            } else {
                Text(stringResource(R.string.connect_and_encrypt))
            }
        }
        TextButton(onClick = { uriHandler.openUri(HELP_URL) }) {
            Text(stringResource(R.string.sync_help_link))
        }
    }
}

// ----------------------------------------------------------------------
// Idioma y Acerca de
// ----------------------------------------------------------------------

@Composable
private fun LanguageSection(current: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        AppLanguage.entries.forEachIndexed { index, language ->
            val label = if (language == AppLanguage.SYSTEM) stringResource(R.string.language_system) else language.nativeName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = language == current,
                        onClick = { if (language != current) onSelected(language) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == 0) SettingIcon(Icons.Rounded.Language) else Spacer(modifier = Modifier.width(26.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp)
                )
                RadioButton(selected = language == current, onClick = null)
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_logo_round),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row {
            TextButton(onClick = { uriHandler.openUri(REPO_URL) }) { Text(stringResource(R.string.source_code)) }
            TextButton(onClick = { uriHandler.openUri("https://www.gnu.org/licenses/gpl-3.0.html") }) { Text(stringResource(R.string.license)) }
        }
    }
}

private const val REPO_URL = "https://github.com/ferlagod/mis_2fa"
private const val HELP_URL = "https://github.com/ferlagod/mis_2fa#conectar-con-nextcloud"
