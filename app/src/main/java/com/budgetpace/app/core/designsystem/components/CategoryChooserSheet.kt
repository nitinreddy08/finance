package com.budgetpace.app.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetpace.app.core.model.Category
import kotlinx.coroutines.launch

/**
 * Every active category (emoji + name) plus, when [onDontRecord] is given, a "Don't record" row.
 * Shared by the transaction detail screen, Add expense, and the category delete/reassign flow —
 * anywhere the owner picks a category or opts an expense out of budgeting (spec §21/§43/§45).
 *
 * Dismissal always plays the sheet's hide animation before the caller's [onDismiss] runs: a tap
 * that picks a category or "Don't record" calls `sheetState.hide()` and only then invokes the
 * callback, so the sheet never just vanishes mid-tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChooserSheet(
    categories: List<Category>,
    onSelectCategory: (Category) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDontRecord: (() -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    val scope = rememberCoroutineScope()
    fun dismissThen(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Text(
                    text = "Choose a category",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            items(categories, key = { it.id }) { category ->
                CategoryChooserRow(
                    category = category,
                    onClick = { dismissThen { onSelectCategory(category) } },
                )
            }
            val dontRecordAction = onDontRecord
            if (dontRecordAction != null) {
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    DontRecordRow(onClick = { dismissThen(dontRecordAction) })
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun CategoryChooserRow(category: Category, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(iconKey = category.iconKey, name = category.name, size = 36.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DontRecordRow(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Don't record",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "This expense won't count toward any budget.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
