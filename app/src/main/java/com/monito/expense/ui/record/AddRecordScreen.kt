package com.monito.expense.ui.record

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.Template
import com.monito.expense.data.Transaction
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.CategoryPickerDialog
import com.monito.expense.ui.ConfirmDialog
import com.monito.expense.ui.MonitoDatePickerDialog
import com.monito.expense.ui.PickerField
import com.monito.expense.ui.amountColor
import com.monito.expense.ui.pretty
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Add or edit a transaction: amount (with a small calculator), category, date, note and an optional photo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(nav: NavController, recordId: Int, presetDate: String?, templateId: Int = -1) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val scope = rememberCoroutineScope()
    val editing = recordId >= 0

    var loaded by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    var date by remember { mutableStateOf(presetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var imgPath by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
    var amountError by remember { mutableStateOf<Int?>(null) }
    var categoryError by remember { mutableStateOf<Int?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showPhotoChooser by remember { mutableStateOf(false) }
    var showPhoto by remember { mutableStateOf(false) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    fun applyTemplate(t: Template) {
        t.amount?.let { amountText = it.stripTrailingZeros().toPlainString() }
        t.catId?.let { id -> categories.firstOrNull { it.id == id }?.let { category = it; categoryError = null } }
        if (t.note.isNotBlank()) note = t.note
    }

    LaunchedEffect(recordId) {
        val result = withContext(Dispatchers.IO) {
            val cats = repo.categories()
            val t: Transaction? = if (editing) repo.transaction(recordId) else null
            val cat = t?.let { repo.category(it.catId) }
            val tpls = if (editing) emptyList() else repo.templates()
            listOf(cats, t, cat, tpls)
        }
        @Suppress("UNCHECKED_CAST")
        categories = result[0] as List<Category>
        (result[1] as Transaction?)?.let { t ->
            amountText = t.amount.stripTrailingZeros().toPlainString()
            note = t.note
            date = t.date
            imgPath = t.imgPath
        }
        (result[2] as Category?)?.let { category = it }
        @Suppress("UNCHECKED_CAST")
        templates = result[3] as List<Template>
        if (!editing && templateId >= 0) templates.firstOrNull { it.id == templateId }?.let { applyTemplate(it) }
        loaded = true
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCameraFile
        if (ok && f != null && f.exists()) imgPath = f.absolutePath else f?.delete()
        pendingCameraFile = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val target = newImageFile(context)
                        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                        target.absolutePath
                    }.getOrNull()
                }
                if (saved != null) imgPath = saved
            }
        }
    }

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
                if (editing) repo.updateTransaction(recordId, note, amount, cat.id, date, imgPath)
                else repo.insertTransaction(note, amount, cat.id, date, imgPath)
            }
            toast(context, if (editing) R.string.transaction_modified else R.string.transaction_added)
            nav.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing) R.string.edit_transaction else R.string.add_transaction)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { showSaveTemplate = true }) { Icon(Icons.Default.BookmarkAdd, contentDescription = stringResource(R.string.save_as_template)) }
                    if (editing) {
                        IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
                    }
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
            if (!editing && templates.isNotEmpty()) {
                Text(stringResource(R.string.quick_templates), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    items(templates, key = { it.id }) { t ->
                        SuggestionChip(
                            onClick = { applyTemplate(t) },
                            label = {
                                val amt = t.amount?.let { " · " + AmountFormatter.format(it, settings.currency) } ?: ""
                                Text(t.name + amt)
                            },
                            icon = if (t.colorCode != null) ({ CategoryAvatar(t.colorCode, t.colorTextType, t.catName ?: t.name, 20.dp) }) else null,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; amountError = null },
                label = { Text(stringResource(R.string.amount)) },
                placeholder = { Text(stringResource(R.string.enter_amount)) },
                prefix = if (settings.currency.symbolOnLeft) ({ Text(settings.currency.symbol) }) else null,
                suffix = if (!settings.currency.symbolOnLeft) ({ Text(settings.currency.symbol) }) else null,
                isError = amountError != null,
                supportingText = amountError?.let { { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = if (cat != null) amountColor(cat.type) else MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                for (op in listOf("+", "-", "×", "÷")) {
                    AssistChip(onClick = { amountText += op }, label = { Text(op) })
                }
                AssistChip(
                    onClick = {
                        AmountFormatter.parse(amountText, settings.currency)?.let { amountText = it.toPlainString() }
                    },
                    label = { Text("=") },
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showCategories = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cat != null) CategoryAvatar(cat.colorCode, cat.colorTextType, cat.name)
                else Box(Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        cat?.name ?: stringResource(R.string.select_category),
                        style = MaterialTheme.typography.bodyLarge,
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
            if (categoryError != null) {
                Text(stringResource(categoryError!!), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))

            PickerField(value = date.pretty(), label = stringResource(R.string.date), modifier = Modifier.fillMaxWidth()) { showDate = true }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_optional)) },
                supportingText = { Text(stringResource(R.string.tags_hint)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
            Spacer(Modifier.height(16.dp))

            val path = imgPath
            if (path != null && File(path).exists()) {
                Text(stringResource(R.string.attached_photo), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = stringResource(R.string.attached_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPhoto = true },
                    )
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = { imgPath = null }) { Text(stringResource(R.string.remove)) }
                }
            } else {
                FilledTonalButton(onClick = { showPhotoChooser = true }) {
                    Icon(Icons.Default.AddAPhoto, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_photo))
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }

    if (showCategories) {
        CategoryPickerDialog(categories, onDismiss = { showCategories = false }) { category = it; categoryError = null }
    }
    if (showDate) MonitoDatePickerDialog(date, onDismiss = { showDate = false }) { date = it }
    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.transaction_moved_to_trash),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.moveToTrash(recordId) }
                    toast(context, R.string.transaction_moved_to_trash)
                    nav.popBackStack()
                }
            },
            onDismiss = { showDelete = false },
        )
    }
    if (showSaveTemplate) {
        var name by remember { mutableStateOf(category?.name ?: "") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSaveTemplate = false },
            title = { Text(stringResource(R.string.save_as_template)) },
            text = {
                Column {
                    Text(stringResource(R.string.templates_desc), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = false },
                        label = { Text(stringResource(R.string.template_name)) },
                        isError = error,
                        supportingText = if (error) ({ Text(stringResource(R.string.error_template_name_cant_be_empty)) }) else null,
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) { error = true; return@TextButton }
                    val amount = AmountFormatter.parse(amountText, settings.currency)
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.insertTemplate(name, amount, category?.id, note) }
                        toast(context, R.string.template_saved)
                    }
                    showSaveTemplate = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showSaveTemplate = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPhotoChooser) {
        AlertDialog(
            onDismissRequest = { showPhotoChooser = false },
            title = { Text(stringResource(R.string.add_photo)) },
            text = {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoChooser = false
                                val f = newImageFile(context)
                                pendingCameraFile = f
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                takePicture.launch(uri)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.take_photo))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoChooser = false
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.choose_image))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPhotoChooser = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPhoto && imgPath != null) {
        AlertDialog(
            onDismissRequest = { showPhoto = false },
            text = {
                AsyncImage(
                    model = File(imgPath!!),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { showPhoto = false }) { Text(stringResource(R.string.okay)) } },
        )
    }
}

private fun newImageFile(context: android.content.Context): File {
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "monito_images").apply { mkdirs() }
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return File(dir, "IMG_$stamp.jpg")
}
