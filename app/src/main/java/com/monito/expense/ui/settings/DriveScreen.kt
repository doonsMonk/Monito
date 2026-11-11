package com.monito.expense.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.drive.DriveAuth
import com.monito.expense.drive.DriveClient
import com.monito.expense.drive.DriveSync
import com.monito.expense.drive.SecretStore
import com.monito.expense.drive.SyncCrypto
import com.monito.expense.ui.ConfirmDialog
import com.monito.expense.ui.Routes
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google Drive: link an account, set the encryption passphrase, upload encrypted snapshots and
 * restore them. Access tokens are obtained on demand; the consent screen is shown the first time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(nav: NavController, firstRun: Boolean = false) {
    val context = LocalContext.current
    val prefs = rememberPrefs()
    val settings by prefs.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val secrets = remember { SecretStore(context.applicationContext) }
    var busy by remember { mutableStateOf<Int?>(null) }
    var snapshots by remember { mutableStateOf<List<DriveClient.DriveFile>?>(null) }
    var selected by remember { mutableStateOf<DriveClient.DriveFile?>(null) }
    var confirmRestore by remember { mutableStateOf<DriveClient.DriveFile?>(null) }
    var confirmDelete by remember { mutableStateOf<DriveClient.DriveFile?>(null) }
    var showPassphrase by remember { mutableStateOf(false) }
    /** Action to run once a token is available (after the consent screen, if needed). */
    var pending by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }

    suspend fun afterToken(token: String, action: suspend (String) -> Unit) {
        if (settings.driveAccount == null) {
            val email = withContext(Dispatchers.IO) { DriveAuth.fetchEmail(token) } ?: "Google"
            prefs.update { it.copy(driveAccount = email) }
        }
        action(token)
    }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = pending
        pending = null
        val r = DriveAuth.fromConsentResult(context, result.data)
        if (r is DriveAuth.AuthResult.Token && action != null) {
            scope.launch { afterToken(r.accessToken, action) }
        } else if (r is DriveAuth.AuthResult.Failed) {
            toast(context, context.getString(R.string.drive_sign_in_failed, r.message))
        }
    }

    /** Gets a token (silently or through the consent screen) and runs [action] with it. */
    fun withToken(action: suspend (String) -> Unit) {
        scope.launch {
            when (val r = DriveAuth.authorize(context)) {
                is DriveAuth.AuthResult.Token -> afterToken(r.accessToken, action)
                is DriveAuth.AuthResult.NeedsConsent -> {
                    pending = action
                    consentLauncher.launch(IntentSenderRequest.Builder(r.pendingIntent.intentSender).build())
                }
                is DriveAuth.AuthResult.Failed -> toast(context, context.getString(R.string.drive_sign_in_failed, r.message))
            }
        }
    }

    fun refresh() {
        withToken { token ->
            busy = R.string.refresh
            runCatching { withContext(Dispatchers.IO) { DriveSync.listSnapshots(context, token) } }
                .onSuccess { snapshots = it }
                .onFailure { toast(context, it.message ?: context.getString(R.string.unknown_error_occurred)) }
            busy = null
        }
    }

    val linked = settings.driveAccount != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.google_drive)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    if (linked) IconButton(onClick = { refresh() }, enabled = busy == null) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "account") {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (linked) Icons.Default.CloudDone else Icons.Default.CloudOff, null, tint = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (linked) stringResource(R.string.drive_linked) else stringResource(R.string.drive_not_linked), style = MaterialTheme.typography.titleMedium)
                                Text(settings.driveAccount ?: stringResource(R.string.drive_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (!linked) {
                            Button(onClick = { withToken { refresh() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.link_drive)) }
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.drive_setup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            OutlinedButton(onClick = { prefs.update { it.copy(driveAccount = null, driveAutoUpload = false) }; snapshots = null }) { Text(stringResource(R.string.unlink_drive)) }
                        }
                    }
                }
            }
            item(key = "passphrase") {
                SettingRow(
                    stringResource(R.string.drive_passphrase),
                    stringResource(if (settings.drivePassphraseSet) R.string.passphrase_set else R.string.passphrase_not_set),
                    onClick = { showPassphrase = true },
                ) {
                    TextButton(onClick = { showPassphrase = true }) { Text(stringResource(if (settings.drivePassphraseSet) R.string.change_passphrase else R.string.set_passphrase)) }
                }
                Text(
                    stringResource(R.string.drive_passphrase_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(Modifier.padding(top = 12.dp))
            }
            item(key = "upload") {
                SwitchRow(stringResource(R.string.drive_auto_upload), stringResource(R.string.drive_auto_upload_desc), settings.driveAutoUpload && linked) { on ->
                    if (!linked) { toast(context, R.string.link_required); return@SwitchRow }
                    if (!settings.drivePassphraseSet) { toast(context, R.string.passphrase_required); return@SwitchRow }
                    prefs.update { it.copy(driveAutoUpload = on) }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(
                        enabled = busy == null,
                        onClick = {
                            if (!linked) { toast(context, R.string.link_required); return@Button }
                            if (!settings.drivePassphraseSet) { toast(context, R.string.passphrase_required); return@Button }
                            withToken { token ->
                                busy = R.string.uploading
                                runCatching { withContext(Dispatchers.IO) { DriveSync.uploadSnapshot(context, interactive = true, accessToken = token) } }
                                    .onSuccess { toast(context, R.string.upload_successful); refresh() }
                                    .onFailure { toast(context, context.getString(R.string.upload_failed, it.message ?: "")) }
                                busy = null
                            }
                        },
                    ) {
                        Icon(Icons.Default.CloudUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.upload_now))
                    }
                    if (busy != null) {
                        Spacer(Modifier.width(16.dp))
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(busy!!), modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.drive_snapshots),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                Text(stringResource(R.string.drive_folder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val list = snapshots
            if (linked && list == null) {
                item { TextButton(onClick = { refresh() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.refresh)) } }
            } else if (list != null && list.isEmpty()) {
                item { Text(stringResource(R.string.no_drive_snapshots), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (list != null) {
                items(list, key = { it.id }) { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = f }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge)
                            Text(f.modifiedTime.replace("T", " ").take(16), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${f.size / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showPassphrase) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<Int?>(null) }
        AlertDialog(
            onDismissRequest = { showPassphrase = false },
            title = { Text(stringResource(R.string.drive_passphrase)) },
            text = {
                Column {
                    Text(stringResource(R.string.drive_passphrase_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = p1, onValueChange = { p1 = it; error = null }, label = { Text(stringResource(R.string.drive_passphrase)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = p2,
                        onValueChange = { p2 = it; error = null },
                        label = { Text(stringResource(R.string.confirm_passphrase)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = error != null,
                        supportingText = error?.let { { Text(stringResource(it)) } },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        p1.length < 8 -> error = R.string.error_passphrase_too_short
                        p1 != p2 -> error = R.string.error_passphrases_differ
                        else -> {
                            secrets.savePassphrase(p1.toCharArray())
                            prefs.update { it.copy(drivePassphraseSet = true) }
                            toast(context, R.string.passphrase_set)
                            showPassphrase = false
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showPassphrase = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    selected?.let { f ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(f.name) },
            text = {
                Column {
                    TextButton(onClick = { selected = null; confirmRestore = f }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_from_drive)) }
                    TextButton(onClick = { selected = null; confirmDelete = f }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    confirmRestore?.let { f ->
        ConfirmDialog(
            title = stringResource(R.string.restore_backup),
            text = stringResource(R.string.restoring_will_erase_data),
            confirmLabel = stringResource(R.string.restore),
            onConfirm = {
                if (!settings.drivePassphraseSet && f.name.endsWith(DriveSync.EXTENSION)) { toast(context, R.string.passphrase_required); return@ConfirmDialog }
                withToken { token ->
                    busy = R.string.downloading
                    val result = runCatching { withContext(Dispatchers.IO) { DriveSync.restoreSnapshot(context, f, token) } }
                    busy = null
                    result.onSuccess { ok ->
                        toast(context, if (ok) R.string.backup_restored_successfully else R.string.error_restore_failed)
                        if (ok) {
                            prefs.update { it.copy(hasRunBefore = true) }
                            if (firstRun) nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
                        }
                    }.onFailure { e ->
                        if (e is SyncCrypto.WrongPassphraseException) toast(context, R.string.error_wrong_passphrase)
                        else toast(context, e.message ?: context.getString(R.string.error_restore_failed))
                    }
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
    confirmDelete?.let { f ->
        ConfirmDialog(
            title = stringResource(R.string.delete_backup),
            text = stringResource(R.string.backup_will_be_deleted),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                withToken { token ->
                    runCatching { withContext(Dispatchers.IO) { DriveSync.deleteSnapshot(context, f, token) } }
                        .onSuccess { toast(context, R.string.backup_deleted_successfully); refresh() }
                        .onFailure { toast(context, R.string.error_backup_delete_failed) }
                }
            },
            onDismiss = { confirmDelete = null },
        )
    }
}
