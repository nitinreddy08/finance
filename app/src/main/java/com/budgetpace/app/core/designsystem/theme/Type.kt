package com.budgetpace.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography scale per spec §37:
 *   Large balance:   32–40sp / Medium or Semibold
 *   Screen title:    22–24sp / Semibold
 *   Section title:   14–16sp / Medium
 *   Body:            14–16sp / Regular
 *   Metadata:        12–13sp
 *
 * Uses Android system sans (default). Typography should do most visual work.
 */
val BudgetPaceTypography = Typography(
    // Large balance display (e.g. ₹11,183 remaining)
    displayLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 38.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
    ),
    // Safe-to-spend figure
    displayMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    // Screen title (e.g. "September 2026")
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    // Section labels (e.g. "OVERALL PACE", "CATEGORIES"). Never below 12sp (spec §37).
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    ),
    // Category row name / period amounts
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    // Metadata: bank info, timestamps, status labels. Never below 12sp (spec §37).
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)
