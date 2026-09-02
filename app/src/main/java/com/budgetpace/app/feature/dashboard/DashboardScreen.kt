package com.budgetpace.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.designsystem.components.CategoryIcon
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
    viewModel: DashboardViewModel,
    onCategoryClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is DashboardUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            }
        }
        is DashboardUiState.Success -> {
            DashboardScreen(summary = state.summary, onCategoryClick = onCategoryClick)
        }
        is DashboardUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text("Error loading dashboard", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/**
 * Spec §5 "HOME — FINAL": the most important screen, and it must never scroll. Everything here
 * is a single non-scrolling Column — header, the dominant Safe-to-spend number, a wordless
 * 4-segment pace bar, and a compact cross-category chart.
 */
@Composable
fun DashboardScreen(summary: MonthSummary, onCategoryClick: (String) -> Unit = {}) {
    val monthName = Month.of(summary.month.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
    val currentPeriod = summary.overallPeriods.firstOrNull { it.isCurrentPeriod }
    val overageMinor = currentPeriod?.overageMinor ?: 0L
    val pctUsed = if (summary.totalBudgetMinor > 0)
        (summary.totalSpentMinor.toFloat() / summary.totalBudgetMinor * 100).toInt().coerceAtLeast(0)
    else 0
    val hasSpending = summary.totalSpentMinor > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header — spec §5: month name + a single overflow action, nothing else.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$monthName ${summary.month.year}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Change month",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Safe to spend — the dominant element on the screen.
        Text(
            text = "SAFE TO SPEND",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = Money.formatRupeesWhole(summary.safeToSpendMinor),
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "this week",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (overageMinor > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${Money.formatRupeesWhole(overageMinor)} over pace",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF44336)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Spent / Budget row — spec §5: values only, no chart words.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Spent ${Money.formatRupeesWhole(summary.totalSpentMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Budget ${Money.formatRupeesWhole(summary.totalBudgetMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$pctUsed% used",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Four-segment pace bar — spec §5: color communicates status, no "Week"/status words.
        Row(
            modifier = Modifier.fillMaxWidth().height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            summary.overallPeriods.forEach { period ->
                PaceSegment(period = period, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "CATEGORY PACE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (summary.categories.isEmpty() || !hasSpending) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No spending yet",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your bank transactions will appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                items(summary.categories) { categorySummary ->
                    CategoryPaceItem(
                        summary = categorySummary,
                        onClick = { onCategoryClick(categorySummary.category.id.toString()) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun statusColor(status: BudgetStatus): Color = when (status) {
    BudgetStatus.GREEN -> MaterialTheme.bpColors.statusGreen
    BudgetStatus.ORANGE -> MaterialTheme.bpColors.statusOrange
    BudgetStatus.RED -> MaterialTheme.bpColors.statusRed
    BudgetStatus.GREY -> MaterialTheme.colorScheme.outline
    BudgetStatus.CURRENT -> MaterialTheme.bpColors.statusBlue
}

@Composable
private fun PaceSegment(period: PeriodSummary, modifier: Modifier = Modifier) {
    val filled = if (period.periodStatus == PeriodStatus.UPCOMING || period.effectiveBudgetMinor <= 0) 0f
    else (period.spentMinor.toFloat() / period.effectiveBudgetMinor).coerceIn(0f, 1f)
    // The bar communicates status by color+fill alone visually (spec's minimal-wording rule) —
    // this is the screen-reader-only equivalent, not a visible label.
    val statusDescription = when (period.periodStatus) {
        PeriodStatus.UPCOMING -> "upcoming"
        PeriodStatus.CURRENT -> "current"
        PeriodStatus.COMPLETED -> "completed"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            .semantics {
                contentDescription = "Period ${period.periodIndex + 1}, $statusDescription, " +
                    "${Money.formatRupeesWhole(period.spentMinor)} of ${Money.formatRupeesWhole(period.effectiveBudgetMinor)}"
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(filled)
                .clip(RoundedCornerShape(4.dp))
                .background(statusColor(period.paceStatus))
        )
    }
}

/** Spec §5: emoji, a compact scaled mark, amount, and percent — one column per category. */
@Composable
private fun CategoryPaceItem(summary: CategorySummary, onClick: () -> Unit) {
    val ratio = if (summary.category.monthlyBudgetMinor > 0)
        summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor
    else 0f
    val pct = (ratio * 100).toInt().coerceAtLeast(0)
    // Cap the visual scale at 150% so a large blowout doesn't dominate the chart.
    val markFraction = (ratio / 1.5f).coerceIn(0.04f, 1f)
    val color = statusColor(summary.overallStatus)

    Column(
        modifier = Modifier
            .width(56.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CategoryIcon(iconKey = summary.category.iconKey, name = summary.category.name, size = 28.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                .semantics {
                    contentDescription = "${summary.category.name}, ${Money.formatRupeesWhole(summary.totalSpentMinor)} spent, $pct% of budget"
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(markFraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Money.formatRupeesWhole(summary.totalSpentMinor),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
