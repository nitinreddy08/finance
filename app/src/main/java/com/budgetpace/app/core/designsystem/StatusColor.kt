package com.budgetpace.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.BudgetStatus

/**
 * The single mapping from [BudgetStatus] to a color, shared by every screen that draws a pace bar
 * or a pace-colored figure (Home, Category Pace, Category Detail's period bars). Previously
 * duplicated between DashboardScreen and CategoryDetailScreen — two copies that could silently
 * drift apart on the next palette change.
 */
@Composable
fun statusColor(status: BudgetStatus): Color = when (status) {
    BudgetStatus.GREEN -> MaterialTheme.bpColors.statusGreen
    BudgetStatus.ORANGE -> MaterialTheme.bpColors.statusOrange
    BudgetStatus.RED -> MaterialTheme.bpColors.statusRed
    BudgetStatus.GREY -> MaterialTheme.colorScheme.outline
    BudgetStatus.CURRENT -> MaterialTheme.bpColors.statusBlue
}
