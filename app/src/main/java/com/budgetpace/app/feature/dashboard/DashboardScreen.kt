package com.budgetpace.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.BudgetStatus
import com.budgetpace.app.core.model.CategorySummary
import com.budgetpace.app.core.model.MonthSummary
import com.budgetpace.app.core.model.PeriodStatus
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
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            }
        }
        is DashboardUiState.Success -> {
            DashboardScreen(summary = state.summary)
        }
        is DashboardUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text("Error loading dashboard", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
fun DashboardScreen(summary: MonthSummary) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Dark background from mockup
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val monthName = Month.of(summary.month.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
                    Text(
                        text = "$monthName ${summary.month.year}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Change Month",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        
        // TOTAL REMAINING Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TOTAL REMAINING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Money.formatRupeesWhole(summary.remainingMinor),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "of ${Money.formatRupeesWhole(summary.totalBudgetMinor)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Circular Progress
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                        CircularProgressIndicator(
                            progress = 1f,
                            color = MaterialTheme.colorScheme.outline,
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        val ratio = if (summary.totalBudgetMinor > 0) summary.remainingMinor.toFloat() / summary.totalBudgetMinor else 0f
                        CircularProgressIndicator(
                            progress = ratio.coerceIn(0f, 1f),
                            color = Color(0xFF4CAF50),
                            strokeWidth = 6.dp,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.fillMaxSize()
                        )
                        val pct = (ratio * 100).toInt().coerceIn(0, 100)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${pct}%", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("left", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        
        // SAFE TO SPEND TODAY Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SAFE TO SPEND TODAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Money.formatRupeesWhole(summary.safeToSpendMinor),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Based on time passed & budget pace",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // BUDGET PACE
        item {
            Column {
                Text(
                    text = "BUDGET PACE (4 PERIODS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summary.overallPeriods.forEach { period ->
                        PeriodTile(period = period, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        // TOP CATEGORIES
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOP CATEGORIES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "View all",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64B5F6)
                )
            }
        }
        
        items(summary.categories) { categorySummary ->
            CategoryMockupItem(categorySummary)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PeriodTile(period: PeriodSummary, modifier: Modifier = Modifier) {
    val color = when (period.paceStatus) {
        BudgetStatus.GREEN -> Color(0xFF4CAF50)
        BudgetStatus.ORANGE -> Color(0xFFFF9800)
        BudgetStatus.RED -> Color(0xFFF44336)
        BudgetStatus.GREY -> MaterialTheme.colorScheme.onSurfaceVariant
        BudgetStatus.CURRENT -> Color(0xFF64B5F6)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "WEEK ${period.periodIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = when (period.periodStatus) {
                    PeriodStatus.UPCOMING -> "UPCOMING"
                    PeriodStatus.CURRENT -> "CURRENT"
                    PeriodStatus.COMPLETED -> "COMPLETED"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (period.periodStatus == PeriodStatus.UPCOMING) "—"
                       else Money.formatRupeesWhole(period.spentMinor),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
            Text(
                text = "of ${Money.formatRupeesWhole(period.effectiveBudgetMinor)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Spec §39: status must never rely on color alone — always pair it with a word.
            Text(
                text = periodStatusLabel(period),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

private fun periodStatusLabel(period: PeriodSummary): String = when (period.paceStatus) {
    BudgetStatus.GREEN -> "ON TRACK"
    BudgetStatus.ORANGE -> "OVER"
    BudgetStatus.RED -> "OVER BUDGET"
    BudgetStatus.GREY -> "UPCOMING"
    BudgetStatus.CURRENT -> "ON TRACK"
}

@Composable
fun CategoryMockupItem(summary: CategorySummary) {
    Row(verticalAlignment = Alignment.Top) {
        // Spec §33: no literal emoji/color-square icons in production — a neutral letter
        // avatar until categories have real per-category icons.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                summary.category.name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = summary.category.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${Money.formatRupeesWhole(summary.totalSpentMinor)} / ${Money.formatRupeesWhole(summary.category.monthlyBudgetMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Spec §24/§33: a category with weeklyPacingEnabled = false (e.g. Rent)
            // participates in the overall pace but shows "Monthly" here instead of its own
            // four-period breakdown.
            if (summary.category.weeklyPacingEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    summary.periods.forEach { period ->
                        CategoryPeriodMiniTile(period = period, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${Money.formatRupeesWhole(summary.remainingMinor)} remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Monthly",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                val ratio = if (summary.category.monthlyBudgetMinor > 0) summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor else 0f
                LinearProgressIndicator(
                    progress = ratio.coerceIn(0f, 1f),
                    color = Color(0xFF4CAF50),
                    trackColor = MaterialTheme.colorScheme.outline,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryPeriodMiniTile(period: PeriodSummary, modifier: Modifier = Modifier) {
    val color = when (period.paceStatus) {
        BudgetStatus.GREEN -> Color(0xFF4CAF50)
        BudgetStatus.ORANGE -> Color(0xFFFF9800)
        BudgetStatus.RED -> Color(0xFFF44336)
        BudgetStatus.GREY -> MaterialTheme.colorScheme.onSurfaceVariant
        BudgetStatus.CURRENT -> Color(0xFF64B5F6)
    }
    val statusText = when (period.paceStatus) {
        BudgetStatus.GREEN -> "ON TRACK"
        BudgetStatus.ORANGE -> "OVER"
        BudgetStatus.RED -> "OVER"
        BudgetStatus.GREY -> "UPCOMING"
        BudgetStatus.CURRENT -> "NOW"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "W${period.periodIndex + 1}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (period.periodStatus == PeriodStatus.UPCOMING) "—"
                       else Money.formatRupeesWhole(period.spentMinor),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                // Spec §39: status must never rely on color alone — pair it with text.
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = color
            )
        }
    }
}
