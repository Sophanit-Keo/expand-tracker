package com.expensetracker.ui.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
import com.expensetracker.data.Expense
import com.expensetracker.ui.AppViewModelProvider
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.ExpenseRow
import com.expensetracker.ui.components.PeriodNavigator
import com.expensetracker.ui.components.icon
import com.expensetracker.ui.theme.CashInText
import com.expensetracker.ui.theme.card
import com.expensetracker.util.formatMoney
import com.expensetracker.util.friendlyDate
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenExpense: (Long) -> Unit,
    onImport: () -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val haptics = LocalHapticFeedback.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    IconButton(onClick = onImport, modifier = Modifier.testTag("history_import")) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = "Import bank statement")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item {
                AnimatedContent(targetState = state.searchingAllMonths, label = "periodHeader") { searching ->
                    if (searching) {
                        Text(
                            "Results from all months",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        )
                    } else {
                        PeriodNavigator(
                            label = state.month.format(monthFormatter),
                            onPrevious = viewModel::previousMonth,
                            onNext = viewModel::nextMonth,
                            canGoNext = state.canGoNext,
                        )
                    }
                }
                Text(
                    buildList {
                        if (state.spent > 0 || state.received == 0.0) add("−${formatMoney(state.spent, state.currency)} out")
                        if (state.received > 0) add("+${formatMoney(state.received, state.currency)} in")
                        add("${state.count} ${if (state.count == 1) "transaction" else "transactions"}")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                SearchField(state.query, viewModel::setQuery)
                Spacer(Modifier.height(12.dp))
            }
            item {
                TypeFilter(state.type, viewModel::setType)
                Spacer(Modifier.height(8.dp))
                CategoryFilters(state.category, state.type, viewModel::setCategory)
                Spacer(Modifier.height(8.dp))
            }

            if (!state.loading && state.groups.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = if (state.isFiltered) "No matches" else "Nothing here yet",
                        message = if (state.searchingAllMonths) "Nothing matches “${state.query.trim()}” in any month."
                        else if (state.isFiltered) "Try a different search or category."
                        else "No expenses recorded in ${state.month.format(monthFormatter)}.",
                    )
                }
            }

            state.groups.forEach { group ->
                item(key = "header-${group.date}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    ) {
                        Text(
                            friendlyDate(group.date),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (group.received > 0) {
                            Text(
                                "+" + formatMoney(group.received, state.currency),
                                style = MaterialTheme.typography.labelLarge,
                                color = CashInText,
                            )
                        }
                        if (group.received > 0 && group.spent > 0) Spacer(Modifier.size(10.dp))
                        if (group.spent > 0) {
                            Text(
                                "−" + formatMoney(group.spent, state.currency),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                group.items.forEachIndexed { index, expense ->
                    item(key = expense.id) {
                        SwipeToDeleteRow(
                            expense = expense,
                            currency = state.currency,
                            shape = groupShape(index, group.items.size),
                            onClick = { onOpenExpense(expense.id) },
                            onDelete = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.delete(expense)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result = snackbar.showSnackbar(
                                        message = "Deleted “${expense.title}”",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/** Rows of one day share a card: round the outer corners only. */
private fun groupShape(index: Int, count: Int): Shape {
    val big = 20.dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search notes or categories") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun TypeFilter(selected: EntryType?, onSelect: (EntryType?) -> Unit) {
    val options = listOf(null to "All", EntryType.EXPENSE to "Spending", EntryType.INCOME to "Income")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun CategoryFilters(selected: Category?, type: EntryType?, onSelect: (Category?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                shape = CircleShape,
            )
        }
        items(if (type == null) Category.entries else Category.of(type)) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category.label) },
                leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun SwipeToDeleteRow(
    expense: Expense,
    currency: AppCurrency,
    shape: Shape,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.padding(vertical = 1.dp),
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Box(
            Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.card),
        ) {
            ExpenseRow(
                expense = expense,
                currency = currency,
                supportingText = expense.category.label,
                onClick = onClick,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
