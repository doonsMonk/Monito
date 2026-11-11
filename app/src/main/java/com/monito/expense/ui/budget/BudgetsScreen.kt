package com.monito.expense.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.TimePeriods
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.EmptyState
import com.monito.expense.ui.SummaryRow
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.theme.LocalMonitoColors
import com.monito.expense.ui.title
import com.monito.expense.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

private data class BudgetRow(val category: Category, val budget: BigDecimal?, val spent: BigDecimal)

/** Monthly budget per expense category with progress for the current month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val changes by repo.changes.collectAsState()
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<BudgetRow>?>(null) }
    var editing by remember { mutableStateOf<BudgetRow?>(null) }
    val month = remember { YearMonth.now() }

    LaunchedEffect(changes) {
        rows = withContext(Dispatchers.IO) {
            val range = TimePeriods.wholeMonth(month)
            val budgets = repo.budgets().associateBy { it.catId }
            val spent = repo.categoryTotals(CatType.EXPENSE, range.from, range.to).associateBy { it.catId }
            repo.categories().filter { it.type == CatType.EXPENSE }.map { c ->
                BudgetRow(c, budgets[c.id]?.amount, spent[c.id]?.total ?: BigDecimal.ZERO)
            }.sortedWith(compareByDescending<BudgetRow> { it.budget != null }.thenBy { it.category.name.lowercase() })
        }
    }
    val currency = settings.currency
    val colors = LocalMonitoColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.budgets))
                        Text(month.title(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val list = rows ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.no_categories), stringResource(R.string.no_expense_categories), Modifier.padding(padding))
            return@Scaffold
        }
        val budgeted = list.filter { it.budget != null }
        val totalBudget = budgeted.fold(BigDecimal.ZERO) { a, r -> a + r.budget!! }
        val totalSpent = budgeted.fold(BigDecimal.ZERO) { a, r -> a + r.spent }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "summary") {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.budgets_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (budgeted.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            SummaryRow(stringResource(R.string.total_budget), AmountFormatter.format(totalBudget, currency))
                            SummaryRow(stringResource(R.string.total_spent), AmountFormatter.format(totalSpent, currency), colors.expense)
                            val left = totalBudget - totalSpent
                            SummaryRow(
                                stringResource(if (left.signum() >= 0) R.string.left_label else R.string.over_label),
                                AmountFormatter.format(left.abs(), currency),
                                if (left.signum() >= 0) colors.income else colors.expense,
                                bold = true,
                            )
                            Spacer(Modifier.height(6.dp))
                            BudgetBar(totalSpent, totalBudget)
                        }
                    }
                }
            }
            items(list, key = { it.category.id }) { row ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editing = row }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(row.category.colorCode, row.category.colorTextType, row.category.name)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.category.name, style = MaterialTheme.typography.bodyLarge)
                            val b = row.budget
                            Text(
                                if (b == null) stringResource(R.string.no_budget)
                                else stringResource(R.string.spent_of, AmountFormatter.format(row.spent, currency), AmountFormatter.format(b, currency)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val b = row.budget
                        if (b != null) {
                            val left = b - row.spent
                            Text(
                                if (left.signum() >= 0) stringResource(R.string.left, AmountFormatter.format(left, currency))
                                else stringResource(R.string.over_budget_by, AmountFormatter.format(left.abs(), currency)),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (left.signum() >= 0) colors.income else colors.expense,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else if (row.spent.signum() > 0) {
                            Text(AmountFormatter.format(row.spent, currency), style = MaterialTheme.typography.labelLarge, color = colors.expense)
                        }
                    }
                    if (row.budget != null) {
                        Spacer(Modifier.height(8.dp))
                        BudgetBar(row.spent, row.budget)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editing?.let { row ->
        var text by remember(row) { mutableStateOf(row.budget?.stripTrailingZeros()?.toPlainString() ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(row.category.name) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.monthly_budget)) },
                        prefix = { Text(currency.symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = AmountFormatter.parse(text, currency)
                    if (amount != null && amount.signum() > 0) {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.setBudget(row.category.id, amount) }
                            toast(context, R.string.budget_saved)
                        }
                    }
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    if (row.budget != null) {
                        TextButton(onClick = {
                            scope.launch { withContext(Dispatchers.IO) { repo.deleteBudget(row.category.id) } }
                            editing = null
                        }) { Text(stringResource(R.string.remove_budget), color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun BudgetBar(spent: BigDecimal, budget: BigDecimal) {
    val colors = LocalMonitoColors.current
    val ratio = if (budget.signum() > 0) spent.divide(budget, 4, RoundingMode.HALF_UP).toFloat() else 0f
    val color = when {
        ratio > 1f -> colors.expense
        ratio > 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> colors.income
    }
    LinearProgressIndicator(
        progress = { ratio.coerceIn(0f, 1f) },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
    )
}
