package com.monito.expense.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.BackupManager
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.CurrencySettings
import com.monito.expense.data.Prefs
import com.monito.expense.data.Repository
import com.monito.expense.data.Transaction
import com.monito.expense.ui.theme.LocalMonitoColors
import com.monito.expense.ui.theme.categoryColor
import com.monito.expense.ui.theme.categoryTextColor
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun rememberRepository(): Repository {
    val context = LocalContext.current
    return remember { Repository.get(context) }
}

@Composable
fun rememberPrefs(): Prefs {
    val context = LocalContext.current
    return remember { Prefs.get(context) }
}

@Composable
fun rememberBackupManager(): BackupManager {
    val context = LocalContext.current
    val repo = rememberRepository()
    return remember { BackupManager(context.applicationContext, repo) }
}

fun toast(context: Context, resId: Int) {
    Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
}

fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
private val SHORT_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val SHORT_MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())

@Composable
fun LocalDate.pretty(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> format(DAY_FORMAT)
    }
}

fun LocalDate.short(): String = format(SHORT_DAY_FORMAT)
fun YearMonth.title(): String = format(MONTH_FORMAT)
fun YearMonth.shortTitle(): String = format(SHORT_MONTH_FORMAT)

@Composable
fun CategoryAvatar(colorCode: String?, textType: Int, letter: String, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(categoryColor(colorCode), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.take(1).uppercase(),
            color = categoryTextColor(textType),
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun amountColor(type: CatType): Color =
    if (type == CatType.EXPENSE) LocalMonitoColors.current.expense else LocalMonitoColors.current.income

@Composable
fun AmountText(
    amount: BigDecimal,
    type: CatType,
    currency: CurrencySettings,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    signed: Boolean = false,
) {
    val prefix = if (signed) (if (type == CatType.EXPENSE) "- " else "+ ") else ""
    Text(
        text = prefix + AmountFormatter.format(amount, currency),
        color = amountColor(type),
        style = style,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
fun EmptyState(title: String, message: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = stringResource(R.string.okay),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                onDismiss()
            }) { Text(stringResource(R.string.okay)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoTimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) {
                Text(stringResource(R.string.okay))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Category chooser: favourites first, then the rest alphabetically, like the original app's picker. */
@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    allowNone: Boolean = false,
    onPick: (Category?) -> Unit,
) {
    val ordered = remember(categories) { categories.sortedWith(compareByDescending<Category> { it.isFavorite }.thenBy { it.name.lowercase() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_category)) },
        text = {
            if (ordered.isEmpty()) {
                Text(stringResource(R.string.please_add_category_before_adding_record))
            } else {
                LazyColumn {
                    if (allowNone) {
                        item {
                            Text(
                                stringResource(R.string.none),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(null); onDismiss() }
                                    .padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(ordered, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(c); onDismiss() }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryAvatar(c.colorCode, c.colorTextType, c.name, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(if (c.type == CatType.EXPENSE) R.string.expense else R.string.income),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = amountColor(c.type),
                                )
                            }
                            if (c.isFavorite) Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    t: Transaction,
    currency: CurrencySettings,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(t.colorCode, t.colorTextType, t.catName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.catName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = buildString {
                if (t.note.isNotBlank()) append(t.note)
                if (t.isAutoAdded) {
                    if (isNotEmpty()) append(" • ")
                    append(stringResource(R.string.auto_added_by_recurring))
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (t.imgPath != null) {
            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        if (t.isAutoAdded) {
            Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        AmountText(t.amount, t.catType, currency)
    }
}

/** A row that can be swiped left to delete and long pressed for Edit / Duplicate / Delete. */
@Composable
fun SwipeableTransactionRow(
    t: Transaction,
    currency: CurrencySettings,
    onClick: () -> Unit,
    onDelete: ((Transaction) -> Unit)?,
    onDuplicate: ((Transaction) -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val hasActions = onDelete != null || onDuplicate != null
    val row: @Composable () -> Unit = {
        Box {
            TransactionRow(t, currency, onClick, onLongClick = if (hasActions) ({ menu = true }) else null)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menu = false; onClick() },
                )
                if (onDuplicate != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { menu = false; onDuplicate(t) },
                    )
                }
                if (onDelete != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete(t) },
                    )
                }
            }
        }
    }
    if (onDelete == null) {
        row()
        return
    }
    var deleted by remember(t.id) { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !deleted) {
                deleted = true
                onDelete(t)
            }
            // Spring back; the row disappears when the list reloads, and a restored row starts clean.
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.background) { row() }
    }
}

/**
 * Emits the transaction list grouped by month and day with daily totals, the way the original list
 * showed month labels and day headers. Use inside a LazyColumn. When [onDelete] is given, rows can
 * be swiped away; when [onDuplicate] is given, long pressing offers a copy.
 */
fun LazyListScope.transactionItems(
    transactions: List<Transaction>,
    currency: CurrencySettings,
    showMonthLabels: Boolean = true,
    onClick: (Transaction) -> Unit,
    onDelete: ((Transaction) -> Unit)? = null,
    onDuplicate: ((Transaction) -> Unit)? = null,
) {
    val byMonth = transactions.groupBy { YearMonth.from(it.date) }
    for ((ym, monthList) in byMonth) {
        if (showMonthLabels) {
            item(key = "m-$ym") {
                Text(
                    ym.title(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
        }
        val byDay = monthList.groupBy { it.date }
        for ((day, dayList) in byDay) {
            item(key = "d-$day") {
                val expense = dayList.filter { it.catType == CatType.EXPENSE }.fold(BigDecimal.ZERO) { a, t -> a + t.amount }
                val income = dayList.filter { it.catType == CatType.INCOME }.fold(BigDecimal.ZERO) { a, t -> a + t.amount }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(day.pretty(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (income.signum() > 0) {
                        AmountText(income, CatType.INCOME, currency, style = MaterialTheme.typography.labelMedium, signed = true)
                    }
                    if (income.signum() > 0 && expense.signum() > 0) Spacer(Modifier.width(10.dp))
                    if (expense.signum() > 0) {
                        AmountText(expense, CatType.EXPENSE, currency, style = MaterialTheme.typography.labelMedium, signed = true)
                    }
                }
            }
            items(dayList, key = { "t-${it.id}" }) { t ->
                SwipeableTransactionRow(t, currency, onClick = { onClick(t) }, onDelete = onDelete, onDuplicate = onDuplicate)
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/** A read-only outlined field that opens a picker when tapped. */
@Composable
fun PickerField(value: String, label: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        trailingIcon = trailing,
        modifier = modifier.clickable(onClick = onClick),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
