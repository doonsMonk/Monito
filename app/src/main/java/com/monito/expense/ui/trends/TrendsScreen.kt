package com.monito.expense.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.MonthTotal
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.PickerField
import com.monito.expense.ui.SummaryRow
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.shortTitle
import com.monito.expense.ui.theme.LocalMonitoColors
import com.monito.expense.ui.theme.categoryColor
import com.monito.expense.ui.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/** A bar series for the chart: one value per month, drawn in [color]. */
data class BarSeries(val label: String, val color: Color, val values: List<BigDecimal>)

/** Grouped bar chart: one group per month, one bar per series. */
@Composable
fun MonthlyBarChart(months: List<YearMonth>, series: List<BarSeries>, modifier: Modifier = Modifier) {
    val max = series.flatMap { it.values }.maxOfOrNull { it.toFloat() }?.takeIf { it > 0f } ?: 1f
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val groups = months.size
            if (groups == 0) return@Canvas
            val groupW = size.width / groups
            val barW = groupW * 0.7f / series.size.coerceAtLeast(1)
            val chartH = size.height
            for (i in 0..4) {
                val y = chartH - chartH * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            for (g in 0 until groups) {
                val groupStart = g * groupW + groupW * 0.15f
                series.forEachIndexed { s, ser ->
                    val v = ser.values.getOrNull(g)?.toFloat() ?: 0f
                    val h = chartH * (v / max)
                    drawRect(
                        ser.color,
                        topLeft = Offset(groupStart + s * barW, chartH - h),
                        size = Size(barW * 0.9f, h),
                    )
                }
            }
            drawLine(grid, Offset(0f, chartH), Offset(size.width, chartH), strokeWidth = 2f)
        }
        Row(Modifier.fillMaxWidth()) {
            for (m in months) {
                Text(
                    m.shortTitle().replace(" ", "\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
            for (s in series) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .padding(0.dp),
                    ) { Canvas(Modifier.fillMaxSize()) { drawRect(s.color) } }
                    Spacer(Modifier.width(6.dp))
                    Text(s.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Income vs expense over 12 months, per-category history and a yearly summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(nav: NavController) {
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val changes by repo.changes.collectAsState()
    var endMonth by remember { mutableStateOf(YearMonth.now()) }
    val startMonth = endMonth.minusMonths(11)
    var totals by remember { mutableStateOf<List<MonthTotal>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var category by remember { mutableStateOf<Category?>(null) }
    var categoryTotals by remember { mutableStateOf<List<Pair<YearMonth, BigDecimal>>>(emptyList()) }
    var yearTotals by remember { mutableStateOf<List<MonthTotal>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }
    val colors = LocalMonitoColors.current
    val currency = settings.currency

    LaunchedEffect(endMonth, changes) {
        val r = withContext(Dispatchers.IO) {
            Triple(
                repo.monthlyTotals(startMonth, endMonth),
                repo.categories(),
                repo.monthlyTotals(YearMonth.of(endMonth.year, 1), YearMonth.of(endMonth.year, 12)),
            )
        }
        totals = r.first
        categories = r.second
        yearTotals = r.third
    }
    LaunchedEffect(category, endMonth, changes) {
        val c = category
        categoryTotals = if (c == null) emptyList() else withContext(Dispatchers.IO) { repo.categoryMonthlyTotals(c.id, startMonth, endMonth) }
    }

    val months = totals.map { it.month }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trends)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "range") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { endMonth = endMonth.minusMonths(12) }) { Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.previous_year)) }
                    Text(
                        "${startMonth.title()} – ${endMonth.title()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { endMonth = endMonth.plusMonths(12) }, enabled = endMonth.isBefore(YearMonth.now())) {
                        Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.next_year))
                    }
                }
            }
            item(key = "chart") {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.income_vs_expense), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))
                        MonthlyBarChart(
                            months = months,
                            series = listOf(
                                BarSeries(stringResource(R.string.income), colors.income, totals.map { it.income }),
                                BarSeries(stringResource(R.string.expense), colors.expense, totals.map { it.expense }),
                            ),
                        )
                        val withData = totals.count { it.income.signum() > 0 || it.expense.signum() > 0 }
                        if (withData > 0) {
                            Spacer(Modifier.height(12.dp))
                            val avgExpense = totals.fold(BigDecimal.ZERO) { a, t -> a + t.expense }.divide(BigDecimal(withData), 2, RoundingMode.HALF_UP)
                            val avgIncome = totals.fold(BigDecimal.ZERO) { a, t -> a + t.income }.divide(BigDecimal(withData), 2, RoundingMode.HALF_UP)
                            Text(stringResource(R.string.months_with_data, withData), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SummaryRow(stringResource(R.string.average) + " · " + stringResource(R.string.income), AmountFormatter.format(avgIncome, currency), colors.income)
                            SummaryRow(stringResource(R.string.average) + " · " + stringResource(R.string.expense), AmountFormatter.format(avgExpense, currency), colors.expense)
                        }
                    }
                }
            }
            item(key = "category") {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.by_category), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Box {
                            PickerField(
                                value = category?.name ?: stringResource(R.string.select_category),
                                label = stringResource(R.string.category),
                                modifier = Modifier.fillMaxWidth(),
                            ) { menu = true }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                for (c in categories) {
                                    DropdownMenuItem(
                                        text = { Text(c.name) },
                                        leadingIcon = { CategoryAvatar(c.colorCode, c.colorTextType, c.name, 28.dp) },
                                        onClick = { category = c; menu = false },
                                    )
                                }
                            }
                        }
                        val c = category
                        if (c != null && categoryTotals.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            MonthlyBarChart(
                                months = categoryTotals.map { it.first },
                                series = listOf(BarSeries(c.name, categoryColor(c.colorCode), categoryTotals.map { it.second })),
                            )
                            val sum = categoryTotals.fold(BigDecimal.ZERO) { a, p -> a + p.second }
                            val withData = categoryTotals.count { it.second.signum() > 0 }
                            Spacer(Modifier.height(8.dp))
                            SummaryRow(stringResource(R.string.total), AmountFormatter.format(sum, currency), if (c.type == CatType.EXPENSE) colors.expense else colors.income)
                            if (withData > 0) {
                                SummaryRow(stringResource(R.string.average), AmountFormatter.format(sum.divide(BigDecimal(withData), 2, RoundingMode.HALF_UP), currency))
                            }
                        }
                    }
                }
            }
            item(key = "year") {
                val income = yearTotals.fold(BigDecimal.ZERO) { a, t -> a + t.income }
                val expense = yearTotals.fold(BigDecimal.ZERO) { a, t -> a + t.expense }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.year_summary) + " ${endMonth.year}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        SummaryRow(stringResource(R.string.total_income), AmountFormatter.format(income, currency), colors.income)
                        SummaryRow(stringResource(R.string.total_expense), AmountFormatter.format(expense, currency), colors.expense)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SummaryRow(stringResource(R.string.balance), AmountFormatter.format(income - expense, currency), bold = true)
                    }
                }
            }
            items(totals.reversed(), key = { it.month.toString() }) { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.month.title(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(AmountFormatter.format(t.income, currency), color = colors.income, style = MaterialTheme.typography.bodyMedium)
                        Text(AmountFormatter.format(t.expense, currency), color = colors.expense, style = MaterialTheme.typography.bodyMedium)
                        Text(AmountFormatter.format(t.balance, currency), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
