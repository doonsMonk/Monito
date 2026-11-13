package com.monito.expense.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.monito.expense.drive.SyncCrypto
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.monito.expense.data.BackupFile
import com.monito.expense.ui.ConfirmDialog
import com.monito.expense.ui.EmptyState
import com.monito.expense.ui.Routes
import com.monito.expense.ui.rememberBackupManager
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.getDefault())

private fun sizeText(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * Lists local backups and lets the user restore, share, export or delete them, and import a
 * backup file from anywhere on the phone (for example one made by the old Monito app).
 * When [firstRun] is true a successful restore finishes onboarding and opens the main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(nav: NavController, firstRun: Boolean = false) {
    val context = LocalContext.current
    val manager = rememberBackupManager()
    val prefs = rememberPrefs()
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf<List<BackupFile>?>(null) }
    var selected by remember { mutableStateOf<BackupFile?>(null) }
    var confirmRestore by remember { mutableStateOf<BackupFile?>(null) }
    var confirmDelete by remember { mutableStateOf<BackupFile?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingEncrypted by remember { mutableStateOf<ByteArray?>(null) }
    var exporting by remember { mutableStateOf<BackupFile?>(null) }

    LaunchedEffect(reload) { list = withContext(Dispatchers.IO) { manager.list() } }

    fun finishRestore(ok: Boolean) {
        toast(context, if (ok) R.string.backup_restored_successfully else R.string.error_restore_failed)
        if (ok) {
            prefs.update { it.copy(hasRunBefore = true) }
            if (firstRun) {
                nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = exporting
        exporting = null
        if (uri != null && f != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { manager.exportToUri(File(f.path), uri) }.isSuccess }
                toast(context, if (ok) R.string.export_successful else R.string.export_failed)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.view_restore_backups)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Backup, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_backup_file))
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { runCatching { manager.createBackup() }.isSuccess }
                                toast(context, if (ok) R.string.backup_successful else R.string.error_could_not_create_backup)
                                reload++
                            }
                        },
                    ) { Text(stringResource(R.string.backup)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.import_backup_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.phone_storage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            val items = list ?: return@Column
            if (items.isEmpty()) {
                EmptyState(stringResource(R.string.no_backups_on_phone))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.path }) { b ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = b }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(b.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.created_on, Instant.ofEpochMilli(b.modified).atZone(ZoneId.systemDefault()).format(STAMP)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(sizeText(b.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    val sel = selected
    if (sel != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(sel.name) },
            text = {
                Column {
                    TextButton(onClick = { selected = null; confirmRestore = sel }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore)) }
                    TextButton(
                        onClick = {
                            selected = null
                            val uri = manager.shareUri(sel)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, sel.name))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.share)) }
                    TextButton(
                        onClick = { selected = null; exporting = sel; exportLauncher.launch(sel.name) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.export_backup)) }
                    TextButton(onClick = { selected = null; confirmDelete = sel }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.delete_backup), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    confirmRestore?.let { b ->
        ConfirmDialog(
            title = stringResource(R.string.restore_backup),
            text = stringResource(R.string.restoring_will_erase_data),
            confirmLabel = stringResource(R.string.restore),
            onConfirm = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { manager.restore(File(b.path)) }.getOrDefault(false) }
                    finishRestore(ok)
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
    confirmDelete?.let { b ->
        ConfirmDialog(
            title = stringResource(R.string.delete_backup),
            text = stringResource(R.string.backup_will_be_deleted),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                val ok = manager.delete(b)
                toast(context, if (ok) R.string.backup_deleted_successfully else R.string.error_backup_delete_failed)
                reload++
            },
            onDismiss = { confirmDelete = null },
        )
    }
    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_backup),
            text = stringResource(R.string.restoring_will_erase_data),
            confirmLabel = stringResource(R.string.restore),
            onConfirm = {
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) { runCatching { manager.readUri(uri) }.getOrNull() }
                    if (bytes != null && manager.needsPassphrase(bytes)) {
                        // A downloaded Google Drive copy: ask for the passphrase it was encrypted with.
                        pendingEncrypted = bytes
                    } else {
                        val ok = bytes != null && withContext(Dispatchers.IO) { runCatching { manager.restoreBytes(bytes) }.getOrDefault(false) }
                        finishRestore(ok)
                    }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
    pendingEncrypted?.let { bytes ->
        var passphrase by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) pendingEncrypted = null },
            title = { Text(stringResource(R.string.drive_passphrase)) },
            text = {
                Column {
                    Text(stringResource(R.string.encrypted_backup_desc))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it; wrong = false },
                        label = { Text(stringResource(R.string.drive_passphrase)) },
                        singleLine = true,
                        isError = wrong,
                        supportingText = if (wrong) ({ Text(stringResource(R.string.error_wrong_passphrase)) }) else null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && passphrase.isNotEmpty(),
                    onClick = {
                        busy = true
                        scope.launch {
                            // null = wrong passphrase, otherwise whether the restore itself worked
                            val result: Boolean? = withContext(Dispatchers.IO) {
                                try {
                                    manager.restoreEncrypted(bytes, passphrase.toCharArray())
                                } catch (e: SyncCrypto.WrongPassphraseException) {
                                    null
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            busy = false
                            if (result == null) {
                                wrong = true
                            } else {
                                pendingEncrypted = null
                                finishRestore(result)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingEncrypted = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
