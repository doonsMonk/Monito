package com.monito.expense.ui.record

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.Category
import com.monito.expense.data.Template
import com.monito.expense.ui.AmountText
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.CategoryPickerDialog
import com.monito.expense.ui.EmptyState
import com.monito.expense.ui.PickerField
import com.monito.expense.ui.Routes
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manage quick templates. Tapping one starts a new transaction prefilled from it; the pencil edits it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val changes by repo.changes.collectAsState()
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf<List<Template>?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var editing by remember { mutableStateOf<Template?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(changes) {
        val r = withContext(Dispatchers.IO) { repo.templates() to repo.categories() }
        templates = r.first
        categories = r.second
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_templates)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_template))
            }
        },
    ) { padding ->
        val list = templates ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.no_templates), stringResource(R.string.templates_desc), Modifier.padding(padding))
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
                        .clickable { nav.navigate(Routes.record(templateId = t.id)) }
                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryAvatar(t.colorCode, t.colorTextType, t.catName ?: t.name)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge)
                        val sub = listOfNotNull(t.catName, t.note.takeIf { it.isNotBlank() }).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (t.amount != null && t.catType != null) AmountText(t.amount, t.catType, settings.currency)
                    else if (t.amount != null) Text(AmountFormatter.format(t.amount, settings.currency), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { editing = t; showDialog = true }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_template)) }
                }
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showDialog) {
        TemplateDialog(
            template = editing,
            categories = categories,
            onDismiss = { showDialog = false },
            onSave = { name, amountText, cat, note ->
                val amount = AmountFormatter.parse(amountText, settings.currency)
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val e = editing
                        if (e != null) repo.updateTemplate(e.id, name, amount, cat?.id, note)
                        else repo.insertTemplate(name, amount, cat?.id, note)
                    }
                    toast(context, R.string.template_saved)
                }
            },
            onDelete = {
                val e = editing ?: return@TemplateDialog
                scope.launch {
                    withContext(Dispatchers.IO) { repo.deleteTemplate(e.id) }
                    toast(context, R.string.template_deleted)
                }
            },
        )
    }
}

@Composable
private fun TemplateDialog(
    template: Template?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, amountText: String, category: Category?, note: String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var amountText by remember { mutableStateOf(template?.amount?.stripTrailingZeros()?.toPlainString() ?: "") }
    var category by remember { mutableStateOf(categories.firstOrNull { it.id == template?.catId }) }
    var note by remember { mutableStateOf(template?.note ?: "") }
    var error by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (template == null) R.string.add_template else R.string.edit_template)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    label = { Text(stringResource(R.string.template_name)) },
                    isError = error,
                    supportingText = if (error) ({ Text(stringResource(R.string.error_template_name_cant_be_empty)) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.amount_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                PickerField(
                    value = category?.name ?: stringResource(R.string.none),
                    label = stringResource(R.string.category_optional),
                    modifier = Modifier.fillMaxWidth(),
                ) { showCategories = true }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { error = true; return@TextButton }
                onSave(name, amountText, category, note)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (template != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
    if (showCategories) {
        CategoryPickerDialog(categories, onDismiss = { showCategories = false }, allowNone = true) { category = it }
    }
}
