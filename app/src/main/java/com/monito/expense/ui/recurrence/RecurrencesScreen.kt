package com.monito.expense.ui.recurrence

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.RecurType
import com.monito.expense.data.Recurrence
import com.monito.expense.ui.AmountText
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.EmptyState
import com.monito.expense.ui.Routes
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
private val DAY_MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val NEXT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.getDefault())

/** Human readable repeat rule, same wording as the original list ("Everyday at…", "15 of every month"…). */
@Composable
fun recurrenceDescription(type: RecurType, nextOn: LocalDateTime): String = when (type) {
    RecurType.DAILY -> stringResource(R.string.every_day_at, nextOn.format(TIME_FMT))
    RecurType.WEEKLY -> stringResource(R.string.every_week_on, nextOn.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
    RecurType.MONTHLY -> stringResource(R.string.of_every_month, ordinal(nextOn.dayOfMonth))
    RecurType.YEARLY -> stringResource(R.string.every_year_on, nextOn.format(DAY_MONTH_FMT))
}

fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

fun LocalDateTime.nextRunText(): String = format(NEXT_FMT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrencesScreen(nav: NavController) {
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val changes by repo.changes.collectAsState()
    var list by remember { mutableStateOf<List<Recurrence>?>(null) }
    LaunchedEffect(changes) { list = withContext(Dispatchers.IO) { repo.recurrences() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurrings)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.recurrence()) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_recurring))
            }
        },
    ) { padding ->
        val items = list ?: return@Scaffold
        if (items.isEmpty()) {
            EmptyState(stringResource(R.string.no_recurrings), stringResource(R.string.msg_to_add_recurring), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(items, key = { it.id }) { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate(Routes.recurrence(r.id)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryAvatar(r.colorCode, r.colorTextType, r.catName)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.note.ifBlank { r.catName }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (r.note.isNotBlank()) Text(r.catName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(recurrenceDescription(r.type, r.nextOn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        val end = r.endDate
                        val ended = end != null && r.nextOn.toLocalDate().isAfter(end)
                        Text(
                            if (ended) stringResource(R.string.ended) else stringResource(R.string.next_run, r.nextOn.nextRunText()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ended) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (end != null && !ended) {
                            Text(stringResource(R.string.ends_on, end.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AmountText(r.amount, r.catType, settings.currency)
                }
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}
