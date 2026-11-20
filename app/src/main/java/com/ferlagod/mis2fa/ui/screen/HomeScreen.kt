// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.ui.screen

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ferlagod.mis2fa.R
import com.ferlagod.mis2fa.data.local.AppSettingsState
import com.ferlagod.mis2fa.data.local.Hints
import com.ferlagod.mis2fa.data.sync.SyncError
import com.ferlagod.mis2fa.data.sync.SyncState
import com.ferlagod.mis2fa.model.OtpType
import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.otp.Base32
import com.ferlagod.mis2fa.otp.OtpGenerator
import com.ferlagod.mis2fa.ui.components.CountdownRing
import com.ferlagod.mis2fa.ui.components.EmptyState
import com.ferlagod.mis2fa.ui.components.HintCard
import com.ferlagod.mis2fa.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mis2fa.ui.components.SingleLine
import com.ferlagod.mis2fa.ui.components.TokenAvatar
import com.ferlagod.mis2fa.ui.components.syncErrorText
import com.ferlagod.mis2fa.ui.theme.AccentOrange
import com.ferlagod.mis2fa.ui.theme.AccentRed
import com.ferlagod.mis2fa.ui.theme.CodeTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Acciones de la pantalla principal. */
class HomeActions(
    val onSearchQueryChange: (String) -> Unit,
    val onCopy: (token: Token, code: String) -> Unit,
    val onIncrementCounter: (String) -> Unit,
    val onTogglePin: (String) -> Unit,
    val onEdit: (String) -> Unit,
    val onShowQr: (Token) -> Unit,
    val onTrash: (String) -> Unit,
    val onScan: () -> Unit,
    val onPickImage: () -> Unit,
    val onAddManually: () -> Unit,
    val onImport: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onSync: () -> Unit,
    val onEnableAppLock: () -> Unit,
    val onReenterPassword: () -> Unit,
    val onDismissHint: (String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tokens: List<Token>,
    totalCount: Int,
    searchQuery: String,
    settings: AppSettingsState,
    syncState: SyncState,
    isAppLockAvailable: Boolean,
    loadFailed: Boolean,
    locked: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: HomeActions
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var menuTokenId by rememberSaveable { mutableStateOf<String?>(null) }
    // Códigos destapados temporalmente cuando "Ocultar códigos" está activado.
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()

    // Reloj: cada 100 ms para los anillos; los códigos solo se recalculan al cambiar de periodo.
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            delay(100)
        }
    }

    fun reveal(id: String) {
        revealed = revealed + id
        scope.launch {
            delay(REVEAL_MS)
            revealed = revealed - id
        }
    }

    val syncing = syncState is SyncState.Syncing
    val syncConfigured = syncState !is SyncState.NotConfigured

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    // Logo discreto junto al nombre; el mismo título se usa plegado y desplegado.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo_round),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (syncConfigured) SyncStatusButton(syncState, onClick = actions.onSync, onError = actions.onOpenSettings)
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (totalCount > 0 && !loadFailed) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_account)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    expanded = !listState.canScrollBackward
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncing,
            // Sin Nextcloud no hay nada que refrescar: el gesto vuelve sin hacer nada.
            onRefresh = { if (syncConfigured) actions.onSync() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (totalCount == 0 && !loadFailed) {
                WelcomeContent(actions, syncConfigured)
                return@PullToRefreshBox
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "banners") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.widthIn(max = READABLE_MAX_WIDTH)
                    ) {
                        Banners(
                            totalCount = totalCount,
                            settings = settings,
                            syncState = syncState,
                            isAppLockAvailable = isAppLockAvailable,
                            loadFailed = loadFailed,
                            actions = actions
                        )
                        if (totalCount > 3) SearchField(searchQuery, actions.onSearchQueryChange)
                    }
                }
                if (tokens.isEmpty() && searchQuery.isNotBlank()) {
                    item(key = "no_results") {
                        EmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = stringResource(R.string.search_no_results),
                            modifier = Modifier.padding(top = 48.dp)
                        )
                    }
                }
                items(tokens, key = { it.id }) { token ->
                    TokenCard(
                        token = token,
                        now = now,
                        settings = settings,
                        hidden = settings.hideCodes && token.id !in revealed,
                        onClick = { code ->
                            if (settings.hideCodes) reveal(token.id)
                            actions.onCopy(token, code)
                        },
                        onLongClick = { menuTokenId = token.id },
                        onIncrementCounter = { actions.onIncrementCounter(token.id) },
                        modifier = Modifier
                            .widthIn(max = READABLE_MAX_WIDTH)
                            .animateItem()
                    )
                }
            }
        }
    }

    // Las hojas son ventanas aparte: con la app bloqueada se verían por encima del bloqueo.
    if (locked) return

    if (showAddSheet) {
        AddAccountSheet(
            onDismiss = { showAddSheet = false },
            onScan = { showAddSheet = false; actions.onScan() },
            onPickImage = { showAddSheet = false; actions.onPickImage() },
            onAddManually = { showAddSheet = false; actions.onAddManually() },
            onImport = { showAddSheet = false; actions.onImport() }
        )
    }

    val menuToken = menuTokenId?.let { id -> tokens.firstOrNull { it.id == id } }
    if (menuToken != null) {
        TokenActionsSheet(
            token = menuToken,
            now = now,
            onDismiss = { menuTokenId = null },
            onCopy = { code -> menuTokenId = null; actions.onCopy(menuToken, code) },
            onTogglePin = { menuTokenId = null; actions.onTogglePin(menuToken.id) },
            onEdit = { menuTokenId = null; actions.onEdit(menuToken.id) },
            onShowQr = { menuTokenId = null; actions.onShowQr(menuToken) },
            onTrash = { menuTokenId = null; actions.onTrash(menuToken.id) }
        )
    } else if (menuTokenId != null) {
        menuTokenId = null
    }
}

private const val REVEAL_MS = 15_000L

@Composable
private fun SyncStatusButton(state: SyncState, onClick: () -> Unit, onError: () -> Unit) {
    when (state) {
        SyncState.Syncing -> Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        is SyncState.Failed -> IconButton(onClick = onError) {
            Icon(Icons.Rounded.CloudOff, contentDescription = stringResource(R.string.cd_sync_error), tint = AccentRed)
        }
        else -> IconButton(onClick = onClick) {
            Icon(Icons.Rounded.CloudDone, contentDescription = stringResource(R.string.cd_sync_now), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Banners(
    totalCount: Int,
    settings: AppSettingsState,
    syncState: SyncState,
    isAppLockAvailable: Boolean,
    loadFailed: Boolean,
    actions: HomeActions
) {
    if (loadFailed) {
        HintCard(
            text = stringResource(R.string.error_vault_unreadable_long),
            icon = Icons.Rounded.ErrorOutline,
            tint = AccentRed
        )
        return
    }
    if (syncState is SyncState.Failed) {
        if (syncState.error == SyncError.PASSWORD_CHANGED) {
            HintCard(
                text = syncErrorText(syncState.error),
                icon = Icons.Rounded.Lock,
                tint = AccentOrange,
                actionLabel = stringResource(R.string.enter_master_password),
                onAction = actions.onReenterPassword
            )
        } else if (syncState.error != SyncError.NETWORK) {
            HintCard(
                text = syncErrorText(syncState.error),
                icon = Icons.Rounded.CloudOff,
                tint = AccentOrange,
                actionLabel = stringResource(R.string.review_settings),
                onAction = actions.onOpenSettings
            )
        }
    }
    // Los consejos se muestran de uno en uno, del más importante al menos, para no saturar.
    when {
        syncState is SyncState.NotConfigured && settings.showHint(Hints.SYNC) -> HintCard(
            text = stringResource(R.string.hint_sync),
            icon = Icons.Rounded.CloudSync,
            actionLabel = stringResource(R.string.hint_sync_action),
            onAction = actions.onOpenSettings,
            onDismiss = { actions.onDismissHint(Hints.SYNC) }
        )
        !settings.appLock && isAppLockAvailable && settings.showHint(Hints.APP_LOCK) -> HintCard(
            text = stringResource(R.string.hint_app_lock),
            icon = Icons.Rounded.Fingerprint,
            actionLabel = stringResource(R.string.hint_app_lock_action),
            onAction = actions.onEnableAppLock,
            onDismiss = { actions.onDismissHint(Hints.APP_LOCK) }
        )
        totalCount > 0 && settings.showHint(Hints.GESTURES) -> HintCard(
            text = stringResource(R.string.hint_gestures),
            icon = Icons.Rounded.TouchApp,
            onDismiss = { actions.onDismissHint(Hints.GESTURES) }
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.SearchOff, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TokenCard(
    token: Token,
    now: State<Long>,
    settings: AppSettingsState,
    hidden: Boolean,
    onClick: (code: String) -> Unit,
    onLongClick: () -> Unit,
    onIncrementCounter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    // Secreto ilegible (fichero dañado o editado a mano): no se inventa un código.
    val key = remember(token.secret) { Base32.decodeOrNull(token.secret)?.takeIf { it.isNotEmpty() } }
    if (key == null) {
        InvalidTokenCard(token, onLongClick, modifier)
        return
    }
    val isTimeBased = token.type != OtpType.HOTP
    // Solo cambia cuando empieza un periodo nuevo: los códigos no se recalculan 10 veces por segundo.
    val step by remember(token.period, token.counter, isTimeBased) {
        derivedStateOf { if (isTimeBased) OtpGenerator.timeStep(now.value, token.period) else token.counter }
    }
    val expiring by remember(token.period, isTimeBased) {
        derivedStateOf { isTimeBased && OtpGenerator.millisRemaining(now.value, token.period) <= 5_000 }
    }
    val code = remember(token, step) { OtpGenerator.code(token, step * token.period * 1000L, key) }
    val nextCode = remember(token, step) {
        if (isTimeBased) OtpGenerator.nextCode(token, step * token.period * 1000L, key) else null
    }
    val codeColor by animateColorAsState(
        if (expiring) AccentRed else MaterialTheme.colorScheme.primary,
        label = "codeColor"
    )
    val copyLabel = stringResource(R.string.cd_copy_code)
    val moreLabel = stringResource(R.string.cd_more_options)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${token.displayName}, ${if (hidden) "" else code.toList().joinToString(" ")}"
                onLongClick(label = moreLabel) { onLongClick(); true }
            }
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClickLabel = copyLabel,
                    onClick = { onClick(code) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TokenAvatar(token.displayName)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SingleLine(
                        text = token.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (token.pinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Rounded.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
                if (settings.showAccountName && token.account.isNotBlank() && token.issuer.isNotBlank()) {
                    SingleLine(
                        text = token.account,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (hidden) maskFor(code) else OtpGenerator.format(code),
                    style = CodeTextStyle,
                    color = codeColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (isTimeBased) {
                    CountdownRing(period = token.period, now = now)
                    if (settings.showNextCode && nextCode != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (hidden) maskFor(nextCode) else OtpGenerator.format(nextCode),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(onClick = onIncrementCounter) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.cd_next_hotp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun maskFor(code: String): String = OtpGenerator.format("•".repeat(code.length))

/** Cuenta cuyo secreto no se puede leer: se muestra el error y al tocarla se abre su menú. */
@Composable
private fun InvalidTokenCard(token: Token, onOpenMenu: () -> Unit, modifier: Modifier = Modifier) {
    val message = stringResource(R.string.code_invalid_secret)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onOpenMenu,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "${token.displayName}, $message" }
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TokenAvatar(token.displayName)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                SingleLine(token.displayName, MaterialTheme.typography.titleSmall, MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun WelcomeContent(actions: HomeActions, syncConfigured: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_round),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = actions.onScan, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_scan_qr))
        }
        Spacer(modifier = Modifier.height(10.dp))
        FilledTonalButton(onClick = actions.onAddManually, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)) {
            Icon(Icons.Rounded.Keyboard, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_manual))
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = actions.onImport, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)) {
            Icon(Icons.Rounded.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_import))
        }
        if (!syncConfigured) {
            Spacer(modifier = Modifier.height(24.dp))
            HintCard(
                text = stringResource(R.string.welcome_sync_hint),
                icon = Icons.AutoMirrored.Rounded.Login,
                actionLabel = stringResource(R.string.hint_sync_action),
                onAction = actions.onOpenSettings,
                modifier = Modifier.widthIn(max = 360.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onPickImage: () -> Unit,
    onAddManually: () -> Unit,
    onImport: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.add_account),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            SheetItem(Icons.Rounded.QrCodeScanner, stringResource(R.string.add_scan_qr), stringResource(R.string.add_scan_qr_desc), onScan)
            SheetItem(Icons.Rounded.Image, stringResource(R.string.add_from_image), stringResource(R.string.add_from_image_desc), onPickImage)
            SheetItem(Icons.Rounded.Keyboard, stringResource(R.string.add_manual), stringResource(R.string.add_manual_desc), onAddManually)
            SheetItem(Icons.Rounded.FileOpen, stringResource(R.string.add_import), stringResource(R.string.add_import_desc), onImport)
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, danger: Boolean = false) {
    val tint = if (danger) AccentRed else MaterialTheme.colorScheme.primary
    ListItem(
        headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenActionsSheet(
    token: Token,
    now: State<Long>,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onShowQr: () -> Unit,
    onTrash: () -> Unit
) {
    val key = remember(token.secret) { Base32.decodeOrNull(token.secret)?.takeIf { it.isNotEmpty() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                TokenAvatar(token.displayName, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    SingleLine(token.displayName, MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.onSurface)
                    if (token.account.isNotBlank() && token.issuer.isNotBlank()) {
                        SingleLine(token.account, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (key != null) {
                SheetItem(Icons.Rounded.ContentCopy, stringResource(R.string.action_copy), null, { onCopy(OtpGenerator.code(token, now.value, key)) })
                if (token.type != OtpType.HOTP) {
                    SheetItem(Icons.Rounded.ContentCopy, stringResource(R.string.action_copy_next), null, { onCopy(OtpGenerator.nextCode(token, now.value, key)) })
                }
            }
            SheetItem(
                if (token.pinned) Icons.Outlined.PushPin else Icons.Rounded.PushPin,
                stringResource(if (token.pinned) R.string.action_unpin else R.string.action_pin),
                null,
                onTogglePin
            )
            SheetItem(Icons.Rounded.Edit, stringResource(R.string.action_edit), null, onEdit)
            SheetItem(Icons.Rounded.QrCode2, stringResource(R.string.action_show_qr), stringResource(R.string.action_show_qr_desc), onShowQr)
            SheetItem(Icons.Rounded.Delete, stringResource(R.string.action_trash), null, onTrash, danger = true)
        }
    }
}
