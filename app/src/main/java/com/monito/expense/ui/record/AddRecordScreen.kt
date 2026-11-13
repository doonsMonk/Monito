package com.monito.expense.ui.record

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.Template
import com.monito.expense.data.Transaction
import com.monito.expense.ui.AmountPadDialog
import com.monito.expense.ui.CategoryAvatar
import com.monito.expense.ui.CategoryPickerDialog
import com.monito.expense.ui.ConfirmDialog
import com.monito.expense.ui.MonitoDatePickerDialog
import com.monito.expense.ui.amountColor
import com.monito.expense.ui.amountPadText
import com.monito.expense.ui.rememberPrefs
import com.monito.expense.ui.rememberRepository
import com.monito.expense.ui.short
import com.monito.expense.ui.theme.categoryColor
import com.monito.expense.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val AmountSaver = Saver<BigDecimal?, String>(
    save = { it?.toPlainString() ?: "" },
    restore = { s -> s.takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() } },
)

private val DateSaver = Saver<LocalDate, String>(save = { it.toString() }, restore = { LocalDate.parse(it) })

private val CategorySaver = listSaver<Category?, Any>(
    save = { c -> if (c == null) emptyList() else listOf(c.id, c.name, c.type.dbValue, c.count, c.colorId, c.colorCode, c.colorTextType, c.isFavorite) },
    restore = { v ->
        if (v.isEmpty()) null
        else Category(v[0] as Int, v[1] as String, CatType.fromDb(v[2] as String), v[3] as Int, v[4] as Int, v[5] as String, v[6] as Int, v[7] as Boolean)
    },
)

/** Steps of adding a new transaction, as in the original app: amount, then category, then the details. */
private enum class Step { AMOUNT, CATEGORY, DETAILS }

/**
 * Add or edit a transaction. A new one is entered in three steps: the calculator for the amount,
 * the category picker, and a details page with date, amount, category, note and photo. On the
 * details page, tapping the amount or the category reopens that step. Editing starts on the
 * details page.
 */
@Composable
fun AddRecordScreen(nav: NavController, recordId: Int, presetDate: String?, templateId: Int = -1) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val settings by rememberPrefs().settings.collectAsState()
    val scope = rememberCoroutineScope()
    val editing = recordId >= 0

    var loaded by remember { mutableStateOf(false) }
    // Saved across rotation: once the first load has filled the fields, a reload must not reset them.
    var initialized by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableStateOf(Step.DETAILS) }
    var amount by rememberSaveable(stateSaver = AmountSaver) { mutableStateOf<BigDecimal?>(null) }
    var category by rememberSaveable(stateSaver = CategorySaver) { mutableStateOf<Category?>(null) }
    var date by rememberSaveable(stateSaver = DateSaver) {
        mutableStateOf(presetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
    }
    var note by rememberSaveable { mutableStateOf("") }
    var imgPath by rememberSaveable { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
    var amountError by remember { mutableStateOf(false) }
    var categoryError by remember { mutableStateOf(false) }
    var showAmountPad by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showPhotoChooser by remember { mutableStateOf(false) }
    var showPhoto by remember { mutableStateOf(false) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun applyTemplate(t: Template) {
        t.amount?.let { amount = it; amountError = false }
        t.catId?.let { id -> categories.firstOrNull { it.id == id }?.let { category = it; categoryError = false } }
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
        @Suppress("UNCHECKED_CAST")
        templates = result[3] as List<Template>
        if (!initialized) {
            (result[1] as Transaction?)?.let { t ->
                amount = t.amount
                note = t.note
                date = t.date
                imgPath = t.imgPath
            }
            (result[2] as Category?)?.let { category = it }
            val template = if (!editing && templateId >= 0) templates.firstOrNull { it.id == templateId } else null
            template?.let { applyTemplate(it) }
            // A new transaction starts with the calculator; a template or an existing record goes straight to the details.
            step = if (editing || template != null) Step.DETAILS else Step.AMOUNT
            initialized = true
        }
        loaded = true
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCameraPath?.let { File(it) }
        if (ok && f != null && f.exists()) imgPath = f.absolutePath else f?.delete()
        pendingCameraPath = null
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
        val amt = amount
        val cat = category
        amountError = amt == null || amt.signum() <= 0
        categoryError = cat == null
        if (amt == null || amt.signum() <= 0 || cat == null) return
        scope.launch {
            withContext(Dispatchers.IO) {
                if (editing) repo.updateTransaction(recordId, note, amt, cat.id, date, imgPath)
                else repo.insertTransaction(note, amt, cat.id, date, imgPath)
            }
            toast(context, if (editing) R.string.transaction_modified else R.string.transaction_added)
            nav.popBackStack()
        }
    }

    if (!loaded) return

    // ---- step 1 and 2: shown on their own, over the screen underneath, until the details card is reached
    if (step != Step.DETAILS) {
        if (step == Step.AMOUNT) {
            AmountPadDialog(
                initial = amountPadText(amount, settings.currency),
                currency = settings.currency,
                onDismiss = { nav.popBackStack() },
                dismissOnOutside = false,
                header = if (templates.isNotEmpty()) ({
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        items(templates, key = { it.id }) { t ->
                            SuggestionChip(
                                onClick = { applyTemplate(t); step = Step.DETAILS },
                                label = {
                                    val amt = t.amount?.let { " · " + AmountFormatter.format(it, settings.currency) } ?: ""
                                    Text(t.name + amt)
                                },
                                icon = if (t.colorCode != null) ({ CategoryAvatar(t.colorCode, t.colorTextType, t.catName ?: t.name, 20.dp) }) else null,
                            )
                        }
                    }
                }) else null,
            ) { value ->
                amount = value
                amountError = false
                step = Step.CATEGORY
            }
        } else {
            CategoryPickerDialog(
                categories = categories,
                onDismiss = { step = Step.AMOUNT },
                dismissAfterPick = false,
                dismissOnOutside = false,
            ) { picked ->
                category = picked
                categoryError = false
                step = Step.DETAILS
            }
        }
        return
    }

    // ---- step 3: details
    val cat = category
    // A floating card over the screen underneath, like the original app, instead of a full page.
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .imePadding(),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel)) }
                Text(
                    stringResource(if (editing) R.string.edit_transaction else R.string.add_transaction),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showSaveTemplate = true }) { Icon(Icons.Default.BookmarkAdd, contentDescription = stringResource(R.string.save_as_template)) }
                if (editing) {
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
                }
            }
            FieldLabel(stringResource(R.string.date), first = true)
            FieldBox(onClick = { showDate = true }) {
                Text(date.short(), style = MaterialTheme.typography.titleMedium)
            }

            FieldLabel(stringResource(R.string.amount), error = amountError)
            FieldBox(onClick = { showAmountPad = true }) {
                Text(
                    amount?.let { AmountFormatter.format(it, settings.currency) } ?: stringResource(R.string.enter_amount),
                    style = MaterialTheme.typography.headlineSmall,
                    color = when {
                        amount == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        cat != null -> amountColor(cat.type)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (amountError) ErrorText(stringResource(R.string.error_amt_should_greater_than_zero))

            FieldLabel(stringResource(R.string.category), error = categoryError)
            FieldBox(onClick = { showCategories = true }) {
                if (cat != null) {
                    Box(Modifier.size(24.dp).background(categoryColor(cat.colorCode), CircleShape))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    cat?.name ?: stringResource(R.string.select_category),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (cat == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (categoryError) ErrorText(stringResource(R.string.error_cat_cant_be_empty))

            FieldLabel(stringResource(R.string.note))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.note_optional)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = fieldBackground(),
                        unfocusedContainerColor = fieldBackground(),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showPhotoChooser = true }) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.add_photo))
                }
            }

            val path = imgPath
            if (path != null && File(path).exists()) {
                Spacer(Modifier.height(16.dp))
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
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FloatingActionButton(onClick = { save() }, shape = CircleShape) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save_transaction))
                }
            }
        }
    }

    if (showAmountPad) {
        AmountPadDialog(
            initial = amountPadText(amount, settings.currency),
            currency = settings.currency,
            onDismiss = { showAmountPad = false },
        ) { value ->
            amount = value
            amountError = false
            showAmountPad = false
        }
    }
    if (showCategories) {
        CategoryPickerDialog(categories, onDismiss = { showCategories = false }) { category = it; categoryError = false }
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
                    val amt = amount
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.insertTemplate(name, amt, category?.id, note) }
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
                                pendingCameraPath = f.absolutePath
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

@Composable
private fun FieldLabel(text: String, error: Boolean = false, first: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = if (first) 4.dp else 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
}

/**
 * Background of the Date / Amount / Category / Note boxes: the card colour shifted towards the text
 * colour, so the boxes are a little darker than the card in light mode and a little lighter in dark mode.
 */
@Composable
private fun fieldBackground(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)

/** A rounded, tappable box that shows a value and opens its editor, like the fields of the original app. */
@Composable
private fun FieldBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(fieldBackground())
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

private fun newImageFile(context: android.content.Context): File {
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "monito_images").apply { mkdirs() }
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return File(dir, "IMG_$stamp.jpg")
}
