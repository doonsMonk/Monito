package com.monito.expense.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.SearchFilter
import com.monito.expense.data.Transaction
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.EmptyState
import com.monito.expense.ui.MonitoDatePickerDialog
import com.monito.expense.ui.Routes
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.short
import com.monito.expense.ui.transactionItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(nav: NavController) {
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val changes by repo.changes.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCategories by remember { mutableStateOf(false) }
    var showAmount by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(changes) {
        val r = withContext(Dispatchers.IO) { repo.categories() to repo.tags() }
        categories = r.first
        tags = r.second
    }
    val effective = filter.copy(query = query)
    LaunchedEffect(effective, changes) {
        if (effective.isEmpty) {
            results = emptyList()
        } else {
            delay(200)
            results = withContext(Dispatchers.IO) { repo.search(effective) }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val filterActive = !filter.isEmpty

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_transactions)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search)) }
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.filters),
                            tint = if (filterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedVisibility(visible = showFilters || filterActive) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter.type == CatType.EXPENSE,
                            onClick = { filter = filter.copy(type = if (filter.type == CatType.EXPENSE) null else CatType.EXPENSE) },
                            label = { Text(stringResource(R.string.expense)) },
                        )
                        FilterChip(
                            selected = filter.type == CatType.INCOME,
                            onClick = { filter = filter.copy(type = if (filter.type == CatType.INCOME) null else CatType.INCOME) },
                            label = { Text(stringResource(R.string.income)) },
                        )
                        FilterChip(
                            selected = filter.catIds.isNotEmpty(),
                            onClick = { showCategories = true },
                            label = {
                                Text(
                                    if (filter.catIds.isEmpty()) stringResource(R.string.categories)
                                    else if (filter.catIds.size == 1) categories.firstOrNull { it.id == filter.catIds.first() }?.name ?: ""
                                    else stringResource(R.string.categories_selected, filter.catIds.size),
                                )
                            },
                        )
                        FilterChip(
                            selected = filter.minAmount != null || filter.maxAmount != null,
                            onClick = { showAmount = true },
                            label = {
                                val c = settings.currency
                                val txt = when {
                                    filter.minAmount != null && filter.maxAmount != null -> "${AmountFormatter.format(filter.minAmount!!, c)} – ${AmountFormatter.format(filter.maxAmount!!, c)}"
                                    filter.minAmount != null -> "≥ ${AmountFormatter.format(filter.minAmount!!, c)}"
                                    filter.maxAmount != null -> "≤ ${AmountFormatter.format(filter.maxAmount!!, c)}"
                                    else -> stringResource(R.string.amount_range)
                                }
                                Text(txt)
                            },
                        )
                        FilterChip(
                            selected = filter.from != null,
                            onClick = { pickFrom = true },
                            label = { Text(filter.from?.short()?.let { "$it →" } ?: stringResource(R.string.start_date)) },
                        )
                        FilterChip(
                            selected = filter.to != null,
                            onClick = { pickTo = true },
                            label = { Text(filter.to?.short()?.let { "→ $it" } ?: stringResource(R.string.end_date)) },
                        )
                        FilterChip(
                            selected = filter.withPhoto,
                            onClick = { filter = filter.copy(withPhoto = !filter.withPhoto) },
                            label = { Text(stringResource(R.string.with_photo)) },
                        )
                    }
                    if (tags.isNotEmpty()) {
                        Text(stringResource(R.string.tags), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (tag in tags.take(20)) {
                                FilterChip(
                                    selected = filter.tag == tag,
                                    onClick = { filter = filter.copy(tag = if (filter.tag == tag) null else tag) },
                                    label = { Text(tag) },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!effective.isEmpty) {
                            Text(stringResource(R.string.results_count, results.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.weight(1f))
                        if (filterActive) TextButton(onClick = { filter = SearchFilter() }) { Text(stringResource(R.string.clear_filters)) }
                    }
                    HorizontalDivider()
                }
            }
            if (!effective.isEmpty && results.isEmpty()) {
                EmptyState(stringResource(R.string.no_transactions))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    transactionItems(results, settings.currency, onClick = { nav.navigate(Routes.record(it.id)) })
                }
            }
        }
    }

    if (showCategories) {
        AlertDialog(
            onDismissRequest = { showCategories = false },
            title = { Text(stringResource(R.string.categories)) },
            text = {
                LazyColumn {
                    items(categories.size) { i ->
                        val c = categories[i]
                        val checked = c.id in filter.catIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { on -> filter = filter.copy(catIds = if (on) filter.catIds + c.id else filter.catIds - c.id) })
                            CategoryAvatar(c.colorCode, c.colorTextType, c.name, 28.dp)
                            Spacer(Modifier.padding(4.dp))
                            Text(c.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategories = false }) { Icon(Icons.Default.Check, null); Text(stringResource(R.string.okay)) } },
            dismissButton = { TextButton(onClick = { filter = filter.copy(catIds = emptySet()); showCategories = false }) { Text(stringResource(R.string.clear)) } },
        )
    }
    if (showAmount) {
        var minText by remember { mutableStateOf(filter.minAmount?.toPlainString() ?: "") }
        var maxText by remember { mutableStateOf(filter.maxAmount?.toPlainString() ?: "") }
        AlertDialog(
            onDismissRequest = { showAmount = false },
            title = { Text(stringResource(R.string.amount_range)) },
            text = {
                Column {
                    OutlinedTextField(value = minText, onValueChange = { minText = it }, label = { Text(stringResource(R.string.min_amount)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = maxText, onValueChange = { maxText = it }, label = { Text(stringResource(R.string.max_amount)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    filter = filter.copy(
                        minAmount = AmountFormatter.parse(minText, settings.currency),
                        maxAmount = AmountFormatter.parse(maxText, settings.currency),
                    )
                    showAmount = false
                }) { Text(stringResource(R.string.okay)) }
            },
            dismissButton = { TextButton(onClick = { filter = filter.copy(minAmount = null, maxAmount = null); showAmount = false }) { Text(stringResource(R.string.clear)) } },
        )
    }
    if (pickFrom) MonitoDatePickerDialog(filter.from ?: LocalDate.now().withDayOfMonth(1), onDismiss = { pickFrom = false }) { filter = filter.copy(from = it) }
    if (pickTo) MonitoDatePickerDialog(filter.to ?: LocalDate.now(), onDismiss = { pickTo = false }) { filter = filter.copy(to = it) }
}
