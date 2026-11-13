package com.monito.expense.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.ColorItem
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.ConfirmDialog
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.theme.categoryColor
import com.monito.expense.ui.theme.categoryTextColor
import com.monito.expense.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoryScreen(nav: NavController, categoryId: Int) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val editing = categoryId >= 0

    var loaded by remember { mutableStateOf(!editing) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CatType.EXPENSE) }
    var colorId by remember { mutableStateOf(2) }
    var favorite by remember { mutableStateOf(false) }
    var colors by remember { mutableStateOf<List<ColorItem>>(emptyList()) }
    var nameError by remember { mutableStateOf<Int?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(categoryId) {
        val result = withContext(Dispatchers.IO) { repo.colors() to (if (editing) repo.category(categoryId) else null) }
        val leastUsed = withContext(Dispatchers.IO) { repo.leastUsedColorId() }
        colors = result.first
        result.second?.let { c ->
            existing = c
            name = c.name
            type = c.type
            colorId = c.colorId
            favorite = c.isFavorite
        }
        if (!editing) colorId = leastUsed ?: colors.firstOrNull()?.id ?: colorId
        loaded = true
    }

    fun save() {
        val trimmed = name.trim()
        nameError = when {
            trimmed.isEmpty() -> R.string.error_cat_name_cant_be_empty
            else -> null
        }
        if (nameError != null) return
        scope.launch {
            val duplicate = withContext(Dispatchers.IO) {
                repo.categoryNameExists(trimmed, if (editing) categoryId else null)
            }
            if (duplicate) {
                nameError = R.string.error_cat_already_exists
                return@launch
            }
            withContext(Dispatchers.IO) {
                if (editing) repo.updateCategory(categoryId, trimmed, colorId, favorite)
                else repo.insertCategory(trimmed, type, colorId, favorite)
            }
            toast(context, if (editing) R.string.category_modified else R.string.category_added)
            nav.popBackStack()
        }
    }

    val selectedColor = colors.firstOrNull { it.id == colorId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing) R.string.edit_category else R.string.add_category)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    if (editing) IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_category)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { save() }) { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save)) }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(selectedColor?.code ?: "9E9E9E", selectedColor?.textType ?: 1, name.ifBlank { "?" }, 56.dp)
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.category_name)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(stringResource(it)) } },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.category_type), style = MaterialTheme.typography.labelLarge)
            Row {
                for ((t, label) in listOf(CatType.EXPENSE to R.string.expense, CatType.INCOME to R.string.income)) {
                    Row(
                        Modifier
                            .selectable(selected = type == t, enabled = !editing, onClick = { type = t })
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = type == t, onClick = { type = t }, enabled = !editing)
                        Text(stringResource(label))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.choose_color), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((colors.size + 4) / 5 * 64).coerceAtLeast(64).dp),
                userScrollEnabled = false,
            ) {
                items(colors, key = { it.id }) { c ->
                    val selected = c.id == colorId
                    // Outer box absorbs the grid cell width so the swatch itself stays a 48dp circle.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(categoryColor(c.code), CircleShape)
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { colorId = c.id },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Default.Check, null, tint = categoryTextColor(c.textType))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.favorite_category), modifier = Modifier.weight(1f))
                Switch(checked = favorite, onCheckedChange = { favorite = it })
            }
            Spacer(Modifier.height(88.dp))
        }
    }

    if (showDelete) {
        val fallback = if (type == CatType.INCOME) Category.OTHER_INCOMES else Category.OTHER_EXPENSES
        ConfirmDialog(
            title = stringResource(R.string.delete_category),
            text = stringResource(R.string.delete_this_category_records_will_be_moved, fallback),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.deleteCategory(categoryId) }
                    toast(context, R.string.category_deleted)
                    nav.popBackStack()
                }
            },
            onDismiss = { showDelete = false },
        )
    }
}
