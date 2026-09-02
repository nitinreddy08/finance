package com.budgetpace.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Extended color container for semantic tokens ─────────────────────────────
data class BudgetPaceColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val statusGreen: Color,
    val statusOrange: Color,
    val statusRed: Color,
    val statusBlue: Color,
    val statusGrey: Color,
)

val LocalBudgetPaceColors = staticCompositionLocalOf {
    BudgetPaceColors(
        background     = BackgroundLight,
        surface        = SurfaceLight,
        textPrimary    = TextPrimaryLight,
        textSecondary  = TextSecondaryLight,
        border         = BorderLight,
        statusGreen    = StatusGreen,
        statusOrange   = StatusOrange,
        statusRed      = StatusRed,
        statusBlue     = StatusBlue,
        statusGrey     = StatusGrey,
    )
}

private val LightColorScheme = lightColorScheme(
    primary          = PrimaryLight,
    onPrimary        = OnPrimaryLight,
    background       = BackgroundLight,
    onBackground     = TextPrimaryLight,
    surface          = SurfaceLight,
    onSurface        = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    outline          = BorderLight,
)

private val DarkColorScheme = darkColorScheme(
    primary          = PrimaryDark,
    onPrimary        = OnPrimaryDark,
    background       = BackgroundDark,
    onBackground     = TextPrimaryDark,
    surface          = SurfaceDark,
    onSurface        = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline          = BorderDark,
)

@Composable
fun BudgetPaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val extendedColors = if (darkTheme) {
        BudgetPaceColors(
            background    = BackgroundDark,
            surface       = SurfaceDark,
            textPrimary   = TextPrimaryDark,
            textSecondary = TextSecondaryDark,
            border        = BorderDark,
            statusGreen   = StatusGreen,
            statusOrange  = StatusOrange,
            statusRed     = StatusRed,
            statusBlue    = StatusBlue,
            statusGrey    = StatusGrey,
        )
    } else {
        BudgetPaceColors(
            background    = BackgroundLight,
            surface       = SurfaceLight,
            textPrimary   = TextPrimaryLight,
            textSecondary = TextSecondaryLight,
            border        = BorderLight,
            statusGreen   = StatusGreen,
            statusOrange  = StatusOrange,
            statusRed     = StatusRed,
            statusBlue    = StatusBlue,
            statusGrey    = StatusGrey,
        )
    }

    CompositionLocalProvider(LocalBudgetPaceColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = BudgetPaceTypography,
            shapes      = BudgetPaceShapes,
            content     = content,
        )
    }
}

// Convenience accessor
val MaterialTheme.bpColors: BudgetPaceColors
    @Composable get() = LocalBudgetPaceColors.current
