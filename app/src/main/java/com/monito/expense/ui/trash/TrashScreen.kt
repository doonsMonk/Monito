package com.monito.expense.ui.trash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.TrashItem
import com.monito.expense.ui.AmountText
import com.monito.expense.ui.ConfirmDialog
import com.monito.expense.ui.EmptyState
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.short
import com.monito.expense.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val changes by repo.changes.collectAsState()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TrashItem>?>(null) }
    var selected by remember { mutableStateOf<TrashItem?>(null) }
    var showClear by remember { mutableStateOf(false) }
    LaunchedEffect(changes) { items = withContext(Dispatchers.IO) { repo.trashItems() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    if (!items.isNullOrEmpty()) {
                        IconButton(onClick = { showClear = true }) { Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_trash)) }
                    }
                },
            )
        },
    ) { padding ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.trash_is_empty), stringResource(R.string.msg_empty_trash), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(list, key = { it.id }) { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = t }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.catName, style = MaterialTheme.typography.bodyLarge)
                        if (t.note.isNotBlank()) {
                            Text(t.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            t.date.short() + (t.deletedOn?.let { "  •  " + stringResource(R.string.deleted_on, it.short()) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    AmountText(t.amount, t.catType, settings.currency)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }

    val sel = selected
    if (sel != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(R.string.choose_action)) },
            text = { Text(sel.catName + if (sel.note.isNotBlank()) "\n${sel.note}" else "") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.restoreFromTrash(sel.id) }
                        toast(context, R.string.transaction_restored)
                    }
                    selected = null
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.deleteFromTrash(sel.id) }
                        toast(context, R.string.transaction_deleted_permanently)
                    }
                    selected = null
                }) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
    if (showClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_trash),
            text = stringResource(R.string.clear_all_records_from_trash),
            confirmLabel = stringResource(R.string.delete_forever),
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.clearTrash() }
                    toast(context, R.string.trash_cleared)
                }
            },
            onDismiss = { showClear = false },
        )
    }
}
