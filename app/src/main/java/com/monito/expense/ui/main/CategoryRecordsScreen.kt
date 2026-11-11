package com.monito.expense.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.Category
import com.monito.expense.data.Transaction
import com.monito.expense.ui.AmountText
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.EmptyState
import com.monito.expense.ui.Routes
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.transactionItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/** Records of one category inside the selected time period (the original FilteredRecordActivity). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRecordsScreen(nav: NavController, catId: Int) {
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val changes by repo.changes.collectAsState()
    val range = rememberPeriodRange(settings, repo, changes)
    var category by remember { mutableStateOf<Category?>(null) }
    var list by remember { mutableStateOf<List<Transaction>?>(null) }

    LaunchedEffect(range, changes) {
        val r = range ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { repo.category(catId) to repo.transactionsOfCategory(catId, r.from, r.to) }
        category = result.first
        list = result.second
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.let { stringResource(R.string.details, it.name) } ?: "") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val cat = category
        val items = list
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (cat != null && items != null) {
                item(key = "header") {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryAvatar(cat.colorCode, cat.colorTextType, cat.name)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                                Text(periodLabel(settings, range), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AmountText(items.fold(BigDecimal.ZERO) { a, t -> a + t.amount }, cat.type, settings.currency, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if (items.isEmpty()) {
                    item(key = "empty") { EmptyState(stringResource(R.string.no_transactions)) }
                } else {
                    transactionItems(items, settings.currency, onClick = { nav.navigate(Routes.record(it.id)) })
                }
            }
        }
    }
}
