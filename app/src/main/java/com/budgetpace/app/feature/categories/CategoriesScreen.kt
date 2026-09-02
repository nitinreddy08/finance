package com.budgetpace.app.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.model.CategorySummary
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.domain.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    budgetRepository: BudgetRepository
) : ViewModel() {
    
    // Passing empty string for phase testing
    val uiState: StateFlow<CategoriesUiState> = budgetRepository.observeMonthSummary(UUID.randomUUID().toString())
        .map { summary -> CategoriesUiState.Success(summary.categories) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState.Loading
        )
}

sealed interface CategoriesUiState {
    object Loading : CategoriesUiState
    data class Success(val categories: List<CategorySummary>) : CategoriesUiState
    object Error : CategoriesUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesRoute(
    viewModel: CategoriesViewModel,
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Add category */ }) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF15161A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF15161A)
    ) { innerPadding ->
        when (val state = uiState) {
            is CategoriesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFF15161A)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
                }
            }
            is CategoriesUiState.Success -> {
                CategoriesList(
                    categories = state.categories,
                    modifier = Modifier.padding(innerPadding),
                    onCategoryClick = onCategoryClick
                )
            }
            is CategoriesUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFF15161A)), contentAlignment = Alignment.Center) {
                    Text("Error loading categories", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CategoriesList(
    categories: List<CategorySummary>,
    modifier: Modifier = Modifier,
    onCategoryClick: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp) // Space for bottom nav
    ) {
        items(categories) { category ->
            CategoryMockupRow(
                summary = category,
                onClick = { onCategoryClick(category.category.id.toString()) }
            )
        }
    }
}

@Composable
fun CategoryMockupRow(
    summary: CategorySummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // Icon Placeholder
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
            Text(
                text = summary.category.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Money.formatRupeesWhole(summary.category.monthlyBudgetMinor),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = Money.formatRupeesWhole(summary.totalSpentMinor),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            val pct = if (summary.category.monthlyBudgetMinor > 0) 
                (summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor * 100).toInt() 
            else 0
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.width(40.dp)
            )
        }
    }
    Divider(color = Color(0xFF2A2D35))
}
