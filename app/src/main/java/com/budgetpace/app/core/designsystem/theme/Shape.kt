package com.budgetpace.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape tokens per spec §35:
 *   Small controls / inputs: 8dp
 *   Buttons:                 8–10dp  → 8dp
 *   Cards:                   12dp
 *   Large containers:        14–16dp → 16dp
 *
 * The interface should have "slightly sharp corners" — avoid 28–32dp radii.
 */
val BudgetPaceShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),   // inputs, small controls
    medium     = RoundedCornerShape(8.dp),   // buttons
    large      = RoundedCornerShape(12.dp),  // cards
    extraLarge = RoundedCornerShape(16.dp),  // large containers
)
