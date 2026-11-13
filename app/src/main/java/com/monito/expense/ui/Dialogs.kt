package com.monito.expense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.ColorItem
import com.monito.expense.data.CurrencySettings
import com.monito.expense.ui.theme.categoryColor
import com.monito.expense.ui.theme.categoryTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

private const val OPERATORS = "+-×÷"

/** Text for the calculator display that represents [amount] with the user's decimal separator. */
fun amountPadText(amount: BigDecimal?, currency: CurrencySettings): String =
    amount?.stripTrailingZeros()?.toPlainString()?.replace('.', currency.decimalSeparator) ?: ""

/** Applies one key of the calculator to the expression shown in its display. */
internal fun amountPadInput(expr: String, key: String, decimal: Char): String {
    val last = expr.lastOrNull()
    return when {
        key.length == 1 && key[0] in OPERATORS -> when {
            expr.isEmpty() -> if (key == "-") key else expr
            last != null && last in OPERATORS -> if (expr.length == 1) expr else expr.dropLast(1) + key
            else -> expr + key
        }
        key == "." -> {
            val number = expr.takeLastWhile { it !in OPERATORS }
            when {
                decimal in number -> expr
                number.isEmpty() -> expr + "0" + decimal
                else -> expr + decimal
            }
        }
        key == "00" -> if (expr.isEmpty() || (last != null && last in OPERATORS)) expr + "0" else expr + key
        else -> expr + key
    }
}

/**
 * The calculator of the original app: a number pad with + - × ÷. OKAY evaluates the expression and
 * returns it through [onDone] when it is a positive amount.
 */
@Composable
fun AmountPadDialog(
    initial: String,
    currency: CurrencySettings,
    onDismiss: () -> Unit,
    dismissOnOutside: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    onDone: (BigDecimal) -> Unit,
) {
    var expr by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<Int?>(null) }

    fun press(key: String) {
        expr = amountPadInput(expr, key, currency.decimalSeparator)
        error = null
    }

    fun done() {
        val trimmed = expr.trimEnd { it in OPERATORS }
        val amount = AmountFormatter.parse(trimmed, currency)
        error = when {
            trimmed.isBlank() -> R.string.error_amt_cant_be_empty
            amount == null -> R.string.error_amt_is_invalid
            amount.signum() <= 0 -> R.string.error_amt_should_greater_than_zero
            else -> null
        }
        if (error == null && amount != null) onDone(amount.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = dismissOnOutside),
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column {
                if (header != null) header()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 16.dp, top = 20.dp, bottom = 20.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = expr.ifEmpty { "0" },
                        style = MaterialTheme.typography.displaySmall,
                        color = if (expr.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                            .padding(end = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                    IconButton(onClick = { expr = expr.dropLast(1); error = null }) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.delete))
                    }
                }
                error?.let {
                    Text(
                        stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                val rows = listOf(
                    listOf("1", "2", "3", "÷"),
                    listOf("4", "5", "6", "×"),
                    listOf("7", "8", "9", "-"),
                    listOf(".", "0", "00", "+"),
                )
                for (row in rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (key in row) {
                            val isOperator = key[0] in OPERATORS
                            Box(
                                modifier = Modifier
                                    .weight(if (isOperator) 0.9f else 1f)
                                    .height(62.dp)
                                    .background(if (isOperator) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface)
                                    .clickable { press(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (key == ".") currency.decimalSeparator.toString() else key,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { expr = ""; error = null }) {
                        Text(stringResource(R.string.clear).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { done() }) { Text(stringResource(R.string.okay).uppercase(), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Colour swatches of the category palette; the selected one is ringed and ticked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatches(colors: List<ColorItem>, selectedId: Int, swatch: androidx.compose.ui.unit.Dp = 40.dp, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (c in colors) {
            val selected = c.id == selectedId
            Box(
                modifier = Modifier
                    .size(swatch)
                    .background(categoryColor(c.code), CircleShape)
                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(c.id) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, null, tint = categoryTextColor(c.textType))
            }
        }
    }
}

/** Creates a category without leaving the current screen; used from the category picker. */
@Composable
fun AddCategoryDialog(defaultType: CatType = CatType.EXPENSE, onDismiss: () -> Unit, onAdded: (Category) -> Unit) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(defaultType) }
    var colorId by rememberSaveable { mutableStateOf(-1) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var colors by remember { mutableStateOf<List<ColorItem>>(emptyList()) }
    var nameError by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { repo.colors() to repo.leastUsedColorId() }
        colors = result.first
        if (colorId == -1) colorId = result.second ?: colors.firstOrNull()?.id ?: -1
    }

    fun save() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            nameError = R.string.error_cat_name_cant_be_empty
            return
        }
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                if (repo.categoryNameExists(trimmed)) null
                else repo.category(repo.insertCategory(trimmed, type, colorId, favorite).toInt())
            }
            if (created == null) nameError = R.string.error_cat_already_exists else onAdded(created)
        }
    }

    val selected = colors.firstOrNull { it.id == colorId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_category)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(selected?.code ?: "9E9E9E", selected?.textType ?: 1, name.ifBlank { "?" }, 44.dp)
                    Spacer(Modifier.width(12.dp))
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
                Row {
                    for ((t, label) in listOf(CatType.EXPENSE to R.string.expense, CatType.INCOME to R.string.income)) {
                        Row(
                            Modifier
                                .selectable(selected = type == t, onClick = { type = t })
                                .padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = type == t, onClick = { type = t })
                            Text(stringResource(label))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.choose_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorSwatches(colors, colorId, 36.dp) { colorId = it }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.favorite_category), modifier = Modifier.weight(1f))
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
