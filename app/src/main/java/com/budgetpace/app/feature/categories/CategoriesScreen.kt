package com.budgetpace.app.feature.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.mapper.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    categoryDao: CategoryDao
) : ViewModel() {

    // Passing empty string for phase testing
    val uiState: StateFlow<CategoriesUiState> = categoryDao.observeByMonth("")
        .map { list -> CategoriesUiState.Success(list.map { it.toDomain() }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState.Loading
        )
}

sealed interface CategoriesUiState {
    object Loading : CategoriesUiState
    data class Success(val categories: List<Category>) : CategoriesUiState
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
                title = { Text("Categories") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.bpColors.background,
                    titleContentColor = MaterialTheme.bpColors.textPrimary
                )
            )
        },
        containerColor = MaterialTheme.bpColors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Add category */ },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("+")
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is CategoriesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }
            is CategoriesUiState.Success -> {
                CategoriesList(
                    categories = state.categories,
                    modifier = Modifier.padding(innerPadding),
                    onCategoryClick = onCategoryClick
                )
            }
            is CategoriesUiState.Error -> {}
        }
    }
}

@Composable
fun CategoriesList(
    categories: List<Category>,
    modifier: Modifier = Modifier,
    onCategoryClick: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(categories) { category ->
            CategoryCard(
                category = category,
                onClick = { onCategoryClick(category.id.toString()) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCard(
    category: Category,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.bpColors.surface),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.bpColors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.bpColors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (category.weeklyPacingEnabled) "4 Periods" else "Monthly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.bpColors.textSecondary
                )
            }
            
            Text(
                text = Money.formatRupeesWhole(category.monthlyBudgetMinor),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.bpColors.textPrimary
            )
        }
    }
}
