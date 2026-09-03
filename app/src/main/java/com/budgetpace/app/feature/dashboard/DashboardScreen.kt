package com.budgetpace.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.core.model.BudgetStatus
import com.budgetpace.app.core.model.CategorySummary
import com.budgetpace.app.core.model.MonthStatus
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
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is DashboardUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            }
        }
        is DashboardUiState.Success -> {
            DashboardScreen(
                summary = state.summary,
                availableMonths = availableMonths,
                onCategoryClick = onCategoryClick,
                onSelectMonth = viewModel::selectMonth,
            )
        }
        is DashboardUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text("Error loading dashboard", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/**
 * Spec §5 "HOME — FINAL": the most important screen, and it must never scroll. The summary
 * section (header, Safe-to-spend, pace bar) is fixed; only the Category Pace grid below it may
 * scroll internally, and only once there are more categories than fit on screen.
 */
@Composable
fun DashboardScreen(
    summary: MonthSummary,
    availableMonths: List<BudgetMonth> = emptyList(),
    onCategoryClick: (String) -> Unit = {},
    onSelectMonth: (String?) -> Unit = {},
) {
    val monthName = Month.of(summary.month.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
    val currentPeriod = summary.overallPeriods.firstOrNull { it.isCurrentPeriod }
    val overageMinor = currentPeriod?.overageMinor ?: 0L
    val pctUsed = if (summary.totalBudgetMinor > 0)
        (summary.totalSpentMinor.toFloat() / summary.totalBudgetMinor * 100).toInt().coerceAtLeast(0)
    else 0
    val hasSpending = summary.totalSpentMinor > 0
    var showMonthPicker by remember { mutableStateOf(false) }
    var showMoreCategories by remember { mutableStateOf(false) }

    if (showMonthPicker) {
        MonthPickerDialog(
            months = availableMonths,
            onSelect = { monthId -> onSelectMonth(monthId); showMonthPicker = false },
            onDismiss = { showMonthPicker = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header — month name opens a picker to view a past (archived) month; there's no
        // overflow action defined yet, so no dead "⋮" button sitting there doing nothing.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMonthPicker = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        Spacer(modifier = Modifier.height(20.dp))

        // Safe to spend — the dominant element on the screen. "this week"/"over pace" sit beside
        // the number rather than stacked under it, to leave more vertical room for Category Pace.
        Text(
            text = "SAFE TO SPEND",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = Money.formatRupeesWhole(summary.safeToSpendMinor),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = "this week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (overageMinor > 0) {
                    Text(
                        text = "${Money.formatRupeesWhole(overageMinor)} over pace",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFF44336)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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

        Spacer(modifier = Modifier.height(24.dp))

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
                        text = "Your bank expenses will appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Only a "4 periods" category has real week-by-week pacing worth visualizing — a
            // "Start of month" category is just a lump sum with nothing to pace, so it moves out
            // of this scroller into the plain "More categories" list below instead. This area
            // (not the summary above it) is the only part of Home that ever scrolls.
            val pacingCategories = summary.categories.filter { it.category.weeklyPacingEnabled }
            val lumpSumCategories = summary.categories.filterNot { it.category.weeklyPacingEnabled }
            val currentPeriodIndex = summary.overallPeriods.firstOrNull { it.isCurrentPeriod }?.periodIndex

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (pacingCategories.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(pacingCategories) { categorySummary ->
                            CategoryPaceItem(
                                summary = categorySummary,
                                currentPeriodIndex = currentPeriodIndex,
                                onClick = { onCategoryClick(categorySummary.category.id.toString()) },
                            )
                        }
                    }
                }
                if (lumpSumCategories.isNotEmpty()) {
                    MoreCategoriesSection(
                        categories = lumpSumCategories,
                        expanded = showMoreCategories,
                        onToggle = { showMoreCategories = !showMoreCategories },
                        onCategoryClick = { categorySummary -> onCategoryClick(categorySummary.category.id.toString()) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MonthPickerDialog(
    months: List<BudgetMonth>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text("Select month") },
        text = {
            if (months.isEmpty()) {
                Text("No months yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column {
                    // Daos.observeAll() already orders newest-first.
                    months.forEach { m ->
                        val label = Month.of(m.month).getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${m.year}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(if (m.status == MonthStatus.ACTIVE) null else m.id.toString()) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
                            if (m.status == MonthStatus.ACTIVE) {
                                Text("Current", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
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

private val CATEGORY_BAR_HEIGHT = 96.dp

/**
 * Spec §5: emoji, a scaled mark, amount, and percent — one card per "4 periods" category (a
 * "start of month" category has nothing to pace, so it lives in [MoreCategoriesSection] instead).
 * A light border gives each tile a visible boundary now that they scroll horizontally rather than
 * wrapping into a fixed grid, so there's no leftover-space problem to work around.
 */
@Composable
private fun CategoryPaceItem(summary: CategorySummary, currentPeriodIndex: Int?, onClick: () -> Unit) {
    val pct = if (summary.category.monthlyBudgetMinor > 0)
        (summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor * 100).toInt().coerceAtLeast(0)
    else 0

    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CategoryIcon(iconKey = summary.category.iconKey, name = summary.category.name, size = 28.dp)
        Spacer(modifier = Modifier.height(10.dp))
        CategoryPaceSegmentedMark(summary, currentPeriodIndex)
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

/**
 * A "4 periods" category's mark: [CATEGORY_BAR_HEIGHT] split into as many equal cells as the
 * category has periods, each filled as a discrete step of the category's OVERALL spend fraction
 * (not that period's own budget) — so "half the month's budget spent" always shows as exactly
 * half the cells filled, however that spending actually landed across periods. All filled cells
 * share one color: [CategorySummary.overallStatus], the category's elapsed-time-adjusted pace
 * (spent vs. what you'd expect to have spent by today given how much of the month has passed) —
 * so spending it all in period 1 shows red immediately, and eases back to green as elapsed time
 * catches up if nothing more is spent. The current period's cell gets a subtle highlight border
 * so "which week am I in" is visible at a glance.
 */
@Composable
private fun CategoryPaceSegmentedMark(summary: CategorySummary, currentPeriodIndex: Int?) {
    val overallFraction = if (summary.category.monthlyBudgetMinor > 0)
        (summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor).coerceIn(0f, 1f)
    else 0f
    val pct = (overallFraction * 100).toInt()
    val color = statusColor(summary.overallStatus)
    val sortedAscending = summary.periods.sortedBy { it.periodIndex }
    val periodCount = sortedAscending.size.coerceAtLeast(1)
    val highlightColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .width(14.dp)
            .height(CATEGORY_BAR_HEIGHT)
            .semantics {
                contentDescription = "${summary.category.name}, ${Money.formatRupeesWhole(summary.totalSpentMinor)} of " +
                    "${Money.formatRupeesWhole(summary.category.monthlyBudgetMinor)}, $pct% used"
            },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Top of the column = the last period, bottom = the first — same reading direction as
        // the overall pace bar (left-to-right becomes bottom-to-top for a vertical mark).
        sortedAscending.reversed().forEach { period ->
            val cellFraction = (overallFraction * periodCount - period.periodIndex).coerceIn(0f, 1f)
            val isCurrent = period.periodIndex == currentPeriodIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    .then(
                        if (isCurrent) Modifier.border(1.dp, highlightColor, RoundedCornerShape(3.dp))
                        else Modifier
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (cellFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(cellFraction)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

/** A collapsible list for "start of month" categories — a lump sum with nothing to pace, so a
 * plain row (no bar) is enough; kept out of the horizontally-scrolling pace row above. */
@Composable
private fun MoreCategoriesSection(
    categories: List<CategorySummary>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCategoryClick: (CategorySummary) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "More categories (${categories.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { categorySummary ->
                    LumpSumCategoryRow(summary = categorySummary, onClick = { onCategoryClick(categorySummary) })
                }
            }
        }
    }
}

@Composable
private fun LumpSumCategoryRow(summary: CategorySummary, onClick: () -> Unit) {
    val pct = if (summary.category.monthlyBudgetMinor > 0)
        (summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor * 100).toInt().coerceAtLeast(0)
    else 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(iconKey = summary.category.iconKey, name = summary.category.name, size = 22.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = summary.category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${Money.formatRupeesWhole(summary.totalSpentMinor)} / ${Money.formatRupeesWhole(summary.category.monthlyBudgetMinor)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = statusColor(summary.overallStatus)
            )
        }
    }
}
