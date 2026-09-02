package com.budgetpace.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// ─── Light palette ────────────────────────────────────────────────────────────
val BackgroundLight   = Color(0xFFF7F6F2)
val SurfaceLight      = Color(0xFFFCFBF8)
val TextPrimaryLight  = Color(0xFF1D1D1B)
val TextSecondaryLight= Color(0xFF686762)
val BorderLight       = Color(0xFFD9D7D0)

// ─── Dark palette ─────────────────────────────────────────────────────────────
val BackgroundDark    = Color(0xFF171817)
val SurfaceDark       = Color(0xFF1D1E1C)
val TextPrimaryDark   = Color(0xFFF1F0EB)
val TextSecondaryDark = Color(0xFFA6A49D)
val BorderDark        = Color(0xFF343530)

// ─── Semantic / status ────────────────────────────────────────────────────────
val StatusBlue        = Color(0xFF3B82F6)   // Current / on-track
val StatusOrange      = Color(0xFFE98A15)   // Slightly over
val StatusRed         = Color(0xFFD64545)   // Over budget
val StatusGreen       = Color(0xFF3D8B5F)   // Under budget
val StatusGrey        = Color(0xFF9E9E9E)   // Upcoming / future period

// ─── M3 role mappings ─────────────────────────────────────────────────────────
// Used inside Theme.kt to populate MaterialTheme.colorScheme
val PrimaryLight      = Color(0xFF1D1D1B)
val PrimaryDark       = Color(0xFFF1F0EB)
val OnPrimaryLight    = Color(0xFFFCFBF8)
val OnPrimaryDark     = Color(0xFF171817)
