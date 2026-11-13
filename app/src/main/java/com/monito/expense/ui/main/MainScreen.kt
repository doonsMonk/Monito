package com.monito.expense.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.CatType
import com.monito.expense.data.CategoryTotal
import com.monito.expense.data.DateRange
import com.monito.expense.data.ReportHtml
import com.monito.expense.data.Repository
import com.monito.expense.data.Settings
import com.monito.expense.data.TimePeriod
import com.monito.expense.data.TimePeriods
import com.monito.expense.data.Transaction
import com.monito.expense.data.XlsxExporter
import com.monito.expense.ui.AmountText
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.EmptyState
import com.monito.expense.ui.MonitoDatePickerDialog
import com.monito.expense.ui.Routes
import com.monito.expense.ui.SummaryRow
import com.monito.expense.ui.amountColor
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.short
import com.monito.expense.ui.theme.categoryColor
import com.monito.expense.ui.title
import com.monito.expense.ui.toast
import com.monito.expense.ui.transactionItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Short name of the selected period: "This month", "September 2026", "Last 3 months", … */
@Composable
fun periodLabel(settings: Settings, range: DateRange?): String = when (settings.timePeriod) {
    TimePeriod.THIS_MONTH -> stringResource(R.string.this_month)
    TimePeriod.LAST_MONTH -> stringResource(R.string.last_month)
    TimePeriod.LAST_3_MONTHS -> stringResource(R.string.last_3_months)
    TimePeriod.LAST_6_MONTHS -> stringResource(R.string.last_6_months)
    TimePeriod.ALL_TIME -> stringResource(R.string.all_time)
    TimePeriod.MONTH -> (settings.monthAnchor ?: YearMonth.now()).title()
    TimePeriod.CUSTOM -> range?.let { "${it.from.short()} – ${it.to.short()}" } ?: stringResource(R.string.custom)
}

/** Period name for the home screen: "this month" and "last month" are shown as the month itself, like the original app. */
@Composable
fun homePeriodLabel(settings: Settings, range: DateRange?): String =
    if (range != null && (settings.timePeriod == TimePeriod.THIS_MONTH || settings.timePeriod == TimePeriod.LAST_MONTH)) YearMonth.from(range.from).title()
    else periodLabel(settings, range)

/** Second line under the period name: the calendar month for monthly periods, otherwise the exact dates. */
@Composable
fun periodDetail(settings: Settings, range: DateRange?): String = when (settings.timePeriod) {
    TimePeriod.THIS_MONTH, TimePeriod.LAST_MONTH -> range?.let { YearMonth.from(it.from).title() } ?: ""
    else -> range?.let { "${it.from.short()} – ${it.to.short()}" } ?: ""
}

/** Resolves the selected time period to concrete dates, refreshing when data or settings change. */
@Composable
fun rememberPeriodRange(settings: Settings, repo: Repository, changes: Long): DateRange? {
    var range by remember { mutableStateOf<DateRange?>(null) }
    LaunchedEffect(settings.timePeriodCode, settings.customStart, settings.customEnd, settings.monthAnchor, changes) {
        range = withContext(Dispatchers.IO) {
            TimePeriods.range(
                period = settings.timePeriod,
                customStart = settings.customStart,
                customEnd = settings.customEnd,
                minDate = repo.minDate(),
                maxDate = repo.maxDate(),
                month = settings.monthAnchor,
            )
        }
    }
    return range
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val prefs = rememberPrefs()
    val settings by prefs.settings.collectAsState()
    val changes by repo.changes.collectAsState()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(if (settings.startWithGraphs) 1 else 0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var menuOpen by remember { mutableStateOf(false) }
    var showPeriod by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showExportInfo by remember { mutableStateOf(false) }
    var showPrint by remember { mutableStateOf(false) }
    val range = rememberPeriodRange(settings, repo, changes)
    val focusRequester = remember { FocusRequester() }
    val snackbar = remember { SnackbarHostState() }
    val twoPane = LocalConfiguration.current.screenWidthDp >= 720
    val undoLabel = stringResource(R.string.undo)
    val movedToTrash = stringResource(R.string.transaction_moved_to_trash)
    val restoredText = stringResource(R.string.transaction_restored)
    val duplicatedText = stringResource(R.string.transaction_duplicated)
    val appName = stringResource(R.string.app_name)

    fun deleteWithUndo(t: Transaction) {
        scope.launch {
            withContext(Dispatchers.IO) { repo.moveToTrash(t.id) }
            val result = snackbar.showSnackbar(message = movedToTrash, actionLabel = undoLabel, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) {
                withContext(Dispatchers.IO) { repo.restoreFromTrash(t.id) }
                snackbar.showSnackbar(restoredText)
            }
        }
    }

    fun duplicate(t: Transaction) {
        scope.launch {
            val newId = withContext(Dispatchers.IO) { repo.duplicateTransaction(t.id) }
            if (newId != null) {
                toast(context, duplicatedText)
                nav.navigate(Routes.record(newId.toInt()))
            }
        }
    }

    /** Moves the period one step back (-1) or forward (+1); wired to the arrows and the keyboard. */
    fun stepPeriod(delta: Int) {
        val r = range ?: return
        if (!TimePeriods.canStep(settings.timePeriod)) return
        prefs.update { TimePeriods.step(it, delta, r) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        val r = range
        if (uri != null && r != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val list = repo.transactions(r.from, r.to)
                        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                        context.contentResolver.openOutputStream(uri, "wt")?.use { XlsxExporter.export(it, list, version, appName) }
                            ?: error("no stream")
                    }.isSuccess
                }
                toast(context, if (ok) R.string.export_successful else R.string.export_failed)
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { stepPeriod(-1); true }
                    Key.DirectionRight -> { stepPeriod(+1); true }
                    else -> false
                }
            },
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.expense_manager), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    val close: (() -> Unit) -> Unit = { action -> scope.launch { drawerState.close() }; action() }
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.transactions_and_graphs)) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        selected = true,
                        onClick = { close {} },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.categories)) },
                        icon = { Icon(Icons.Default.Label, null) },
                        selected = false,
                        onClick = { close { nav.navigate(Routes.CATEGORIES) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.recurrings)) },
                        icon = { Icon(Icons.Default.Repeat, null) },
                        selected = false,
                        onClick = { close { nav.navigate(Routes.RECURRENCES) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.budgets)) },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                        selected = false,
                        onClick = { close { nav.navigate(Routes.BUDGETS) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.trends)) },
                        icon = { Icon(Icons.Default.TrendingUp, null) },
                        selected = false,
                        onClick = { close { nav.navigate(Routes.TRENDS) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.quick_templates)) },
                        icon = { Icon(Icons.Default.Bookmarks, null) },
                        selected = false,
                        onClick = { close { nav.navigate(Routes.TEMPLATES) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.trash)) },
                        icon = { Icon(Icons.Default.Delete, null) },
                        selected = false,
                        onClick = { close { nav.navigate(Routes.TRASH) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.settings)) },
                        icon = { Icon(Icons.Default.Settings, null) },
                        selected = false,
                        onClick = { close { nav.navigate(Routes.SETTINGS) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.google_drive)) },
                        icon = { Icon(Icons.Default.CloudUpload, null) },
                        selected = false,
                        onClick = { close { nav.navigate(Routes.DRIVE) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.about_monito)) },
                        icon = { Icon(Icons.Default.Info, null) },
                        selected = false,
                        onClick = { close { showAbout = true } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            },
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Row(
                                    modifier = Modifier.clickable { showPeriod = true },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(homePeriodLabel(settings, range), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.change_time_period))
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                                }
                            },
                            actions = {
                                IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) }
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.print)) },
                                        leadingIcon = { Icon(Icons.Default.Print, null) },
                                        onClick = { menuOpen = false; showPrint = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_to_excel)) },
                                        leadingIcon = { Icon(Icons.Default.Share, null) },
                                        onClick = { menuOpen = false; showExportInfo = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.about_monito)) },
                                        leadingIcon = { Icon(Icons.Default.Info, null) },
                                        onClick = { menuOpen = false; showAbout = true },
                                    )
                                }
                            },
                        )
                    }
                },
                bottomBar = {
                    if (!twoPane) NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                            label = { Text(stringResource(R.string.transactions)) },
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.PieChart, null) },
                            label = { Text(stringResource(R.string.graphs)) },
                        )
                    }
                },
                floatingActionButton = {
                    if (tab == 0 || twoPane) {
                        ExtendedFloatingActionButton(
                            onClick = { nav.navigate(Routes.record()) },
                            icon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            text = { Text(stringResource(R.string.add_transaction)) },
                            shape = RoundedCornerShape(50),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            ) { padding ->
                val r = range
                val periodBar: @Composable () -> Unit = {
                    PeriodNavigator(
                        settings = settings,
                        range = range,
                        onPrevious = { stepPeriod(-1) },
                        onNext = { stepPeriod(+1) },
                        onOpen = { showPeriod = true },
                    )
                }
                if (r != null) {
                    if (twoPane) {
                        Row(Modifier.padding(padding)) {
                            TransactionsTab(
                                range = r,
                                settings = settings,
                                repo = repo,
                                changes = changes,
                                modifier = Modifier.weight(1f),
                                onOpen = { nav.navigate(Routes.record(it.id)) },
                                onDelete = { deleteWithUndo(it) },
                                onDuplicate = { duplicate(it) },
                                onDateClick = { nav.navigate(Routes.record(date = it.toString())) },
                                periodBar = periodBar,
                            )
                            VerticalDivider()
                            GraphsTab(
                                range = r,
                                settings = settings,
                                repo = repo,
                                changes = changes,
                                modifier = Modifier.weight(1f),
                                onCategory = { nav.navigate(Routes.categoryRecords(it.catId)) },
                            )
                        }
                    } else if (tab == 0) {
                        TransactionsTab(
                            range = r,
                            settings = settings,
                            repo = repo,
                            changes = changes,
                            modifier = Modifier.padding(padding),
                            onOpen = { nav.navigate(Routes.record(it.id)) },
                            onDelete = { deleteWithUndo(it) },
                            onDuplicate = { duplicate(it) },
                            onDateClick = { nav.navigate(Routes.record(date = it.toString())) },
                            periodBar = periodBar,
                        )
                    } else {
                        GraphsTab(
                            range = r,
                            settings = settings,
                            repo = repo,
                            changes = changes,
                            modifier = Modifier.padding(padding),
                            periodBar = periodBar,
                            onCategory = { nav.navigate(Routes.categoryRecords(it.catId)) },
                        )
                    }
                }
            }
        }
    }

    if (showPeriod) {
        PeriodDialog(
            settings = settings,
            onDismiss = { showPeriod = false },
            onApply = { period, start, end ->
                prefs.update { it.copy(timePeriodCode = period.code, customStart = start ?: it.customStart, customEnd = end ?: it.customEnd) }
            },
        )
    }
    if (showExportInfo) {
        AlertDialog(
            onDismissRequest = { showExportInfo = false },
            title = { Text(stringResource(R.string.export_to_excel)) },
            text = { Text(stringResource(R.string.transactions_will_be_exported_month_wise)) },
            confirmButton = {
                TextButton(onClick = {
                    showExportInfo = false
                    exportLauncher.launch("Monito_${LocalDate.now()}.xlsx")
                }) { Text(stringResource(R.string.export)) }
            },
            dismissButton = { TextButton(onClick = { showExportInfo = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPrint) {
        val label = periodLabel(settings, range)
        PrintDialog(
            periodLabel = label,
            detail = periodDetail(settings, range),
            onDismiss = { showPrint = false },
            onPrint = { groupByMonth, pagePerMonth ->
                val r = range ?: return@PrintDialog
                scope.launch {
                    val html = withContext(Dispatchers.IO) {
                        val list = repo.transactions(r.from, r.to)
                        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                        ReportHtml.build(list, r, settings.currency, groupByMonth, pagePerMonth, label, version, appName)
                    }
                    runCatching { PrintHelper.print(context, "$appName $label", html) }
                        .onFailure { toast(context, R.string.print_failed) }
                }
            },
        )
    }
    if (showAbout) {
        var dbVersion by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { dbVersion = withContext(Dispatchers.IO) { repo.databaseVersion() } }
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.about_monito)) },
            text = { Text(stringResource(R.string.about_dialog_text, version, dbVersion)) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.okay)) } },
        )
    }
}

// --------------------------------------------------------------------------- period navigator

/** ◀ (period) ▶ line: the arrows walk month by month, tapping the label opens the full period dialog. */
@Composable
fun PeriodNavigator(
    settings: Settings,
    range: DateRange?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    val canStep = TimePeriods.canStep(settings.timePeriod)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious, enabled = canStep) {
            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.previous_month))
        }
        Text(
            homePeriodLabel(settings, range),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 5.dp),
        )
        IconButton(onClick = onNext, enabled = canStep) {
            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.next_month))
        }
    }
}

// --------------------------------------------------------------------------- transactions tab

private data class Totals(
    val income: BigDecimal = BigDecimal.ZERO,
    val expense: BigDecimal = BigDecimal.ZERO,
    val carryForward: BigDecimal = BigDecimal.ZERO,
) {
    val balance: BigDecimal get() = income - expense
    val balanceWithCf: BigDecimal get() = balance + carryForward
}

/** One figure of the totals strip: a small label with the value underneath. */
@Composable
private fun TotalCell(label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(value, fontSize = 18.sp, color = color, maxLines = 1)
    }
}

/** Expense / Income / Balance (and carry forward) side by side under the app bar; scrolls sideways when it does not fit. */
@Composable
private fun TotalsStrip(totals: Totals, settings: Settings) {
    val currency = settings.currency
    val plain = MaterialTheme.colorScheme.onSurface
    val negative = amountColor(CatType.EXPENSE)
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TotalCell(stringResource(R.string.expense), AmountFormatter.format(totals.expense, currency), plain, Icons.Default.RemoveCircleOutline)
            TotalCell(stringResource(R.string.income), AmountFormatter.format(totals.income, currency), plain, Icons.Default.AddCircleOutline)
            TotalCell(stringResource(R.string.balance), AmountFormatter.format(totals.balance, currency), if (totals.balance.signum() < 0) negative else plain, Icons.Default.Balance)
            if (settings.showCarryForward) {
                TotalCell(stringResource(R.string.carry_forward), AmountFormatter.format(totals.carryForward, currency), if (totals.carryForward.signum() < 0) negative else plain)
                TotalCell(stringResource(R.string.balance_with_cf), AmountFormatter.format(totals.balanceWithCf, currency), if (totals.balanceWithCf.signum() < 0) negative else plain)
            }
        }
    }
}

@Composable
fun TransactionsTab(
    range: DateRange,
    settings: Settings,
    repo: Repository,
    changes: Long,
    modifier: Modifier = Modifier,
    onOpen: (Transaction) -> Unit,
    onDelete: ((Transaction) -> Unit)? = null,
    onDuplicate: ((Transaction) -> Unit)? = null,
    onDateClick: ((LocalDate) -> Unit)? = null,
    periodBar: (@Composable () -> Unit)? = null,
) {
    var transactions by remember { mutableStateOf<List<Transaction>?>(null) }
    var totals by remember { mutableStateOf(Totals()) }
    LaunchedEffect(range, changes, settings.showCarryForward) {
        val result = withContext(Dispatchers.IO) {
            val list = repo.transactions(range.from, range.to)
            val income = repo.totalOfType(CatType.INCOME, range.from, range.to)
            val expense = repo.totalOfType(CatType.EXPENSE, range.from, range.to)
            var cf = BigDecimal.ZERO
            if (settings.showCarryForward) {
                val min = repo.minDate()
                if (min != null && min.isBefore(range.from)) {
                    val end = range.from.minusDays(1)
                    cf = repo.totalOfType(CatType.INCOME, min, end) - repo.totalOfType(CatType.EXPENSE, min, end)
                }
            }
            list to Totals(income, expense, cf)
        }
        transactions = result.first
        totals = result.second
    }
    val currency = settings.currency
    val list = transactions
    // The period bar already names a single month, so month labels are only needed for longer ranges.
    val severalMonths = YearMonth.from(range.from) != YearMonth.from(range.to)
    Column(modifier = modifier.fillMaxSize()) {
        TotalsStrip(totals, settings)
        periodBar?.invoke()
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (list != null && list.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.no_transactions), stringResource(R.string.msg_no_trans)) }
            } else if (list != null) {
                transactionItems(
                    list,
                    currency,
                    showMonthLabels = severalMonths,
                    onClick = onOpen,
                    onDelete = onDelete,
                    onDuplicate = onDuplicate,
                    onDateClick = onDateClick,
                )
                item(key = "bottom-space") { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

// --------------------------------------------------------------------------- graphs tab

@Composable
fun GraphsTab(
    range: DateRange,
    settings: Settings,
    repo: Repository,
    changes: Long,
    modifier: Modifier = Modifier,
    periodBar: (@Composable () -> Unit)? = null,
    onCategory: (CategoryTotal) -> Unit,
) {
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    val type = if (typeIndex == 0) CatType.EXPENSE else CatType.INCOME
    var totals by remember { mutableStateOf<List<CategoryTotal>?>(null) }
    var excluded by remember(type) { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(range, changes, type) {
        totals = withContext(Dispatchers.IO) { repo.categoryTotals(type, range.from, range.to) }
    }
    val currency = settings.currency
    Column(modifier = modifier.fillMaxSize()) {
        periodBar?.invoke()
        TabRow(selectedTabIndex = typeIndex) {
            Tab(selected = typeIndex == 0, onClick = { typeIndex = 0 }, text = { Text(stringResource(R.string.expense)) })
            Tab(selected = typeIndex == 1, onClick = { typeIndex = 1 }, text = { Text(stringResource(R.string.income)) })
        }
        val list = totals ?: return
        if (list.isEmpty()) {
            EmptyState(stringResource(if (type == CatType.EXPENSE) R.string.no_expenses else R.string.no_incomes), stringResource(R.string.msg_no_trans))
            return
        }
        val included = list.filter { it.catId !in excluded }
        val includedTotal = included.fold(BigDecimal.ZERO) { a, t -> a + t.total }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "chart") {
                Column(Modifier.padding(top = 16.dp)) {
                    PieChart(
                        slices = included.map { PieSlice(it.total.toFloat(), categoryColor(it.colorCode), it.name) },
                        centerText = AmountFormatter.format(includedTotal, currency),
                    )
                    Text(
                        stringResource(if (type == CatType.EXPENSE) R.string.total_expense else R.string.total_income) + ": " +
                            AmountFormatter.format(list.fold(BigDecimal.ZERO) { a, t -> a + t.total }, currency),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(list, key = { it.catId }) { ct ->
                val checked = ct.catId !in excluded
                val percent = if (checked && includedTotal.signum() > 0) {
                    ct.total.multiply(BigDecimal(100)).divide(includedTotal, 1, RoundingMode.HALF_UP).toPlainString() + "%"
                } else ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategory(ct) }
                        .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { on -> excluded = if (on) excluded - ct.catId else excluded + ct.catId },
                    )
                    CategoryAvatar(ct.colorCode, ct.colorTextType, ct.name, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ct.name, style = MaterialTheme.typography.bodyLarge)
                        if (percent.isNotEmpty()) Text(percent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AmountText(ct.total, type, currency)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// --------------------------------------------------------------------------- dialogs

@Composable
fun PeriodDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onApply: (TimePeriod, LocalDate?, LocalDate?) -> Unit,
) {
    var selected by remember { mutableStateOf(settings.timePeriod) }
    var start by remember { mutableStateOf(settings.customStart ?: LocalDate.now().withDayOfMonth(1)) }
    var end by remember { mutableStateOf(settings.customEnd ?: LocalDate.now()) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val options = buildList {
        add(TimePeriod.THIS_MONTH to stringResource(R.string.this_month))
        add(TimePeriod.LAST_MONTH to stringResource(R.string.last_month))
        if (settings.timePeriod == TimePeriod.MONTH) {
            add(TimePeriod.MONTH to (settings.monthAnchor ?: YearMonth.now()).title())
        }
        add(TimePeriod.LAST_3_MONTHS to stringResource(R.string.last_3_months))
        add(TimePeriod.LAST_6_MONTHS to stringResource(R.string.last_6_months))
        add(TimePeriod.ALL_TIME to stringResource(R.string.all_time))
        add(TimePeriod.CUSTOM to stringResource(R.string.custom))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_time_period)) },
        text = {
            Column {
                for ((period, label) in options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == period, onClick = { selected = period })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == period, onClick = { selected = period })
                        Text(label)
                    }
                }
                Text(
                    stringResource(R.string.specific_month_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (selected == TimePeriod.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = start.short(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.start_date)) },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { pickStart = true },
                            enabled = false,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        OutlinedTextField(
                            value = end.short(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.end_date)) },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { pickEnd = true },
                            enabled = false,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selected == TimePeriod.CUSTOM) onApply(selected, start, end) else onApply(selected, null, null)
                onDismiss()
            }) { Text(stringResource(R.string.set)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickStart) MonitoDatePickerDialog(start, onDismiss = { pickStart = false }) { start = it; if (end.isBefore(it)) end = it }
    if (pickEnd) MonitoDatePickerDialog(end, onDismiss = { pickEnd = false }) { end = it; if (start.isAfter(it)) start = it }
}

@Composable
fun PrintDialog(
    periodLabel: String,
    detail: String,
    onDismiss: () -> Unit,
    onPrint: (groupByMonth: Boolean, pagePerMonth: Boolean) -> Unit,
) {
    var groupByMonth by remember { mutableStateOf(true) }
    var pagePerMonth by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.print_report)) },
        text = {
            Column {
                Text(periodLabel, style = MaterialTheme.typography.titleMedium)
                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.print_description), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.group_by_month), modifier = Modifier.weight(1f))
                    Switch(checked = groupByMonth, onCheckedChange = { groupByMonth = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.page_per_month), modifier = Modifier.weight(1f), color = if (groupByMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = pagePerMonth && groupByMonth, onCheckedChange = { pagePerMonth = it }, enabled = groupByMonth)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onPrint(groupByMonth, pagePerMonth && groupByMonth) }) { Text(stringResource(R.string.print_report)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
