package com.monito.expense.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import com.monito.expense.data.UpdateChecker
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.CurrencySettings
import com.monito.expense.data.ThemeMode
import com.monito.expense.ui.MonitoTimePickerDialog
import com.monito.expense.ui.Routes
import com.monito.expense.ui.rememberBackupManager
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.toast
import com.monito.expense.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun <T> RadioDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                for ((value, label) in options) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, onClick = { onSelect(value); onDismiss() })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value); onDismiss() })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val prefs = rememberPrefs()
    val settings by prefs.settings.collectAsState()
    val backups = rememberBackupManager()
    val scope = rememberCoroutineScope()
    var showCurrency by remember { mutableStateOf(false) }
    var showStartup by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showBackupNow by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    val installedVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            prefs.update { it.copy(dailyReminder = true) }
            Scheduler.scheduleReminder(context, prefs.current.reminderTime)
        } else {
            toast(context, R.string.permission_notifications_needed)
        }
    }

    fun setReminder(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        prefs.update { it.copy(dailyReminder = on) }
        if (on) Scheduler.scheduleReminder(context, prefs.current.reminderTime) else Scheduler.cancelReminder(context)
    }

    val timeFmt = remember { DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()) }
    val reminderTime = runCatching { LocalTime.parse(settings.reminderTime) }.getOrDefault(LocalTime.of(22, 0))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.currency))
            SettingRow(
                stringResource(R.string.change_currency),
                AmountFormatter.format(BigDecimal("1234567.89"), settings.currency),
                onClick = { showCurrency = true },
            )
            SwitchRow(stringResource(R.string.show_carry_forward), null, settings.showCarryForward) { on ->
                prefs.update { it.copy(showCarryForward = on) }
            }
            HorizontalDivider()

            SectionTitle(stringResource(R.string.startup))
            SettingRow(
                stringResource(R.string.what_would_you_like_to_see_first),
                stringResource(if (settings.startWithGraphs) R.string.graphs else R.string.transactions),
                onClick = { showStartup = true },
            )
            SettingRow(
                stringResource(R.string.theme),
                stringResource(
                    when (settings.theme) {
                        ThemeMode.SYSTEM -> R.string.system_default
                        ThemeMode.LIGHT -> R.string.light
                        ThemeMode.DARK -> R.string.dark
                    },
                ),
                onClick = { showTheme = true },
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.alerts))
            SwitchRow(stringResource(R.string.daily_reminder), stringResource(R.string.you_will_be_reminded), settings.dailyReminder) { setReminder(it) }
            if (settings.dailyReminder) {
                SettingRow(
                    stringResource(R.string.select_notification_time),
                    stringResource(R.string.you_will_get_notification_around, reminderTime.format(timeFmt)),
                    onClick = { showTime = true },
                )
            }
            HorizontalDivider()

            SectionTitle(stringResource(R.string.backup))
            SwitchRow(stringResource(R.string.auto_backup_my_data), stringResource(R.string.will_backup_data_everyday), settings.autoBackup) { on ->
                prefs.update { it.copy(autoBackup = on) }
                Scheduler.scheduleAutoBackup(context, on)
            }
            SettingRow(stringResource(R.string.backup_my_data_now), stringResource(R.string.manually_backup_data), onClick = { showBackupNow = true })
            SettingRow(stringResource(R.string.view_restore_backups), stringResource(R.string.phone_storage), onClick = { nav.navigate(Routes.BACKUPS) })
            SettingRow(
                stringResource(R.string.google_drive),
                settings.driveAccount ?: stringResource(R.string.drive_not_linked),
                onClick = { nav.navigate(Routes.DRIVE) },
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.trash))
            SwitchRow(stringResource(R.string.auto_delete_transactions), stringResource(R.string.trash_transaction_auto_deleted), settings.autoDeleteTrash) { on ->
                prefs.update { it.copy(autoDeleteTrash = on) }
            }
            HorizontalDivider()

            SectionTitle(stringResource(R.string.security))
            SwitchRow(stringResource(R.string.screen_lock), stringResource(R.string.confirm_your_pin), settings.screenLock) { on ->
                if (on) {
                    val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    if (BiometricManager.from(context).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
                        toast(context, R.string.no_screen_is_set_for_device)
                        return@SwitchRow
                    }
                }
                prefs.update { it.copy(screenLock = on) }
            }
            SwitchRow(stringResource(R.string.hide_content), stringResource(R.string.this_will_also_prevent_screenshots), settings.hideContent) { on ->
                prefs.update { it.copy(hideContent = on) }
            }
            HorizontalDivider()

            SectionTitle(stringResource(R.string.updates))
            SettingRow(
                stringResource(R.string.check_for_updates),
                if (checkingUpdate) stringResource(R.string.checking) else stringResource(R.string.installed_version, installedVersion),
                onClick = {
                    if (!checkingUpdate) {
                        checkingUpdate = true
                        scope.launch {
                            val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
                            checkingUpdate = false
                            updateResult = latest
                            showUpdate = true
                        }
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showUpdate) {
        val latest = updateResult
        val newer = latest != null && UpdateChecker.isNewer(latest.version, installedVersion)
        AlertDialog(
            onDismissRequest = { showUpdate = false },
            title = {
                Text(
                    when {
                        latest == null -> stringResource(R.string.update_check_failed)
                        newer -> stringResource(R.string.update_available, latest.version)
                        else -> stringResource(R.string.up_to_date)
                    },
                )
            },
            text = {
                Text(
                    when {
                        latest == null -> stringResource(R.string.update_check_failed_desc)
                        newer -> stringResource(R.string.update_available_desc, installedVersion, latest.version)
                        else -> stringResource(R.string.up_to_date_desc, installedVersion)
                    },
                )
            },
            confirmButton = {
                if (latest != null && newer) {
                    TextButton(onClick = {
                        showUpdate = false
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.apkUrl ?: latest.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure { toast(context, R.string.update_check_failed) }
                    }) { Text(stringResource(R.string.download)) }
                } else {
                    TextButton(onClick = { showUpdate = false }) { Text(stringResource(R.string.okay)) }
                }
            },
            dismissButton = if (latest != null && newer) ({ TextButton(onClick = { showUpdate = false }) { Text(stringResource(R.string.later)) } }) else null,
        )
    }

    if (showCurrency) {
        CurrencyDialog(settings.currency, onDismiss = { showCurrency = false }) { c -> prefs.update { it.copy(currency = c) } }
    }
    if (showStartup) {
        RadioDialog(
            title = stringResource(R.string.startup),
            options = listOf(false to stringResource(R.string.transactions), true to stringResource(R.string.graphs)),
            selected = settings.startWithGraphs,
            onDismiss = { showStartup = false },
        ) { v -> prefs.update { it.copy(startWithGraphs = v) } }
    }
    if (showTheme) {
        RadioDialog(
            title = stringResource(R.string.theme),
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.system_default),
                ThemeMode.LIGHT to stringResource(R.string.light),
                ThemeMode.DARK to stringResource(R.string.dark),
            ),
            selected = settings.theme,
            onDismiss = { showTheme = false },
        ) { v -> prefs.update { it.copy(theme = v) } }
    }
    if (showTime) {
        MonitoTimePickerDialog(reminderTime, onDismiss = { showTime = false }) { t ->
            val text = t.format(DateTimeFormatter.ofPattern("HH:mm"))
            prefs.update { it.copy(reminderTime = text) }
            Scheduler.scheduleReminder(context, text)
        }
    }
    if (showBackupNow) {
        var name by remember { mutableStateOf("Monito") }
        AlertDialog(
            onDismissRequest = { showBackupNow = false },
            title = { Text(stringResource(R.string.backup_my_data_now)) },
            text = {
                Column {
                    Text(stringResource(R.string.new_backup_will_be_created_with_following_name))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.backup_file_name)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackupNow = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { backups.createBackup(name) }.isSuccess }
                        toast(context, if (ok) R.string.backup_successful else R.string.error_could_not_create_backup)
                    }
                }) { Text(stringResource(R.string.backup)) }
            },
            dismissButton = { TextButton(onClick = { showBackupNow = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun CurrencyDialog(current: CurrencySettings, onDismiss: () -> Unit, onApply: (CurrencySettings) -> Unit) {
    var symbol by remember { mutableStateOf(current.symbol) }
    var left by remember { mutableStateOf(current.symbolOnLeft) }
    var decimal by remember { mutableStateOf(current.decimalSeparator) }
    var thousand by remember { mutableStateOf(current.thousandSeparator) }
    val preview = CurrencySettings(symbol, left, decimal, thousand)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_currency)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { if (it.length <= 4) symbol = it },
                    label = { Text(stringResource(R.string.currency_symbol)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.show_symbol_on), style = MaterialTheme.typography.labelLarge)
                Row {
                    for ((v, label) in listOf(true to R.string.left_side, false to R.string.right_side)) {
                        Row(Modifier.selectable(selected = left == v, onClick = { left = v }).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = left == v, onClick = { left = v })
                            Text(stringResource(label))
                        }
                    }
                }
                Text(stringResource(R.string.decimal_separator), style = MaterialTheme.typography.labelLarge)
                Row {
                    for ((v, label) in listOf('.' to R.string.dot, ',' to R.string.comma)) {
                        Row(Modifier.selectable(selected = decimal == v, onClick = { decimal = v }).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = decimal == v, onClick = { decimal = v })
                            Text(stringResource(label))
                        }
                    }
                }
                Text(stringResource(R.string.thousand_separator), style = MaterialTheme.typography.labelLarge)
                Column {
                    for ((v, label) in listOf<Pair<Char?, Int>>(',' to R.string.comma, '.' to R.string.dot, ' ' to R.string.space, null to R.string.none)) {
                        Row(Modifier.selectable(selected = thousand == v, onClick = { thousand = v }).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = thousand == v, onClick = { thousand = v })
                            Text(stringResource(label))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.preview) + ": " + AmountFormatter.format(BigDecimal("1234567.89"), preview), style = MaterialTheme.typography.titleMedium)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(preview); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
