package com.budgetpace.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Extended color container for semantic tokens the M3 scheme has no slot for ──
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
    /** Brand accent — same green in both themes; see [statusColor] for pace-driven color. */
    val accent: Color,
    val onAccent: Color,
    /** Destructive actions (delete category, delete expense) — same red as [statusRed]. */
    val danger: Color,
    val googleButtonBackground: Color,
    val googleButtonBorder: Color,
    val googleButtonText: Color,
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
        accent         = StatusGreen,
        onAccent       = OnPrimaryLight,
        danger         = StatusRed,
        googleButtonBackground = GoogleButtonBackgroundLight,
        googleButtonBorder     = GoogleButtonBorderLight,
        googleButtonText       = GoogleButtonTextLight,
    )
}

private val LightColorScheme = lightColorScheme(
    primary                   = PrimaryLight,
    onPrimary                 = OnPrimaryLight,
    primaryContainer          = PrimaryContainerLight,
    onPrimaryContainer        = OnPrimaryContainerLight,
    inversePrimary            = InversePrimaryLight,
    secondary                 = SecondaryLight,
    onSecondary               = OnSecondaryLight,
    secondaryContainer        = SecondaryContainerLight,
    onSecondaryContainer      = OnSecondaryContainerLight,
    tertiary                  = TertiaryLight,
    onTertiary                = OnTertiaryLight,
    tertiaryContainer         = TertiaryContainerLight,
    onTertiaryContainer       = OnTertiaryContainerLight,
    background                = BackgroundLight,
    onBackground              = TextPrimaryLight,
    surface                   = SurfaceLight,
    onSurface                 = TextPrimaryLight,
    surfaceVariant            = SurfaceVariantLight,
    onSurfaceVariant          = TextSecondaryLight,
    surfaceTint               = PrimaryLight,
    inverseSurface            = InverseSurfaceLight,
    inverseOnSurface          = InverseOnSurfaceLight,
    error                     = StatusRed,
    onError                   = Color(0xFFFFFFFF),
    errorContainer            = ErrorContainerLight,
    onErrorContainer          = OnErrorContainerLight,
    outline                   = BorderLight,
    outlineVariant            = OutlineVariantLight,
    scrim                     = Color(0xFF000000),
    surfaceBright             = SurfaceLight,
    surfaceDim                = SurfaceDimLight,
    surfaceContainerLowest    = SurfaceContainerLowestLight,
    surfaceContainerLow       = SurfaceContainerLowLight,
    surfaceContainer          = SurfaceContainerLight,
    surfaceContainerHigh      = SurfaceContainerHighLight,
    surfaceContainerHighest   = SurfaceContainerHighestLight,
)

private val DarkColorScheme = darkColorScheme(
    primary                   = PrimaryDark,
    onPrimary                 = OnPrimaryDark,
    primaryContainer          = PrimaryContainerDark,
    onPrimaryContainer        = OnPrimaryContainerDark,
    inversePrimary            = InversePrimaryDark,
    secondary                 = SecondaryDark,
    onSecondary               = OnSecondaryDark,
    secondaryContainer        = SecondaryContainerDark,
    onSecondaryContainer      = OnSecondaryContainerDark,
    tertiary                  = TertiaryDark,
    onTertiary                = OnTertiaryDark,
    tertiaryContainer         = TertiaryContainerDark,
    onTertiaryContainer       = OnTertiaryContainerDark,
    background                = BackgroundDark,
    onBackground              = TextPrimaryDark,
    surface                   = SurfaceDark,
    onSurface                 = TextPrimaryDark,
    surfaceVariant            = SurfaceVariantDark,
    onSurfaceVariant          = TextSecondaryDark,
    surfaceTint               = PrimaryDark,
    inverseSurface            = InverseSurfaceDark,
    inverseOnSurface          = InverseOnSurfaceDark,
    error                     = StatusRed,
    onError                   = Color(0xFFFFFFFF),
    errorContainer            = ErrorContainerDark,
    onErrorContainer          = OnErrorContainerDark,
    outline                   = BorderDark,
    outlineVariant            = OutlineVariantDark,
    scrim                     = Color(0xFF000000),
    surfaceBright             = SurfaceBrightDark,
    surfaceDim                = BackgroundDark,
    surfaceContainerLowest    = SurfaceContainerLowestDark,
    surfaceContainerLow       = SurfaceContainerLowDark,
    surfaceContainer          = SurfaceContainerDark,
    surfaceContainerHigh      = SurfaceContainerHighDark,
    surfaceContainerHighest   = SurfaceContainerHighestDark,
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
            accent        = StatusGreen,
            onAccent      = OnPrimaryDark,
            danger        = StatusRed,
            googleButtonBackground = GoogleButtonBackgroundDark,
            googleButtonBorder     = GoogleButtonBorderDark,
            googleButtonText       = GoogleButtonTextDark,
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
            accent        = StatusGreen,
            onAccent      = OnPrimaryLight,
            danger        = StatusRed,
            googleButtonBackground = GoogleButtonBackgroundLight,
            googleButtonBorder     = GoogleButtonBorderLight,
            googleButtonText       = GoogleButtonTextLight,
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
