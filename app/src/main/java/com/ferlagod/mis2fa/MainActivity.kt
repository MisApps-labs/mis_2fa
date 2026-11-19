// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ferlagod.mis2fa.data.local.ThemeMode
import com.ferlagod.mis2fa.data.sync.SyncError
import com.ferlagod.mis2fa.model.Token
import com.ferlagod.mis2fa.otp.GoogleMigration
import com.ferlagod.mis2fa.otp.OtpAuthUri
import com.ferlagod.mis2fa.ui.components.syncErrorRes
import com.ferlagod.mis2fa.ui.screen.AppLockScreen
import com.ferlagod.mis2fa.ui.screen.ConfirmDialog
import com.ferlagod.mis2fa.ui.screen.EditTokenScreen
import com.ferlagod.mis2fa.ui.screen.HomeActions
import com.ferlagod.mis2fa.ui.screen.HomeScreen
import com.ferlagod.mis2fa.ui.screen.OnboardingScreen
import com.ferlagod.mis2fa.ui.screen.PasswordDialog
import com.ferlagod.mis2fa.ui.screen.ScanScreen
import com.ferlagod.mis2fa.ui.screen.SettingsActions
import com.ferlagod.mis2fa.ui.screen.SettingsScreen
import com.ferlagod.mis2fa.ui.screen.TokenQrDialog
import com.ferlagod.mis2fa.ui.screen.TrashScreen
import com.ferlagod.mis2fa.ui.theme.Mis2FATheme
import com.ferlagod.mis2fa.ui.viewmodel.MainViewModel
import com.ferlagod.mis2fa.ui.viewmodel.MainViewModelFactory
import com.ferlagod.mis2fa.ui.viewmodel.ScanOutcome
import com.ferlagod.mis2fa.ui.viewmodel.UiEvent
import com.ferlagod.mis2fa.util.AppLanguage
import com.ferlagod.mis2fa.util.AppLock
import com.ferlagod.mis2fa.util.LanguageManager
import com.ferlagod.mis2fa.util.SecureClipboard
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val SETTINGS = "settings"
    const val TRASH = "trash"
    const val TUTORIAL = "tutorial"
    const val EDIT = "edit?id={id}"

    fun edit(id: String? = null) = "edit?id=${id.orEmpty()}"
}

/** Algo pedido desde fuera (acceso directo, enlace) que se hace al desbloquear. */
sealed interface PendingAction {
    data object Scan : PendingAction
    data class Link(val uri: String) : PendingAction
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    /** La app está tapada por la pantalla de bloqueo (huella/PIN). */
    private var appLocked by mutableStateOf(false)
    private var pendingAction by mutableStateOf<PendingAction?>(null)
    private var authInProgress = false
    private var backgroundSince = 0L
    private var pendingAuthCallback: ((Boolean) -> Unit)? = null

    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onAuthResult(result.resultCode == Activity.RESULT_OK)
    }

    override fun attachBaseContext(newBase: Context) {
        // Android 8-12: aplica el idioma elegido en Ajustes (en 13+ lo hace el sistema).
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyScreenPrivacy(viewModel.settings.value.blockScreenshots)
        // Ningún campo de la app debe acabar en un gestor de contraseñas (claves secretas,
        // contraseña maestra, contraseñas de copias): se excluye todo del autocompletado.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        // Ignora toques cuando otra app dibuja encima (tapjacking). Android 12+ ya lo hace por
        // defecto con las capas no fiables; esto protege también Android 8-11.
        window.decorView.filterTouchesWhenObscured = true

        appLocked = savedInstanceState?.getBoolean(STATE_LOCKED) ?: lockEnabled()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(settings.blockScreenshots) { applyScreenPrivacy(settings.blockScreenshots) }

            Mis2FATheme(darkTheme = darkTheme, amoled = settings.amoled) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!settings.onboardingDone) {
                            // Primer arranque: tutorial y después el inicio vacío. Lo que llegue
                            // mientras (un enlace otpauth://) espera en pendingAction.
                            OnboardingScreen(onFinish = viewModel::finishOnboarding)
                        } else AppNavigation(
                            viewModel = viewModel,
                            appLocked = appLocked,
                            pendingAction = pendingAction,
                            onPendingActionConsumed = { pendingAction = null },
                            currentLanguage = LanguageManager.current(this@MainActivity),
                            onLanguageSelected = { LanguageManager.set(this@MainActivity, it) },
                            isAppLockAvailable = AppLock.isAvailable(this@MainActivity),
                            requireAuth = { onGranted -> requestUnlock { ok -> if (ok) onGranted() } },
                            onAppLockChange = { enabled ->
                                // Activarlo (y desactivarlo) exige autenticarse.
                                requestUnlock { ok -> if (ok) viewModel.setAppLock(enabled) }
                            }
                        )
                        AnimatedVisibility(visible = appLocked, enter = fadeIn(), exit = fadeOut()) {
                            AppLockScreen(onUnlock = { requestUnlock() })
                        }
                    }
                }
            }
        }
    }

    private fun lockEnabled() = viewModel.settings.value.appLock && AppLock.isAvailable(this)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val timeout = viewModel.settings.value.lockTimeout.millis
        if (lockEnabled() && backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince >= timeout) {
            appLocked = true
        }
        backgroundSince = 0
        if (appLocked && lockEnabled()) requestUnlock() else appLocked = false
        // Trae los cambios de otros dispositivos al volver a la app.
        viewModel.syncNow()
    }

    override fun onStop() {
        super.onStop()
        if (!authInProgress) {
            backgroundSince = SystemClock.elapsedRealtime()
            // Bloqueo inmediato: se tapa ya, para que no se vea nada al volver.
            if (lockEnabled() && viewModel.settings.value.lockTimeout.millis == 0L) appLocked = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCKED, appLocked)
    }

    /**
     * Pide huella/PIN. Si el dispositivo no tiene bloqueo de pantalla, se concede directamente
     * (no hay con qué comprobarlo).
     */
    private fun requestUnlock(callback: ((Boolean) -> Unit)? = null) {
        if (authInProgress) return
        authInProgress = true
        pendingAuthCallback = callback
        val intent = AppLock.authenticate(
            activity = this,
            title = getString(R.string.app_lock_prompt_title),
            subtitle = getString(R.string.app_lock_prompt_subtitle),
            onResult = ::onAuthResult
        )
        if (intent != null) confirmCredential.launch(intent)
    }

    private fun onAuthResult(success: Boolean) {
        authInProgress = false
        if (success) appLocked = false
        pendingAuthCallback?.invoke(success)
        pendingAuthCallback = null
    }

    /** Impide capturas y grabaciones, y oculta el contenido en "Recientes". */
    private fun applyScreenPrivacy(enabled: Boolean) {
        if (enabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!enabled)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_SCAN -> pendingAction = PendingAction.Scan
            Intent.ACTION_VIEW -> intent.data?.toString()?.let { pendingAction = PendingAction.Link(it) }
        }
        intent.action = null
        intent.data = null
    }

    companion object {
        const val ACTION_SCAN = "com.ferlagod.mis2fa.action.SCAN"
        private const val STATE_LOCKED = "app_locked"
    }
}

@Composable
private fun AppNavigation(
    viewModel: MainViewModel,
    appLocked: Boolean,
    pendingAction: PendingAction?,
    onPendingActionConsumed: () -> Unit,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    isAppLockAvailable: Boolean,
    requireAuth: (onGranted: () -> Unit) -> Unit,
    onAppLockChange: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tokens by viewModel.visibleTokens.collectAsStateWithLifecycle()
    val activeTokens by viewModel.activeTokens.collectAsStateWithLifecycle()
    val trashed by viewModel.trashedTokens.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingEncryptedImport.collectAsStateWithLifecycle()

    // Diálogos
    var qrToken by remember { mutableStateOf<Token?>(null) }
    var showReenterPassword by rememberSaveable { mutableStateOf(false) }
    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    var pendingExportUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var linkToConfirm by rememberSaveable { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importFromImage(uri) {
            if (navController.currentDestination?.route == Routes.SCAN) navController.popBackStack()
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri)
    }
    fun launchPickImage() = pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun launchImport() = importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))

    // Avisos del ViewModel. Cada uno sustituye al anterior en lugar de esperar a que termine.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                when (event) {
                    is UiEvent.Text -> snackbarHostState.showSnackbar(
                        resources.getString(event.res, *event.args.toTypedArray()),
                        withDismissAction = true
                    )
                    is UiEvent.Plural -> snackbarHostState.showSnackbar(
                        resources.getQuantityString(event.res, event.count, event.count),
                        withDismissAction = true
                    )
                    is UiEvent.Trashed -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getQuantityString(R.plurals.moved_to_trash, event.ids.size, event.ids.size),
                            actionLabel = resources.getString(R.string.undo),
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(event.ids)
                    }
                }
            }
        }
    }

    // Accesos directos y enlaces otpauth://, cuando la app está desbloqueada.
    LaunchedEffect(pendingAction, appLocked) {
        val action = pendingAction ?: return@LaunchedEffect
        if (appLocked) return@LaunchedEffect
        onPendingActionConsumed()
        when (action) {
            PendingAction.Scan -> navController.navigate(Routes.SCAN) { launchSingleTop = true }
            // Un enlace de otra app o web se confirma antes de añadirlo: podría no ser tuyo.
            is PendingAction.Link -> linkToConfirm = action.uri
        }
    }

    fun copy(token: Token, code: String) {
        SecureClipboard.copy(context, token.displayName, code, settings.clipboardClearSeconds)
        if (!SecureClipboard.systemShowsCopyConfirmation) {
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.code_copied)) }
        }
    }

    fun NavHostController.backToHome(outcome: ScanOutcome) {
        viewModel.reportScan(outcome)
        popBackStack(Routes.HOME, inclusive = false)
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                tokens = tokens,
                totalCount = activeTokens.size,
                searchQuery = searchQuery,
                settings = settings,
                syncState = syncState,
                isAppLockAvailable = isAppLockAvailable,
                loadFailed = loadFailed,
                locked = appLocked,
                snackbarHostState = snackbarHostState,
                actions = HomeActions(
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onCopy = ::copy,
                    onIncrementCounter = viewModel::incrementCounter,
                    onTogglePin = viewModel::togglePin,
                    onEdit = { id -> navController.navigate(Routes.edit(id)) },
                    onShowQr = { token -> requireAuth { qrToken = token } },
                    onTrash = { id -> viewModel.trash(listOf(id)) },
                    onScan = { navController.navigate(Routes.SCAN) },
                    onPickImage = ::launchPickImage,
                    onAddManually = { navController.navigate(Routes.edit()) },
                    onImport = ::launchImport,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onSync = viewModel::syncNow,
                    onEnableAppLock = { onAppLockChange(true) },
                    onReenterPassword = { passwordError = null; showReenterPassword = true },
                    onDismissHint = viewModel::dismissHint
                )
            )
        }

        composable(Routes.SCAN) {
            ScanScreen(
                onBack = { navController.popBackStack() },
                onScanned = viewModel::handleScanned,
                onFinished = { outcome -> navController.backToHome(outcome) },
                onPickImage = ::launchPickImage,
                onManual = {
                    navController.navigate(Routes.edit()) { popUpTo(Routes.HOME) }
                }
            )
        }

        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() }
            val existing = remember(id) { id?.let(viewModel::findToken) }
            EditTokenScreen(
                existing = existing,
                onBack = { navController.popBackStack() },
                onSave = { token ->
                    viewModel.saveToken(token, isNew = existing == null) { navController.popBackStack(Routes.HOME, inclusive = false) }
                },
                onRevealSecret = requireAuth
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                syncState = syncState,
                syncConfig = remember(syncState) { viewModel.syncConfig() },
                trashCount = trashed.size,
                accountCount = activeTokens.size,
                busy = busy,
                isAppLockAvailable = isAppLockAvailable,
                currentLanguage = currentLanguage,
                snackbarHostState = snackbarHostState,
                actions = SettingsActions(
                    onBack = { navController.popBackStack() },
                    onThemeChange = viewModel::setThemeMode,
                    onAmoledChange = viewModel::setAmoled,
                    onSortOrderChange = viewModel::setSortOrder,
                    onShowAccountNameChange = viewModel::setShowAccountName,
                    onShowNextCodeChange = viewModel::setShowNextCode,
                    onAppLockChange = onAppLockChange,
                    onLockTimeoutChange = viewModel::setLockTimeout,
                    onBlockScreenshotsChange = viewModel::setBlockScreenshots,
                    onHideCodesChange = viewModel::setHideCodes,
                    onClipboardClearChange = viewModel::setClipboardClearSeconds,
                    onConnect = { server, user, appPassword, master, onResult ->
                        viewModel.connect(server, user, appPassword, master, onResult)
                    },
                    onReenterPassword = { passwordError = null; showReenterPassword = true },
                    onChangePassword = { passwordError = null; showChangePassword = true },
                    onDisconnect = viewModel::disconnect,
                    onSyncNow = viewModel::syncNow,
                    onExportEncrypted = { uri -> passwordError = null; pendingExportUri = uri },
                    onExportPlain = viewModel::exportPlain,
                    requireAuth = requireAuth,
                    onImport = ::launchImport,
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                    onOpenTutorial = { navController.navigate(Routes.TUTORIAL) },
                    onLanguageSelected = onLanguageSelected
                )
            )
        }

        // El mismo tutorial del primer arranque, abierto desde Ajustes.
        composable(Routes.TUTORIAL) {
            OnboardingScreen(onFinish = { navController.popBackStack() })
        }

        composable(Routes.TRASH) {
            TrashScreen(
                tokens = trashed,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onRestore = { id -> viewModel.restore(listOf(id)) },
                onDelete = { id -> viewModel.deletePermanently(listOf(id)) },
                onEmpty = viewModel::emptyTrash
            )
        }
    }

    // ------------------------------------------------------------------
    // Diálogos
    // ------------------------------------------------------------------

    // Los diálogos son ventanas aparte: con la app bloqueada no se muestran.
    if (appLocked) return

    qrToken?.let { TokenQrDialog(it) { qrToken = null } }

    linkToConfirm?.let { link ->
        val summary = remember(link) { LinkSummary.of(link) }
        if (summary == null) {
            LaunchedEffect(link) {
                linkToConfirm = null
                viewModel.reportScan(ScanOutcome.NotOtp)
            }
        } else {
            ConfirmDialog(
                title = stringResource(R.string.link_add_title),
                message = when (summary) {
                    is LinkSummary.Single -> stringResource(R.string.link_add_message, summary.name)
                    is LinkSummary.Batch -> pluralStringResource(R.plurals.link_add_batch, summary.count, summary.count)
                },
                confirmLabel = stringResource(R.string.add_account),
                onDismiss = { linkToConfirm = null },
                onConfirm = {
                    linkToConfirm = null
                    viewModel.handleExternalLink(link)
                }
            )
        }
    }

    if (showReenterPassword) {
        val wrongPassword = stringResource(R.string.sync_error_wrong_password)
        PasswordDialog(
            title = stringResource(R.string.enter_master_password),
            message = stringResource(R.string.reenter_password_message),
            confirmLabel = stringResource(R.string.unlock),
            busy = busy,
            error = passwordError,
            onDismiss = { showReenterPassword = false },
            onConfirm = { password ->
                viewModel.reenterMasterPassword(password) { error ->
                    if (error == null || error == SyncError.NETWORK) showReenterPassword = false
                    else passwordError = if (error == SyncError.WRONG_PASSWORD) wrongPassword else resources.getString(syncErrorRes(error))
                }
            }
        )
    }

    if (showChangePassword) {
        PasswordDialog(
            title = stringResource(R.string.change_master_password),
            message = stringResource(R.string.change_password_message),
            confirmLabel = stringResource(R.string.change),
            confirm = true,
            busy = busy,
            error = passwordError,
            warning = stringResource(R.string.master_password_warning),
            onDismiss = { showChangePassword = false },
            onConfirm = { password ->
                viewModel.changeMasterPassword(password) { error ->
                    if (error == null) {
                        showChangePassword = false
                        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.password_changed)) }
                    } else {
                        passwordError = resources.getString(syncErrorRes(error))
                    }
                }
            }
        )
    }

    pendingExportUri?.let { uri ->
        PasswordDialog(
            title = stringResource(R.string.export_encrypted),
            message = stringResource(R.string.export_password_message),
            confirmLabel = stringResource(R.string.export),
            confirm = true,
            busy = busy,
            onDismiss = {
                pendingExportUri = null
                // El fichero ya se creó vacío al elegirlo: se borra para no dejar basura.
                runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            },
            onConfirm = { password ->
                pendingExportUri = null
                viewModel.exportEncrypted(uri, password)
            }
        )
    }

    if (pendingImport != null) {
        val wrongPassword = stringResource(R.string.error_wrong_password)
        PasswordDialog(
            title = stringResource(R.string.import_encrypted_title),
            message = stringResource(R.string.import_encrypted_message),
            confirmLabel = stringResource(R.string.import_action),
            busy = busy,
            error = passwordError,
            onDismiss = {
                passwordError = null
                viewModel.cancelEncryptedImport()
            },
            onConfirm = { password ->
                passwordError = null
                viewModel.importEncrypted(password) { passwordError = wrongPassword }
            }
        )
    }
}

/** Resumen de un enlace externo para confirmar antes de añadirlo. */
private sealed interface LinkSummary {
    data class Single(val name: String) : LinkSummary
    data class Batch(val count: Int) : LinkSummary

    companion object {
        fun of(link: String): LinkSummary? =
            OtpAuthUri.parseOrNull(link)?.let { Single(it.displayName) }
                ?: runCatching { GoogleMigration.parse(link).tokens.size }.getOrNull()?.takeIf { it > 0 }?.let { Batch(it) }
    }
}
