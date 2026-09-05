package com.budgetpace.app.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Sms
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.designsystem.components.CategoryIcon
import com.budgetpace.app.core.designsystem.statusColor
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.core.model.BudgetStatus
import com.budgetpace.app.core.model.CategorySummary
import com.budgetpace.app.core.model.MonthStatus
import com.budgetpace.app.core.model.MonthSummary
import com.budgetpace.app.core.model.OverallPeriod
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.feature.detection.DetectionStatusChecker
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onCategoryClick: (monthId: String, categoryId: String) -> Unit,
    onOpenDetectionHealth: () -> Unit = {},
    onOpenUncategorized: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val uncategorizedCount by viewModel.uncategorizedCount.collectAsStateWithLifecycle()

    androidx.compose.animation.Crossfade(targetState = uiState, label = "dashboard") { state ->
        when (state) {
            is DashboardUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.bpColors.statusGreen)
                }
            }
            is DashboardUiState.Success -> {
                DashboardScreen(
                    summary = state.summary,
                    availableMonths = availableMonths,
                    uncategorizedCount = uncategorizedCount,
                    onCategoryClick = { categoryId -> onCategoryClick(state.summary.month.id.toString(), categoryId) },
                    onSelectMonth = viewModel::selectMonth,
                    onOpenDetectionHealth = onOpenDetectionHealth,
                    onOpenUncategorized = onOpenUncategorized,
                )
            }
            is DashboardUiState.NoMonth -> {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Text(
                        "Setting things up…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Spec §5 "HOME — FINAL": the most important screen, and it must never scroll. The summary
 * section (header, hero figure, pace bar, nudge rows) is fixed; only the Category Pace grid below
 * it may scroll internally, and only once there are more categories than fit on screen.
 */
@Composable
fun DashboardScreen(
    summary: MonthSummary,
    availableMonths: List<BudgetMonth> = emptyList(),
    uncategorizedCount: Int = 0,
    onCategoryClick: (String) -> Unit = {},
    onSelectMonth: (String?) -> Unit = {},
    onOpenDetectionHealth: () -> Unit = {},
    onOpenUncategorized: () -> Unit = {},
) {
    val context = LocalContext.current
    val monthName = Month.of(summary.month.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
    val isArchived = summary.month.status == MonthStatus.ARCHIVED
    val pctUsed = if (summary.totalBudgetMinor > 0)
        (summary.totalSpentMinor.toFloat() / summary.totalBudgetMinor * 100).toInt().coerceAtLeast(0)
    else 0
    var showMonthPicker by remember { mutableStateOf(false) }
    var showMoreCategories by remember { mutableStateOf(false) }

    // Bank-detection nudge: re-read live permission state whenever Home comes back to the
    // foreground (e.g. returning from Detection health), same pattern Settings already uses.
    var needsDetectionSetup by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        val status = DetectionStatusChecker.currentStatus(context)
        needsDetectionSetup = !status.smsPermissionGranted && !status.listenerEnabled
        onPauseOrDispose {}
    }

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
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Change month",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (isArchived) {
            val currentMonthLabel = availableMonths.firstOrNull { it.status == MonthStatus.ACTIVE }?.let {
                Month.of(it.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelectMonth(null) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (currentMonthLabel != null) "Past month · Back to $currentMonthLabel" else "Past month",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = needsDetectionSetup && !isArchived) {
            NudgeRow(
                text = "Turn on bank detection to record expenses automatically",
                icon = Icons.Outlined.Sms,
                onClick = onOpenDetectionHealth,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        HeroFigure(summary = summary, isArchived = isArchived)

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
            text = "$pctUsed% used · ${statusWord(summary.overallStatus)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        OverallPaceBar(summary = summary, pctUsed = pctUsed)

        if (uncategorizedCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            UncategorizedRow(count = uncategorizedCount, onClick = onOpenUncategorized)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "CATEGORY PACE",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (summary.categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No categories yet",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add a category to start tracking your pace.",
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
            val pacingCategories = summary.categories.filter { it.category.periodCount > 1 }
            val lumpSumCategories = summary.categories.filterNot { it.category.periodCount > 1 }

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (pacingCategories.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 4 per row, wrapping to further rows — one border frames the whole grid
                        // rather than each tile individually. Each tile takes an even 1/4 share of
                        // the row's width (not a fixed 76dp, which overflows a 360dp phone).
                        pacingCategories.chunked(4).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEach { categorySummary ->
                                    CategoryPaceItem(
                                        summary = categorySummary,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onCategoryClick(categorySummary.category.id.toString()) },
                                    )
                                }
                                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
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

private fun statusWord(status: BudgetStatus): String = when (status) {
    BudgetStatus.GREEN -> "on track"
    BudgetStatus.ORANGE -> "a bit over"
    BudgetStatus.RED -> "over budget"
    BudgetStatus.GREY -> "not started"
    BudgetStatus.CURRENT -> "on track"
}

@Composable
private fun HeroFigure(summary: MonthSummary, isArchived: Boolean) {
    if (isArchived) {
        val overageMinor = (summary.totalSpentMinor - summary.totalBudgetMinor).coerceAtLeast(0)
        val isOver = overageMinor > 0
        Text(
            text = if (isOver) "OVER BY" else "REMAINING",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = Money.formatRupeesWhole(if (isOver) overageMinor else summary.remainingMinor),
            style = MaterialTheme.typography.displayMedium,
            color = if (isOver) MaterialTheme.bpColors.danger else MaterialTheme.colorScheme.onBackground,
        )
        return
    }

    val overPaceMinor = summary.overPaceMinor
    val isOverPace = overPaceMinor > 0
    Text(
        text = if (isOverPace) "OVER PACE" else "SAFE TO SPEND",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = Money.formatRupeesWhole(if (isOverPace) overPaceMinor else summary.safeToSpendMinor),
            style = MaterialTheme.typography.displayMedium,
            color = if (isOverPace) MaterialTheme.bpColors.danger else MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            // Not "this week": a category may pace itself over 2 or 3 periods, so the figure
            // covers whatever period each of them is currently in.
            text = "this period",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun NudgeRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UncategorizedRow(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count == 1) "1 expense needs a category" else "$count expenses need a category",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.bpColors.statusOrange,
        )
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.bpColors.statusOrange)
    }
}

@Composable
private fun OverallPaceBar(summary: MonthSummary, pctUsed: Int) {
    // A continuous fill across the segments by how much of the TOTAL month's budget has been
    // spent (e.g. 81% used fills into period 3/4, not just period 1), colored by the month's
    // overall elapsed-time-adjusted pace status. Spec §5: color communicates status, no
    // "Week"/status words drawn on the bar itself.
    val overallFraction = if (summary.totalBudgetMinor > 0)
        (summary.totalSpentMinor.toFloat() / summary.totalBudgetMinor).coerceIn(0f, 1f)
    else 0f
    val overallColor = statusColor(summary.overallStatus)
    val segmentCount = summary.overallPeriods.size.coerceAtLeast(1)
    val currentIndex = summary.overallPeriods.firstOrNull { it.isCurrentPeriod }?.periodIndex
    val periodWord = if (currentIndex != null) "period ${currentIndex + 1} of $segmentCount, " else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Overall pace, $periodWord$pctUsed% used, ${statusWord(summary.overallStatus)}"
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        summary.overallPeriods.forEach { period ->
            val targetFraction = (overallFraction * segmentCount - period.periodIndex).coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(targetValue = targetFraction, animationSpec = tween(200), label = "pace")
            PaceSegment(
                fraction = animatedFraction,
                color = overallColor,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
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
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
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
private fun PaceSegment(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

private val CATEGORY_BAR_HEIGHT = 130.dp

/**
 * Spec §5: emoji, a scaled mark, amount, and percent — one tile per "4 periods" category (a
 * "start of month" category has nothing to pace, so it lives in [MoreCategoriesSection] instead).
 * The whole grid gets one shared border around it (see the caller) rather than one per tile.
 */
@Composable
private fun CategoryPaceItem(summary: CategorySummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pct = if (summary.category.monthlyBudgetMinor > 0)
        (summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor * 100).toInt().coerceAtLeast(0)
    else 0

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CategoryIcon(iconKey = summary.category.iconKey, name = summary.category.name, size = 28.dp)
        Spacer(modifier = Modifier.height(10.dp))
        CategoryPaceSegmentedMark(summary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Money.formatRupeesWhole(summary.totalSpentMinor),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.labelSmall,
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
 * catches up if nothing more is spent. The current period's cell (the category's OWN current
 * period, not the month grid's — a 2-period category is in its period 1 for the whole second
 * half of the month) gets a subtle highlight border so "which period am I in" is visible at a
 * glance.
 */
@Composable
private fun CategoryPaceSegmentedMark(summary: CategorySummary) {
    val currentPeriodIndex = summary.currentPeriodIndex
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
            val targetFraction = (overallFraction * periodCount - period.periodIndex).coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(targetValue = targetFraction, animationSpec = tween(200), label = "categoryPace")
            val isCurrent = period.periodIndex == currentPeriodIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(
                        if (isCurrent) Modifier.border(1.dp, highlightColor, RoundedCornerShape(3.dp))
                        else Modifier
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (animatedFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animatedFraction)
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
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
