package com.monito.expense.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.ui.Routes
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown once: restore an old backup, or start fresh with an optional opening balance. */
@Composable
fun FirstRunScreen(nav: NavController) {
    val prefs = rememberPrefs()
    val settings by prefs.settings.collectAsState()
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    var restoreMode by remember { mutableStateOf(false) }
    var driveMode by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf("") }

    if (restoreMode) {
        BackupsScreen(nav, firstRun = true)
        return
    }
    if (driveMode) {
        DriveScreen(nav, firstRun = true)
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.expense_manager), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.existing_user), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.existing_user_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { restoreMode = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_backup)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { driveMode = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_from_drive)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.start_from_here), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.start_fresh_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = { Text(stringResource(R.string.opening_balance_optional)) },
                    prefix = { Text(settings.currency.symbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val amount = AmountFormatter.parse(opening, settings.currency)
                            withContext(Dispatchers.IO) {
                                if (amount != null && amount.signum() > 0) repo.addOpeningBalance(amount)
                            }
                            prefs.update { it.copy(hasRunBefore = true) }
                            nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.start_from_here)) }
            }
        }
    }
}
