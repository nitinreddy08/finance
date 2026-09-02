package com.budgetpace.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.BudgetStatus
import com.budgetpace.app.core.model.CategorySummary
import com.budgetpace.app.core.model.MonthSummary
import com.budgetpace.app.core.model.PeriodSummary
import com.budgetpace.app.core.money.Money
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    when (val state = uiState) {
        is DashboardUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is DashboardUiState.Success -> {
            DashboardScreen(summary = state.summary)
        }
        is DashboardUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error loading dashboard")
            }
        }
    }
}

@Composable
fun DashboardScreen(summary: MonthSummary) {
    val colors = MaterialTheme.bpColors
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Top Banner (Month & Remaining)
        item {
            MonthHeader(summary)
        }
        
        // Safe to spend
        item {
            SafeToSpendBanner(summary.safeToSpendMinor)
        }
        
        // Overall Pace
        item {
            FourPeriodPaceCard(
                title = "OVERALL PACE",
                periods = summary.overallPeriods
            )
        }
        
        // Categories
        item {
            Text(
                text = "CATEGORIES",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary
            )
        }
        
        items(summary.categories) { categorySummary ->
            CategoryPaceItem(categorySummary)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MonthHeader(summary: MonthSummary) {
    val monthName = Month.of(summary.month.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
    Column {
        Text(
            text = "$monthName ${summary.month.year}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.bpColors.textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Money.formatRupees(summary.remainingMinor),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.bpColors.textPrimary
        )
        Text(
            text = "remaining",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.bpColors.textSecondary
        )
    }
}

@Composable
private fun SafeToSpendBanner(safeToSpendMinor: Long) {
    Surface(
        color = MaterialTheme.bpColors.surface,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.bpColors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "SAFE TO SPEND THIS PERIOD",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.bpColors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Money.formatRupees(safeToSpendMinor),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.bpColors.textPrimary
            )
        }
    }
}

@Composable
fun FourPeriodPaceCard(title: String, periods: List<PeriodSummary>) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.bpColors.textSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            periods.forEach { period ->
                PeriodTile(
                    period = period,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PeriodTile(period: PeriodSummary, modifier: Modifier = Modifier) {
    val color = when (period.paceStatus) {
        BudgetStatus.GREEN -> MaterialTheme.bpColors.statusGreen
        BudgetStatus.ORANGE -> MaterialTheme.bpColors.statusOrange
        BudgetStatus.RED -> MaterialTheme.bpColors.statusRed
        BudgetStatus.GREY -> MaterialTheme.bpColors.statusGrey
        BudgetStatus.CURRENT -> MaterialTheme.bpColors.statusBlue
    }
    
    Surface(
        color = MaterialTheme.bpColors.surface,
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.bpColors.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "W${period.periodIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.bpColors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (period.spentMinor > 0) Money.formatRupeesWhole(period.spentMinor) else "—",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.bpColors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

@Composable
fun CategoryPaceItem(summary: CategorySummary) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = summary.category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.bpColors.textPrimary
            )
            Text(
                text = "${Money.formatRupeesWhole(summary.totalSpentMinor)} / ${Money.formatRupeesWhole(summary.category.monthlyBudgetMinor)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.bpColors.textSecondary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        if (summary.category.weeklyPacingEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                summary.periods.forEach { period ->
                    PeriodTile(period = period, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Text(
                text = "Monthly",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.bpColors.textSecondary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${Money.formatRupeesWhole(summary.remainingMinor)} remaining",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.bpColors.textSecondary
        )
    }
}
