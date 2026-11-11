package com.monito.expense.ui.category

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.EmptyState
import com.monito.expense.ui.Routes
import com.monito.expense.ui.amountColor
import com.monito.expense.ui.rememberRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(nav: NavController) {
    val repo = rememberRepository()
    val changes by repo.changes.collectAsState()
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    LaunchedEffect(changes) { categories = withContext(Dispatchers.IO) { repo.categories() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.category()) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_category))
            }
        },
    ) { padding ->
        val list = categories ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.no_categories), stringResource(R.string.msg_to_add_cat), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(list, key = { it.id }) { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate(Routes.category(c.id)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryAvatar(c.colorCode, c.colorTextType, c.name)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        Row {
                            Text(
                                stringResource(if (c.type == CatType.EXPENSE) R.string.expense else R.string.income),
                                style = MaterialTheme.typography.bodySmall,
                                color = amountColor(c.type),
                            )
                            Text(
                                "  •  " + stringResource(R.string.transactions_count, c.count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (c.isFavorite) Icon(Icons.Default.Star, contentDescription = stringResource(R.string.favorite_category), tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}
