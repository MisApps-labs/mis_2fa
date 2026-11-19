// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueContainer,
    onPrimaryContainer = BrandBlueOnContainer,
    secondary = SecondaryLight,
    onSecondary = Color.White,
    // Botones segmentados seleccionados y botones tonales: azul de la marca, no menta.
    secondaryContainer = BrandBlueContainer,
    onSecondaryContainer = BrandBlueOnContainer,
    tertiary = TertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE3F8),
    onTertiaryContainer = Color(0xFF2A1748),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerHighest = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = TextTertiaryLight,
    // Rojo más oscuro para el texto de error: contraste 5,6:1 sobre blanco.
    error = Color(0xFFC62828),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlueLight,
    onPrimary = Color(0xFF14243A),
    primaryContainer = BrandBlueDarkContainer,
    onPrimaryContainer = Color.White,
    secondary = SecondaryDark,
    onSecondary = Color(0xFF07291B),
    secondaryContainer = BrandBlueDarkContainer,
    onSecondaryContainer = Color.White,
    tertiary = TertiaryDark,
    onTertiary = Color(0xFF2A1748),
    tertiaryContainer = Color(0xFF4A3670),
    onTertiaryContainer = Color(0xFFEDE3F8),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = TextTertiaryDark,
    error = Color(0xFFFF6B61),
    onError = Color.White
)

/** Negro puro: apaga los píxeles en pantallas OLED. */
private val AmoledColorScheme = DarkColorScheme.copy(
    background = BackgroundAmoled,
    surface = SurfaceAmoled,
    surfaceVariant = SurfaceVariantAmoled,
    surfaceContainerLowest = BackgroundAmoled,
    surfaceContainerLow = SurfaceAmoled,
    surfaceContainer = SurfaceVariantAmoled,
    surfaceContainerHigh = SurfaceVariantAmoled,
    surfaceContainerHighest = SurfaceVariantDark,
    outline = Color(0xFF2C2C2E),
    outlineVariant = Color(0xFF1C1C1E)
)

/** Indica si la app está en modo oscuro (según el tema elegido en Ajustes, no solo el del sistema). */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun isAppInDarkTheme(): Boolean = LocalDarkTheme.current

/** Estilo de los códigos: Inter con cifras tabulares para que no "bailen" al cambiar. */
val CodeTextStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = 1.5.sp,
    fontFeatureSettings = "tnum"
)

@Composable
fun Mis2FATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        !darkTheme -> LightColorScheme
        amoled -> AmoledColorScheme
        else -> DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

/** Texto de advertencia: naranja con contraste suficiente (AA) en claro y en oscuro. */
@Composable
@ReadOnlyComposable
fun warningTextColor(): Color = if (isAppInDarkTheme()) AccentOrange else Color(0xFFA35400)
