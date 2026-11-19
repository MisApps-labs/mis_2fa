// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Identidad de marca (familia "Mis Apps")
// ==========================================
// Como Mis Notas usa el verde de su logo, Mis 2FA usa el azul acero del suyo (#7F99B9),
// ajustado para que el texto y los iconos tengan buen contraste (AA) en claro y en oscuro.
val LogoBlue = Color(0xFF7F99B9)             // Azul exacto del logo
val BrandBlue = Color(0xFF3F5F88)            // Primario en modo claro (contraste 6,5:1 sobre blanco)
val BrandBlueLight = Color(0xFFA9C2E2)       // Primario en modo oscuro (9,3:1 sobre la superficie)
val BrandBlueContainer = Color(0xFFDCE6F3)
val BrandBlueOnContainer = Color(0xFF0E1D33)
val BrandBlueDarkContainer = Color(0xFF34506F)
val BrandCream = Color(0xFFFFFBF5)           // Fondo del logo

// Acentos del logo: el escudo (menta), los dígitos (lavanda) y la llave (amarillo).
val LogoMint = Color(0xFFBEEFDA)
val LogoMintDeep = Color(0xFF98DDC0)
val LogoLavender = Color(0xFFC8AFE4)
val LogoKeyYellow = Color(0xFFFFD97A)

val SecondaryLight = Color(0xFF2E7D5B)       // Menta oscurecido (contraste 5:1)
val SecondaryDark = Color(0xFF7FD1AE)
val TertiaryLight = Color(0xFF7E57C2)        // Lavanda oscurecido
val TertiaryDark = Color(0xFFC8AFE4)

// ==========================================
// Tema claro (fondos agrupados estilo iOS, compartidos con Mis Notas y Mis Podcast)
// ==========================================
val BackgroundLight = Color(0xFFF2F2F7)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE5E5EA)
val TextPrimaryLight = Color(0xFF000000)
val TextSecondaryLight = Color(0xFF6C6C70)
val TextTertiaryLight = Color(0xFFC7C7CC)
val OutlineLight = Color(0xFFD1D1D6)

// ==========================================
// Tema oscuro (gris elegante) y AMOLED (negro puro)
// ==========================================
val BackgroundDark = Color(0xFF121214)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceVariantDark = Color(0xFF2C2C2E)
val BackgroundAmoled = Color(0xFF000000)
val SurfaceAmoled = Color(0xFF000000)
val SurfaceVariantAmoled = Color(0xFF121214)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFF98989D)
val TextTertiaryDark = Color(0xFF48484A)
val OutlineDark = Color(0xFF38383A)

// ==========================================
// Acentos semánticos
// ==========================================
val AccentGreen = Color(0xFF34C759)
val AccentOrange = Color(0xFFFF9500)
val AccentRed = Color(0xFFFF3B30)

/**
 * Colores de los avatares de las cuentas: tonos pastel del logo y afines, para que la lista sea
 * fácil de recorrer con la vista. Cada emisor recibe siempre el mismo color.
 */
val AvatarPalette = listOf(
    Color(0xFF7F99B9), // azul del logo
    Color(0xFF5FB894), // menta
    Color(0xFF9C7BD0), // lavanda
    Color(0xFFE0A93B), // llave
    Color(0xFFE07A7A), // coral
    Color(0xFF4FA3C7), // celeste
    Color(0xFFC77DAE), // malva
    Color(0xFF7DAA5C)  // salvia
)

fun avatarColorFor(name: String): Color {
    val key = name.trim().lowercase()
    if (key.isEmpty()) return AvatarPalette[0]
    return AvatarPalette[Math.floorMod(key.hashCode(), AvatarPalette.size)]
}
