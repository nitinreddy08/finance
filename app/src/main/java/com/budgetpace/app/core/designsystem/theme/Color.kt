package com.budgetpace.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// ─── Light palette (warm cream / charcoal) ────────────────────────────────────
val BackgroundLight   = Color(0xFFF7F6F2)
val SurfaceLight      = Color(0xFFFCFBF8)
val TextPrimaryLight  = Color(0xFF1D1D1B)
val TextSecondaryLight= Color(0xFF686762)
val BorderLight       = Color(0xFFD9D7D0)

// Neutral (never lavender) tints derived from the same warm palette.
val SurfaceVariantLight        = Color(0xFFECEAE3)
val OutlineVariantLight        = Color(0xFFE7E5DE)
val SecondaryContainerLight    = Color(0xFFECEAE3)
val OnSecondaryContainerLight  = Color(0xFF29271F)
val SurfaceContainerLowestLight  = Color(0xFFFFFFFF)
val SurfaceContainerLowLight     = Color(0xFFF7F6F1)
val SurfaceContainerLight        = Color(0xFFF1EFE9)
val SurfaceContainerHighLight    = Color(0xFFEBE9E2)
val SurfaceContainerHighestLight = Color(0xFFE5E3DC)
val SurfaceDimLight  = Color(0xFFDEDCD5)
val InverseSurfaceLight   = Color(0xFF32332F)
val InverseOnSurfaceLight = Color(0xFFF3F2ED)

// ─── Dark palette ─────────────────────────────────────────────────────────────
val BackgroundDark    = Color(0xFF171817)
val SurfaceDark       = Color(0xFF1D1E1C)
val TextPrimaryDark   = Color(0xFFF1F0EB)
val TextSecondaryDark = Color(0xFFA6A49D)
val BorderDark        = Color(0xFF343530)

val SurfaceVariantDark        = Color(0xFF2A2B27)
val OutlineVariantDark        = Color(0xFF2A2B27)
val SecondaryContainerDark    = Color(0xFF2A2B27)
val OnSecondaryContainerDark  = Color(0xFFE5E3DC)
val SurfaceContainerLowestDark  = Color(0xFF121311)
val SurfaceContainerLowDark     = Color(0xFF1A1B19)
val SurfaceContainerDark        = Color(0xFF1E1F1C)
val SurfaceContainerHighDark    = Color(0xFF282924)
val SurfaceContainerHighestDark = Color(0xFF33342E)
val SurfaceBrightDark  = Color(0xFF34352F)
val InverseSurfaceDark   = Color(0xFFE5E3DC)
val InverseOnSurfaceDark = Color(0xFF1D1E1C)

// ─── Semantic / status (identical across themes — these communicate pace state,
//     not surface elevation, so they deliberately do not shift with light/dark). ──
val StatusBlue        = Color(0xFF3B82F6)   // Current / on-track
val StatusOrange      = Color(0xFFE98A15)   // Slightly over
val StatusRed         = Color(0xFFD64545)   // Over budget / error
val StatusGreen       = Color(0xFF3D8B5F)   // Under budget / accent
val StatusGrey        = Color(0xFF9E9E9E)   // Upcoming / future period

// ─── M3 role mappings ─────────────────────────────────────────────────────────
// Used inside Theme.kt to populate MaterialTheme.colorScheme
val PrimaryLight      = StatusGreen
val PrimaryDark       = StatusGreen
val OnPrimaryLight    = Color(0xFFFFFFFF)
val OnPrimaryDark     = Color(0xFFFFFFFF)
val PrimaryContainerLight   = Color(0xFFD9EEDD)
val OnPrimaryContainerLight = Color(0xFF163D26)
val PrimaryContainerDark    = Color(0xFF1F4A31)
val OnPrimaryContainerDark  = Color(0xFFB9E6C9)
val InversePrimaryLight = Color(0xFF8FD1A8)
val InversePrimaryDark  = Color(0xFF1B3A1B)

val SecondaryLight = Color(0xFF75736C)
val SecondaryDark  = TextSecondaryDark
val OnSecondaryLight = Color(0xFFFFFFFF)
val OnSecondaryDark  = Color(0xFF1D1E1C)

val TertiaryLight = StatusBlue
val TertiaryDark  = StatusBlue
val OnTertiaryLight = Color(0xFFFFFFFF)
val OnTertiaryDark  = Color(0xFFFFFFFF)
val TertiaryContainerLight   = Color(0xFFDCE9FD)
val OnTertiaryContainerLight = Color(0xFF0F2F57)
val TertiaryContainerDark    = Color(0xFF16324F)
val OnTertiaryContainerDark  = Color(0xFFCFE2FF)

val ErrorContainerLight   = Color(0xFFF8D7D7)
val OnErrorContainerLight = Color(0xFF5C1414)
val ErrorContainerDark    = Color(0xFF4A1515)
val OnErrorContainerDark  = Color(0xFFF6B8B8)

// ─── Google sign-in button (fixed brand colors — never theme-adaptive tokens) ──
val GoogleButtonBackgroundLight = Color(0xFFFFFFFF)
val GoogleButtonBorderLight     = Color(0xFF747775)
val GoogleButtonTextLight       = Color(0xFF1F1F1F)
val GoogleButtonBackgroundDark  = Color(0xFF131314)
val GoogleButtonBorderDark      = Color(0xFF8E918F)
val GoogleButtonTextDark        = Color(0xFFE3E3E3)
