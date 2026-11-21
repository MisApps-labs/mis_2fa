// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tema de la interfaz. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Orden de las cuentas (las fijadas siempre van primero). */
enum class SortOrder { ISSUER, ACCOUNT, NEWEST, OLDEST }

/** Tiempo en segundo plano tras el que se vuelve a pedir el desbloqueo. */
enum class LockTimeout(val millis: Long) {
    IMMEDIATELY(0L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L)
}

/** Avisos de ayuda que se muestran una sola vez (hasta que el usuario los cierra). */
object Hints {
    /** En el inicio, sin Nextcloud: los códigos solo están en este dispositivo. */
    const val SYNC = "sync"
    /** En el inicio: tocar un código lo copia, mantenerlo pulsado muestra más opciones. */
    const val GESTURES = "gestures"
    /** En el inicio, sin bloqueo: recomendar activarlo. */
    const val APP_LOCK = "app_lock"
}

/** Preferencias de la app que no son secretas. */
data class AppSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** En modo oscuro, negro puro en lugar de gris (pantallas OLED). */
    val amoled: Boolean = false,
    val sortOrder: SortOrder = SortOrder.ISSUER,
    val appLock: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE,
    /** Impide capturas de pantalla y oculta la app en "Recientes". */
    val blockScreenshots: Boolean = true,
    /** Los códigos se muestran como "••• •••" hasta tocarlos. */
    val hideCodes: Boolean = false,
    /** Muestra debajo el código del siguiente periodo (útil cuando al actual le quedan segundos). */
    val showNextCode: Boolean = true,
    /** Muestra el nombre de la cuenta debajo del emisor. */
    val showAccountName: Boolean = true,
    /** Segundos tras los que se borra el portapapeles (0 = nunca). */
    val clipboardClearSeconds: Int = 30,
    /** Avisos de [Hints] que el usuario ya ha cerrado. */
    val dismissedHints: Set<String> = emptySet(),
    /** El tutorial del primer arranque ya se ha visto (o saltado). */
    val onboardingDone: Boolean = false
) {
    fun showHint(key: String) = key !in dismissedHints
}

/**
 * Preferencias no sensibles en SharedPreferences normales, expuestas como [StateFlow]
 * para que la interfaz reaccione al instante. Comparte fichero con el idioma.
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    private fun read() = AppSettingsState(
        themeMode = enumOrDefault(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM),
        amoled = prefs.getBoolean(KEY_AMOLED, false),
        sortOrder = enumOrDefault(prefs.getString(KEY_SORT, null), SortOrder.ISSUER),
        appLock = prefs.getBoolean(KEY_APP_LOCK, false),
        lockTimeout = enumOrDefault(prefs.getString(KEY_LOCK_TIMEOUT, null), LockTimeout.ONE_MINUTE),
        blockScreenshots = prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, true),
        hideCodes = prefs.getBoolean(KEY_HIDE_CODES, false),
        showNextCode = prefs.getBoolean(KEY_SHOW_NEXT, true),
        showAccountName = prefs.getBoolean(KEY_SHOW_ACCOUNT, true),
        clipboardClearSeconds = prefs.getInt(KEY_CLIPBOARD_CLEAR, 30),
        dismissedHints = prefs.getStringSet(KEY_DISMISSED_HINTS, null)?.toSet().orEmpty(),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    )

    private fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _state.value = read()
    }

    fun setThemeMode(mode: ThemeMode) = update { putString(KEY_THEME, mode.name) }
    fun setAmoled(enabled: Boolean) = update { putBoolean(KEY_AMOLED, enabled) }
    fun setSortOrder(order: SortOrder) = update { putString(KEY_SORT, order.name) }
    fun setAppLock(enabled: Boolean) = update { putBoolean(KEY_APP_LOCK, enabled) }
    fun setLockTimeout(timeout: LockTimeout) = update { putString(KEY_LOCK_TIMEOUT, timeout.name) }
    fun setBlockScreenshots(enabled: Boolean) = update { putBoolean(KEY_BLOCK_SCREENSHOTS, enabled) }
    fun setHideCodes(enabled: Boolean) = update { putBoolean(KEY_HIDE_CODES, enabled) }
    fun setShowNextCode(enabled: Boolean) = update { putBoolean(KEY_SHOW_NEXT, enabled) }
    fun setShowAccountName(enabled: Boolean) = update { putBoolean(KEY_SHOW_ACCOUNT, enabled) }
    fun setClipboardClearSeconds(seconds: Int) = update { putInt(KEY_CLIPBOARD_CLEAR, seconds) }

    fun setOnboardingDone() {
        if (!_state.value.onboardingDone) update { putBoolean(KEY_ONBOARDING_DONE, true) }
    }

    fun dismissHint(key: String) {
        if (key in _state.value.dismissedHints) return
        update { putStringSet(KEY_DISMISSED_HINTS, _state.value.dismissedHints + key) }
    }

    companion object {
        private const val PREFS = "mis2fa_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_SORT = "sort_order"
        private const val KEY_APP_LOCK = "app_lock"
        private const val KEY_LOCK_TIMEOUT = "lock_timeout"
        private const val KEY_BLOCK_SCREENSHOTS = "block_screenshots"
        private const val KEY_HIDE_CODES = "hide_codes"
        private const val KEY_SHOW_NEXT = "show_next_code"
        private const val KEY_SHOW_ACCOUNT = "show_account_name"
        private const val KEY_CLIPBOARD_CLEAR = "clipboard_clear_seconds"
        private const val KEY_DISMISSED_HINTS = "dismissed_hints"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"

        val CLIPBOARD_CLEAR_OPTIONS = listOf(15, 30, 60, 0)

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context).also { INSTANCE = it }
            }

        private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
    }
}
