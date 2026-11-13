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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * Category chooser: favourites first, then the rest alphabetically, like the original app's picker.
 * The list is kept fresh from the database, and "Add category" creates one in place and picks it.
 */
@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    allowNone: Boolean = false,
    allowAdd: Boolean = true,
    dismissAfterPick: Boolean = true,
    dismissOnOutside: Boolean = true,
    onPick: (Category?) -> Unit,
) {
    val repo = rememberRepository()
    val changes by repo.changes.collectAsState()
    var current by remember { mutableStateOf(categories) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(changes) { current = withContext(Dispatchers.IO) { repo.categories() } }
    val ordered = remember(current) { current.sortedWith(compareByDescending<Category> { it.isFavorite }.thenBy { it.name.lowercase() }) }

    fun pick(c: Category?) {
        onPick(c)
        if (dismissAfterPick) onDismiss()
    }

    if (showAdd) {
        AddCategoryDialog(onDismiss = { showAdd = false }) { created ->
            showAdd = false
            pick(created)
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = dismissOnOutside),
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
                                    .clickable { pick(null) }
                                    .padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(ordered, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pick(c) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryTypeBadge(c.colorCode, c.colorTextType, c.type, 36.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (c.isFavorite) Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (allowAdd) {
                    TextButton(onClick = { showAdd = true }) { Text(stringResource(R.string.add_category).uppercase(), fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
    )
}

/** Round badge in the category colour with a minus (expense) or plus (income) sign, as in the original app. */
@Composable
fun CategoryTypeBadge(colorCode: String?, textType: Int, type: CatType, size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(categoryColor(colorCode), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (type == CatType.EXPENSE) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline,
            contentDescription = stringResource(if (type == CatType.EXPENSE) R.string.expense else R.string.income),
            tint = categoryTextColor(textType),
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

/** One transaction as a rounded bar filled with its category colour, like the original list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    t: Transaction,
    currency: CurrencySettings,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val fg = categoryTextColor(t.colorTextType)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(categoryColor(t.colorCode))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 44.dp)
            .padding(start = 12.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (t.catType == CatType.EXPENSE) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline,
            contentDescription = stringResource(if (t.catType == CatType.EXPENSE) R.string.expense else R.string.income),
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.catName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.note.isNotBlank()) {
                Text(
                    t.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = fg.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (t.imgPath != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Image, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
        if (t.isAutoAdded) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Repeat, contentDescription = stringResource(R.string.auto_added_by_recurring), tint = fg, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            AmountFormatter.format(t.amount, currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
        )
    }
}

/**
 * A transaction row whose long press offers Edit / Duplicate / Delete. There is deliberately no
 * swipe gesture: swiping to delete fired by accident while scrolling the list.
 */
@Composable
fun TransactionRowWithMenu(
    t: Transaction,
    currency: CurrencySettings,
    onClick: () -> Unit,
    onDelete: ((Transaction) -> Unit)?,
    onDuplicate: ((Transaction) -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val hasActions = onDelete != null || onDuplicate != null
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

private val DAY_HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy, EEE", Locale.getDefault())

/** Small rounded label with a day's income or expense total. */
@Composable
private fun DayTotalPill(amount: BigDecimal, type: CatType, currency: CurrencySettings) {
    val colors = LocalMonitoColors.current
    Text(
        AmountFormatter.format(amount, currency),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = amountColor(type),
        maxLines = 1,
        modifier = Modifier
            .background(if (type == CatType.EXPENSE) colors.expenseContainer else colors.incomeContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/**
 * Emits the transaction list grouped by month and day with daily totals, the way the original list
 * showed month labels and day headers. Use inside a LazyColumn. Long pressing a row offers
 * Edit / Duplicate / Delete when [onDelete] / [onDuplicate] are given; tapping a day header calls
 * [onDateClick] so a transaction can be added for that day.
 */
fun LazyListScope.transactionItems(
    transactions: List<Transaction>,
    currency: CurrencySettings,
    showMonthLabels: Boolean = true,
    onClick: (Transaction) -> Unit,
    onDelete: ((Transaction) -> Unit)? = null,
    onDuplicate: ((Transaction) -> Unit)? = null,
    onDateClick: ((LocalDate) -> Unit)? = null,
) {
    val byMonth = transactions.groupBy { YearMonth.from(it.date) }
    for ((ym, monthList) in byMonth) {
        if (showMonthLabels) {
            item(key = "m-$ym") {
                Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                    Text(
                        ym.title(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
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
                        .then(if (onDateClick != null) Modifier.clickable { onDateClick(day) } else Modifier)
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        day.format(DAY_HEADER_FORMAT),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (income.signum() > 0) DayTotalPill(income, CatType.INCOME, currency)
                    if (income.signum() > 0 && expense.signum() > 0) Spacer(Modifier.width(6.dp))
                    if (expense.signum() > 0) DayTotalPill(expense, CatType.EXPENSE, currency)
                }
            }
            items(dayList, key = { "t-${it.id}" }) { t ->
                TransactionRowWithMenu(t, currency, onClick = { onClick(t) }, onDelete = onDelete, onDuplicate = onDuplicate)
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
