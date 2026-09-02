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
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF15161A)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            }
        }
        is DashboardUiState.Success -> {
            DashboardScreen(summary = state.summary)
        }
        is DashboardUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF15161A)), contentAlignment = Alignment.Center) {
                Text("Error loading dashboard", color = Color.White)
            }
        }
    }
}

@Composable
fun DashboardScreen(summary: MonthSummary) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF15161A)) // Dark background from mockup
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
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Change Month",
                        tint = Color.White,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color.White
                )
            }
        }
        
        // TOTAL REMAINING Card
        item {
            Surface(
                color = Color(0xFF1E1F24),
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
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Money.formatRupeesWhole(summary.remainingMinor),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "of ${Money.formatRupeesWhole(summary.budgetMinor)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                    
                    // Circular Progress
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                        CircularProgressIndicator(
                            progress = 1f,
                            color = Color(0xFF2A2D35),
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        val ratio = if (summary.budgetMinor > 0) summary.remainingMinor.toFloat() / summary.budgetMinor else 0f
                        CircularProgressIndicator(
                            progress = ratio.coerceIn(0f, 1f),
                            color = Color(0xFF4CAF50),
                            strokeWidth = 6.dp,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.fillMaxSize()
                        )
                        val pct = (ratio * 100).toInt().coerceIn(0, 100)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${pct}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("left", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        
        // SAFE TO SPEND TODAY Card
        item {
            Surface(
                color = Color(0xFF1E1F24),
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
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Money.formatRupeesWhole(summary.safeToSpendMinor),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Based on time passed & budget pace",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    // Placeholder for Line Chart sparkline
                    Box(modifier = Modifier.width(80.dp).height(40.dp), contentAlignment = Alignment.Center) {
                        // Drawing a simple mock sparkline for now
                        Text("〰〰〰", color = Color(0xFF4CAF50), fontSize = 24.sp)
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
                    color = Color.Gray,
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
                    color = Color.Gray,
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
        BudgetStatus.GREY -> Color.Gray
        BudgetStatus.CURRENT -> Color(0xFF64B5F6)
    }
    
    Surface(
        color = Color(0xFF1E1F24),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "P${period.periodIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            Text(
                text = "Week ${period.periodIndex + 1}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (period.spentMinor > 0) Money.formatRupeesWhole(period.spentMinor) else "₹0",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
            Text(
                text = "of ${Money.formatRupeesWhole(period.budgetMinor)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            val pct = if (period.budgetMinor > 0) (period.spentMinor.toFloat() / period.budgetMinor * 100).toInt() else 0
            Text(
                text = if (period.spentMinor > 0) "${pct}%" else "0%",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
fun CategoryMockupItem(summary: CategorySummary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E7D32).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("🛒", fontSize = 18.sp)
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
                    color = Color.White
                )
                Text(
                    text = "${Money.formatRupeesWhole(summary.totalSpentMinor)} / ${Money.formatRupeesWhole(summary.category.monthlyBudgetMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val ratio = if (summary.category.monthlyBudgetMinor > 0) summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor else 0f
            LinearProgressIndicator(
                progress = ratio.coerceIn(0f, 1f),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFF2A2D35),
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        }
    }
}
