package com.monito.expense.ui.recurrence

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.RecurType
import com.monito.expense.data.RecurrenceSchedule
import com.monito.expense.data.Repository
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.CategoryPickerDialog
import com.monito.expense.ui.ConfirmDialog
import com.monito.expense.ui.MonitoDatePickerDialog
import com.monito.expense.ui.MonitoTimePickerDialog
import com.monito.expense.ui.PickerField
import com.monito.expense.ui.amountColor
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.short
import com.monito.expense.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurrenceScreen(nav: NavController, recurrenceId: Int) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val scope = rememberCoroutineScope()
    val editing = recurrenceId >= 0

    var loaded by remember { mutableStateOf(!editing) }
    var note by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    var type by remember { mutableStateOf(RecurType.MONTHLY) }
    var anchorDate by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var storedNext by remember { mutableStateOf<LocalDateTime?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var amountError by remember { mutableStateOf<Int?>(null) }
    var categoryError by remember { mutableStateOf<Int?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showEndDate by remember { mutableStateOf(false) }
    var showWeekday by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(recurrenceId, reload) {
        val result = withContext(Dispatchers.IO) {
            val cats = repo.categories()
            val r = if (editing) repo.recurrence(recurrenceId) else null
            Triple(cats, r, r?.let { repo.category(it.catId) })
        }
        categories = result.first
        result.second?.let { r ->
            note = r.note
            amountText = r.amount.stripTrailingZeros().toPlainString()
            type = r.type
            anchorDate = r.nextOn.toLocalDate()
            time = r.nextOn.toLocalTime()
            endDate = r.endDate
            storedNext = r.nextOn
        }
        result.third?.let { category = it }
        loaded = true
    }

    // When editing without changing the schedule, keep the stored next run instead of recomputing it.
    val scheduleChanged = remember(type, anchorDate, time, storedNext) {
        val s = storedNext
        s == null || s.toLocalDate() != anchorDate || s.toLocalTime() != time
    }
    val nextOn = remember(type, anchorDate, time, storedNext, scheduleChanged) {
        if (!scheduleChanged && storedNext != null) storedNext!! else RecurrenceSchedule.nextOccurrence(type, anchorDate, time)
    }
    val preview = remember(nextOn, type, endDate) { repo.previewRecurrence(type, nextOn, endDate, 5) }

    fun save() {
        val amount = AmountFormatter.parse(amountText, settings.currency)
        amountError = when {
            amountText.isBlank() -> R.string.error_amt_cant_be_empty
            amount == null -> R.string.error_amt_is_invalid
            amount.signum() <= 0 -> R.string.error_amt_should_greater_than_zero
            else -> null
        }
        val cat = category
        categoryError = if (cat == null) R.string.error_cat_cant_be_empty else null
        if (amountError != null || cat == null || amount == null) return
        scope.launch {
            withContext(Dispatchers.IO) {
                if (editing) repo.updateRecurrence(recurrenceId, note, cat.id, amount, type, nextOn, endDate)
                else repo.insertRecurrence(note, cat.id, amount, type, nextOn, endDate)
            }
            toast(context, if (editing) R.string.recurring_modified else R.string.recurring_added)
            nav.popBackStack()
        }
    }

    val typeLabels = mapOf(
        RecurType.DAILY to R.string.every_day,
        RecurType.WEEKLY to R.string.every_week,
        RecurType.MONTHLY to R.string.every_month,
        RecurType.YEARLY to R.string.every_year,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing) R.string.edit_recurring else R.string.add_recurring)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    if (editing) IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_recurring)) }
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
            val cat = category
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showCategories = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cat != null) CategoryAvatar(cat.colorCode, cat.colorTextType, cat.name) else Box(Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        cat?.name ?: stringResource(R.string.select_category),
                        color = if (categoryError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    if (cat != null) {
                        Text(
                            stringResource(if (cat.type == CatType.EXPENSE) R.string.expense else R.string.income),
                            style = MaterialTheme.typography.bodySmall,
                            color = amountColor(cat.type),
                        )
                    }
                }
            }
            if (categoryError != null) Text(stringResource(categoryError!!), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; amountError = null },
                label = { Text(stringResource(R.string.amount)) },
                prefix = if (settings.currency.symbolOnLeft) ({ Text(settings.currency.symbol) }) else null,
                suffix = if (!settings.currency.symbolOnLeft) ({ Text(settings.currency.symbol) }) else null,
                isError = amountError != null,
                supportingText = amountError?.let { { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            Box {
                PickerField(
                    value = stringResource(typeLabels.getValue(type)),
                    label = stringResource(R.string.repeat_type),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = { Icon(Icons.Default.ArrowDropDown, null) },
                ) { showTypeMenu = true }
                DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                    for ((t, label) in typeLabels) {
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { type = t; showTypeMenu = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            when (type) {
                RecurType.WEEKLY -> Box {
                    PickerField(
                        value = anchorDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        label = stringResource(R.string.select_day),
                        modifier = Modifier.fillMaxWidth(),
                        trailing = { Icon(Icons.Default.ArrowDropDown, null) },
                    ) { showWeekday = true }
                    DropdownMenu(expanded = showWeekday, onDismissRequest = { showWeekday = false }) {
                        for (d in DayOfWeek.entries) {
                            DropdownMenuItem(
                                text = { Text(d.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                                onClick = {
                                    var date = LocalDate.now()
                                    while (date.dayOfWeek != d) date = date.plusDays(1)
                                    anchorDate = date
                                    showWeekday = false
                                },
                            )
                        }
                    }
                }
                RecurType.MONTHLY -> PickerField(
                    value = stringResource(R.string.of_every_month, ordinal(anchorDate.dayOfMonth)),
                    label = stringResource(R.string.select_day),
                    modifier = Modifier.fillMaxWidth(),
                ) { showDate = true }
                RecurType.YEARLY -> PickerField(
                    value = anchorDate.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())),
                    label = stringResource(R.string.select_day),
                    modifier = Modifier.fillMaxWidth(),
                ) { showDate = true }
                RecurType.DAILY -> Unit
            }
            if (type != RecurType.DAILY) Spacer(Modifier.height(12.dp))
            PickerField(
                value = time.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())),
                label = stringResource(R.string.time),
                modifier = Modifier.fillMaxWidth(),
            ) { showTime = true }
            Spacer(Modifier.height(12.dp))
            PickerField(
                value = endDate?.short() ?: stringResource(R.string.no_end_date),
                label = stringResource(R.string.end_date_optional),
                modifier = Modifier.fillMaxWidth(),
                trailing = if (endDate != null) ({
                    IconButton(onClick = { endDate = null }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_end_date)) }
                }) else null,
            ) { showEndDate = true }
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.upcoming_dates), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (preview.isEmpty()) {
                Text(stringResource(R.string.ended), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for ((i, d) in preview.withIndex()) {
                Text(
                    (if (i == 0) "▶ " else "   ") + d.nextRunText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            if (editing) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.skipNextRecurrence(recurrenceId) }
                            toast(context, R.string.next_occurrence_skipped)
                            reload++
                        }
                    }) {
                        Icon(Icons.Default.SkipNext, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.skip_next))
                    }
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }

    if (showCategories) CategoryPickerDialog(categories, onDismiss = { showCategories = false }) { category = it; categoryError = null }
    if (showTime) MonitoTimePickerDialog(time, onDismiss = { showTime = false }) { time = it }
    if (showDate) MonitoDatePickerDialog(anchorDate, onDismiss = { showDate = false }) { anchorDate = it }
    if (showEndDate) MonitoDatePickerDialog(endDate ?: nextOn.toLocalDate().plusMonths(6), onDismiss = { showEndDate = false }) { endDate = it }
    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_recurring),
            text = stringResource(R.string.delete_this_recurring_you_cannot_undo_this),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.deleteRecurrence(recurrenceId) }
                    toast(context, R.string.recurring_deleted)
                    nav.popBackStack()
                }
            },
            onDismiss = { showDelete = false },
        )
    }
}
