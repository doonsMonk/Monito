package com.monito.expense.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * All reads and writes against the Monito database. The SQL mirrors the queries of the original
 * app (same tables, columns, ordering and semantics) so the data stays interchangeable.
 */
class Repository private constructor(context: Context) {

    private val database = MonitoDatabase.get(context)
    private val db: SQLiteDatabase get() = database.open()

    private val _changes = MutableStateFlow(0L)

    /** Bumps every time something is written; screens collect it to reload. */
    val changes: StateFlow<Long> = _changes

    fun notifyChanged() {
        _changes.value = _changes.value + 1
    }

    // ------------------------------------------------------------------ categories

    fun categories(includeSpecial: Boolean = false): List<Category> {
        val where = if (includeSpecial) "" else " AND $NOT_SPECIAL"
        return db.list("$CATEGORY_SELECT$where order by cat_name COLLATE NOCASE") { it.toCategory() }
    }

    fun category(id: Int): Category? =
        db.list("$CATEGORY_SELECT AND cat_id=?", arrayOf(id.toString())) { it.toCategory() }.firstOrNull()

    fun categoryByName(name: String): Category? =
        db.list("$CATEGORY_SELECT AND cat_name=?", arrayOf(name)) { it.toCategory() }.firstOrNull()

    fun categoryNameExists(name: String, excludeId: Int? = null): Boolean =
        db.list("select cat_name from category") { it.getString(0) }
            .any { it.equals(name.trim(), ignoreCase = true) } &&
            (excludeId == null || category(excludeId)?.name?.equals(name.trim(), ignoreCase = true) != true)

    fun insertCategory(name: String, type: CatType, colorId: Int, isFavorite: Boolean): Long {
        val id = db.insertAndGetId(
            "insert into category (cat_name, cat_count, cat_type, cat_color_id, cat_is_fav) values (?, 0, ?, ?, ?)",
            arrayOf<Any?>(name.trim(), type.dbValue, colorId, if (isFavorite) 1 else 0),
        )
        notifyChanged()
        return id
    }

    fun updateCategory(id: Int, name: String, colorId: Int, isFavorite: Boolean) {
        db.execSQL(
            "update category set cat_name=?, cat_color_id=?, cat_is_fav=? where cat_id=?",
            arrayOf<Any?>(name.trim(), colorId, if (isFavorite) 1 else 0, id),
        )
        notifyChanged()
    }

    /** Deletes a category; its records move under "Other expenses" / "Other incomes" like the original app. */
    fun deleteCategory(id: Int) {
        val cat = category(id) ?: return
        val fallbackName = if (cat.type == CatType.INCOME) Category.OTHER_INCOMES else Category.OTHER_EXPENSES
        val fallback = categoryByName(fallbackName) ?: return
        db.transaction {
            execSQL("update main set cat_id=? where cat_id=?", arrayOf(fallback.id, id))
            execSQL("update trash set trash_cat_id=? where trash_cat_id=?", arrayOf(fallback.id, id))
            execSQL("delete from recurrence where recur_cat_id=?", arrayOf(id))
            execSQL("delete from category where cat_id=?", arrayOf(id))
        }
        notifyChanged()
    }

    fun colors(): List<ColorItem> =
        db.list("select color_id, color_code, color_text_type from color") {
            ColorItem(it.getInt(0), it.getString(1), it.getInt(2))
        }

    /** The palette colour used by the fewest categories, so a new category stands apart from the others. */
    fun leastUsedColorId(): Int? {
        val used = db.list("select cat_color_id, count(*) from category group by cat_color_id") { it.getInt(0) to it.getInt(1) }.toMap()
        return colors().minByOrNull { used[it.id] ?: 0 }?.id
    }

    /**
     * Spreads the palette over the categories: the first category of each colour keeps it, every
     * other one moves to the colour used least so far. Returns how many categories changed.
     */
    fun assignDistinctColors(): Int {
        val palette = colors().map { it.id }
        if (palette.isEmpty()) return 0
        val usage = palette.associateWith { 0 }.toMutableMap()
        val moved = ArrayList<Pair<Int, Int>>()
        val pending = ArrayList<Category>()
        for (c in categories(includeSpecial = true).sortedBy { it.id }) {
            if (c.colorId in usage && usage[c.colorId] == 0) usage[c.colorId] = 1 else pending.add(c)
        }
        for (c in pending) {
            val target = palette.minByOrNull { usage[it] ?: 0 } ?: continue
            usage[target] = (usage[target] ?: 0) + 1
            if (target != c.colorId) moved.add(c.id to target)
        }
        if (moved.isEmpty()) return 0
        db.transaction {
            for ((catId, colorId) in moved) execSQL("update category set cat_color_id=? where cat_id=?", arrayOf<Any?>(colorId, catId))
        }
        notifyChanged()
        return moved.size
    }

    // ------------------------------------------------------------------ transactions

    fun transactions(from: LocalDate, to: LocalDate): List<Transaction> =
        db.list(
            "$TX_SELECT AND main.date BETWEEN ? AND ? order by main.date desc, main.id desc",
            arrayOf(from.fmt(), to.fmt()),
        ) { it.toTransaction() }

    fun transactionsOfCategory(catId: Int, from: LocalDate, to: LocalDate): List<Transaction> =
        db.list(
            "$TX_SELECT AND main.cat_id=? AND main.date BETWEEN ? AND ? order by main.date desc, main.id desc",
            arrayOf(catId.toString(), from.fmt(), to.fmt()),
        ) { it.toTransaction() }

    fun transaction(id: Int): Transaction? =
        db.list("$TX_SELECT AND main.id=?", arrayOf(id.toString())) { it.toTransaction() }.firstOrNull()

    fun search(query: String, type: CatType? = null): List<Transaction> =
        search(SearchFilter(query = query, type = type))

    /** Filtered search over all transactions, newest first. */
    fun search(filter: SearchFilter): List<Transaction> {
        val where = StringBuilder()
        val args = ArrayList<String>()
        if (filter.query.isNotBlank()) {
            val q = "%${filter.query.trim()}%"
            where.append(" AND (main.comment like ? OR category.cat_name like ? OR main.amount like ?)")
            args.add(q); args.add(q); args.add(q)
        }
        filter.type?.let { where.append(" AND category.cat_type=?"); args.add(it.dbValue) }
        if (filter.catIds.isNotEmpty()) where.append(" AND main.cat_id in (${filter.catIds.joinToString(",")})")
        filter.minAmount?.let { where.append(" AND CAST(main.amount AS REAL) >= ?"); args.add(it.toPlainString()) }
        filter.maxAmount?.let { where.append(" AND CAST(main.amount AS REAL) <= ?"); args.add(it.toPlainString()) }
        filter.from?.let { where.append(" AND main.date >= ?"); args.add(it.fmt()) }
        filter.to?.let { where.append(" AND main.date <= ?"); args.add(it.fmt()) }
        if (filter.withPhoto) where.append(" AND main.img_path IS NOT NULL AND main.img_path != ''")
        filter.tag?.takeIf { it.isNotBlank() }?.let {
            val tag = if (it.startsWith("#")) it else "#$it"
            where.append(" AND main.comment like ?"); args.add("%$tag%")
        }
        return db.list("$TX_SELECT$where order by main.date desc, main.id desc", args.toTypedArray()) { it.toTransaction() }
    }

    /** Copies a transaction (without its photo) to [date]; returns the new id. */
    fun duplicateTransaction(id: Int, date: LocalDate = LocalDate.now()): Long? {
        val t = transaction(id) ?: return null
        return insertTransaction(t.note, t.amount, t.catId, date)
    }

    fun transactionCount(): Int = db.scalarInt("select count(id) from main")

    fun minDate(): LocalDate? = db.scalarString("SELECT MIN(date) FROM main")?.toDate()

    fun maxDate(): LocalDate? = db.scalarString("SELECT MAX(date) FROM main")?.toDate()

    fun insertTransaction(
        note: String,
        amount: BigDecimal,
        catId: Int,
        date: LocalDate,
        imgPath: String? = null,
        isAutoAdded: Boolean = false,
    ): Long {
        var id = 0L
        db.transaction {
            id = insertAndGetId(
                "insert into main (comment, amount, cat_id, date, is_auto_added, img_path) values (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(note.trim(), amount.toPlainString(), catId, date.fmt(), if (isAutoAdded) 1 else 0, imgPath ?: ""),
            )
            execSQL("update category set cat_count = cat_count + 1 where cat_id=?", arrayOf(catId))
        }
        notifyChanged()
        return id
    }

    fun updateTransaction(id: Int, note: String, amount: BigDecimal, catId: Int, date: LocalDate, imgPath: String?) {
        db.execSQL(
            "update main set comment=?, amount=?, cat_id=?, date=?, is_auto_added=0, img_path=? where id=?",
            arrayOf<Any?>(note.trim(), amount.toPlainString(), catId, date.fmt(), imgPath ?: "", id),
        )
        notifyChanged()
    }

    /** Moves a transaction to the trash table, stamping today as the deletion date. */
    fun moveToTrash(id: Int) {
        db.transaction {
            execSQL(
                "insert into trash(trash_id, trash_comment, trash_amount, trash_date, trash_cat_id, trash_deleted_on, trash_is_auto_added, trash_img_path) " +
                    "select id, comment, amount, date, cat_id, ?, is_auto_added, img_path from main where id=?",
                arrayOf<Any?>(LocalDate.now().fmt(), id),
            )
            execSQL("delete from main where id=?", arrayOf(id))
        }
        notifyChanged()
    }

    // ------------------------------------------------------------------ totals

    /** Sum of all transactions whose category has [type] within the inclusive date range. */
    fun totalOfType(type: CatType, from: LocalDate, to: LocalDate): BigDecimal =
        db.list(
            "select main.amount from main, category where category.cat_id=main.cat_id and category.cat_type=? AND main.date BETWEEN ? AND ?",
            arrayOf(type.dbValue, from.fmt(), to.fmt()),
        ) { it.getString(0).toAmount() }.fold(BigDecimal.ZERO) { acc, v -> acc + v }

    fun totalOfCategories(catIds: Collection<Int>, from: LocalDate, to: LocalDate): BigDecimal {
        if (catIds.isEmpty()) return BigDecimal.ZERO
        val ids = catIds.joinToString(",")
        return db.list(
            "select main.amount from main where main.cat_id in ($ids) AND main.date BETWEEN ? AND ?",
            arrayOf(from.fmt(), to.fmt()),
        ) { it.getString(0).toAmount() }.fold(BigDecimal.ZERO) { acc, v -> acc + v }
    }

    /** Per category totals of one type within the range, largest first. Categories without records are omitted. */
    fun categoryTotals(type: CatType, from: LocalDate, to: LocalDate): List<CategoryTotal> {
        val sums = LinkedHashMap<Int, CategoryTotal>()
        db.list(
            "select category.cat_id, category.cat_name, color.color_code, color.color_text_type, main.amount " +
                "from main, category, color where category.cat_id=main.cat_id and category.cat_color_id=color.color_id " +
                "and category.cat_type=? AND main.date BETWEEN ? AND ?",
            arrayOf(type.dbValue, from.fmt(), to.fmt()),
        ) { c ->
            val id = c.getInt(0)
            val prev = sums[id]
            val amount = c.getString(4).toAmount()
            sums[id] = prev?.copy(total = prev.total + amount)
                ?: CategoryTotal(id, c.getString(1), c.getString(2), c.getInt(3), amount)
        }
        return sums.values.sortedByDescending { it.total }
    }

    // ------------------------------------------------------------------ trash

    fun trashItems(): List<TrashItem> =
        db.list("$TRASH_SELECT order by trash.trash_date desc, trash.trash_id desc") { it.toTrashItem() }

    fun trashCount(): Int = db.scalarInt("select count(trash_id) from trash")

    fun restoreFromTrash(trashId: Int) {
        db.transaction {
            execSQL(
                "insert into main(id, comment, amount, date, cat_id, is_auto_added, img_path) " +
                    "select trash_id, trash_comment, trash_amount, trash_date, trash_cat_id, trash_is_auto_added, trash_img_path from trash where trash_id=?",
                arrayOf<Any?>(trashId),
            )
            execSQL("delete from trash where trash_id=?", arrayOf(trashId))
        }
        notifyChanged()
    }

    fun deleteFromTrash(trashId: Int) {
        val img = db.scalarString("select trash_img_path from trash where trash_id=$trashId")
        db.execSQL("delete from trash where trash_id=?", arrayOf(trashId))
        deleteImageFile(img)
        notifyChanged()
    }

    fun clearTrash() {
        db.list("select trash_img_path from trash") { it.getString(0) }.forEach { deleteImageFile(it) }
        db.execSQL("delete from trash")
        notifyChanged()
    }

    /** Removes trash entries deleted more than [days] days ago (the original used 7). */
    fun deleteOldTrash(days: Long = 7): Int {
        val cutoff = LocalDate.now().minusDays(days)
        var removed = 0
        db.list("select trash_id, trash_deleted_on from trash") { it.getInt(0) to it.getString(1) }
            .forEach { (id, deletedOn) ->
                val d = deletedOn?.toDate() ?: return@forEach
                if (d.isBefore(cutoff)) {
                    deleteFromTrash(id)
                    removed++
                }
            }
        return removed
    }

    private fun deleteImageFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    // ------------------------------------------------------------------ recurrences

    fun recurrences(): List<Recurrence> =
        db.list("$RECUR_SELECT order by recur_on") { it.toRecurrence() }

    fun recurrence(id: Int): Recurrence? =
        db.list("$RECUR_SELECT AND recur_id=?", arrayOf(id.toString())) { it.toRecurrence() }.firstOrNull()

    fun recurrenceCount(): Int = db.scalarInt("select count(recur_id) from recurrence")

    fun insertRecurrence(note: String, catId: Int, amount: BigDecimal, type: RecurType, nextOn: LocalDateTime, endDate: LocalDate? = null): Long {
        val id = db.insertAndGetId(
            "insert into recurrence (recur_note, recur_cat_id, recur_amount, recur_type, recur_on, recur_end) values (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(note.trim(), catId, amount.toPlainString(), type.code, nextOn.fmt(), endDate?.fmt()),
        )
        notifyChanged()
        return id
    }

    fun updateRecurrence(id: Int, note: String, catId: Int, amount: BigDecimal, type: RecurType, nextOn: LocalDateTime, endDate: LocalDate? = null) {
        db.execSQL(
            "update recurrence set recur_note=?, recur_cat_id=?, recur_amount=?, recur_type=?, recur_on=?, recur_end=? where recur_id=?",
            arrayOf<Any?>(note.trim(), catId, amount.toPlainString(), type.code, nextOn.fmt(), endDate?.fmt(), id),
        )
        notifyChanged()
    }

    /** Moves the next run forward by one period without adding a transaction. */
    fun skipNextRecurrence(id: Int) {
        val r = recurrence(id) ?: return
        db.execSQL("update recurrence set recur_on=? where recur_id=?", arrayOf<Any?>(advance(r.nextOn, r.type).fmt(), id))
        notifyChanged()
    }

    fun deleteRecurrence(id: Int) {
        db.execSQL("delete from recurrence where recur_id = ?", arrayOf(id))
        notifyChanged()
    }

    /** One transaction added automatically by [processDueRecurrences]. */
    data class AddedRecurrence(val note: String, val catName: String, val amount: BigDecimal, val date: LocalDate)

    /**
     * Inserts a transaction for every recurrence whose next run time has passed and moves it forward,
     * repeating until it is in the future again (catches up after days without the app running).
     * Recurrences with an end date stop firing after it. Returns what was added.
     */
    fun processDueRecurrencesDetailed(now: LocalDateTime = LocalDateTime.now()): List<AddedRecurrence> {
        val added = ArrayList<AddedRecurrence>()
        for (r in recurrences()) {
            var next = r.nextOn
            var guard = 0
            while (next.isBefore(now) && guard++ < 1000) {
                val end = r.endDate
                if (end != null && next.toLocalDate().isAfter(end)) break
                val date = next.toLocalDate()
                db.transaction {
                    execSQL(
                        "insert into main (comment, amount, cat_id, date, is_auto_added) values (?, ?, ?, ?, 1)",
                        arrayOf<Any?>(r.note, r.amount.toPlainString(), r.catId, date.fmt()),
                    )
                    next = advance(next, r.type)
                    execSQL("update recurrence set recur_on=? where recur_id=?", arrayOf<Any?>(next.fmt(), r.id))
                }
                added.add(AddedRecurrence(r.note, r.catName, r.amount, date))
            }
        }
        if (added.isNotEmpty()) notifyChanged()
        return added
    }

    fun processDueRecurrences(now: LocalDateTime = LocalDateTime.now()): Int = processDueRecurrencesDetailed(now).size

    /** The next [count] dates a rule would fire, for previews. */
    fun previewRecurrence(type: RecurType, firstOn: LocalDateTime, endDate: LocalDate?, count: Int = 5): List<LocalDateTime> {
        val out = ArrayList<LocalDateTime>()
        var next = firstOn
        while (out.size < count) {
            if (endDate != null && next.toLocalDate().isAfter(endDate)) break
            out.add(next)
            next = advance(next, type)
        }
        return out
    }

    // ------------------------------------------------------------------ budgets

    fun budgets(): List<Budget> =
        db.list(
            "select budget.cat_id, category.cat_name, color.color_code, color.color_text_type, budget.amount " +
                "from budget, category, color where budget.cat_id=category.cat_id and category.cat_color_id=color.color_id " +
                "order by category.cat_name COLLATE NOCASE",
        ) { Budget(it.getInt(0), it.getString(1), it.getString(2), it.getInt(3), it.getString(4).toAmount()) }

    fun budgetFor(catId: Int): BigDecimal? =
        db.list("select amount from budget where cat_id=?", arrayOf(catId.toString())) { it.getString(0).toAmount() }.firstOrNull()

    fun setBudget(catId: Int, amount: BigDecimal) {
        db.execSQL("delete from budget where cat_id=?", arrayOf<Any?>(catId))
        db.execSQL("insert into budget (cat_id, amount) values (?, ?)", arrayOf<Any?>(catId, amount.toPlainString()))
        notifyChanged()
    }

    fun deleteBudget(catId: Int) {
        db.execSQL("delete from budget where cat_id=?", arrayOf<Any?>(catId))
        notifyChanged()
    }

    // ------------------------------------------------------------------ templates

    fun templates(): List<Template> =
        db.list(
            "select template.template_id, template.name, template.amount, template.cat_id, category.cat_name, category.cat_type, " +
                "color.color_code, color.color_text_type, template.comment from template " +
                "left join category on category.cat_id=template.cat_id left join color on color.color_id=category.cat_color_id " +
                "order by template.sort_order, template.name COLLATE NOCASE",
        ) { c ->
            Template(
                id = c.getInt(0),
                name = c.getString(1),
                amount = c.getString(2)?.takeIf { it.isNotBlank() }?.toAmount(),
                catId = if (c.isNull(3)) null else c.getInt(3),
                catName = c.getString(4),
                catType = c.getString(5)?.let { CatType.fromDb(it) },
                colorCode = c.getString(6),
                colorTextType = if (c.isNull(7)) 1 else c.getInt(7),
                note = c.getString(8) ?: "",
            )
        }

    fun insertTemplate(name: String, amount: BigDecimal?, catId: Int?, note: String): Long {
        val id = db.insertAndGetId(
            "insert into template (name, amount, cat_id, comment) values (?, ?, ?, ?)",
            arrayOf<Any?>(name.trim(), amount?.toPlainString(), catId, note.trim()),
        )
        notifyChanged()
        return id
    }

    fun updateTemplate(id: Int, name: String, amount: BigDecimal?, catId: Int?, note: String) {
        db.execSQL(
            "update template set name=?, amount=?, cat_id=?, comment=? where template_id=?",
            arrayOf<Any?>(name.trim(), amount?.toPlainString(), catId, note.trim(), id),
        )
        notifyChanged()
    }

    fun deleteTemplate(id: Int) {
        db.execSQL("delete from template where template_id=?", arrayOf<Any?>(id))
        notifyChanged()
    }

    // ------------------------------------------------------------------ trends

    /** Income and expense per calendar month between [from] and [to] inclusive; months without data are included as zero. */
    fun monthlyTotals(from: YearMonth, to: YearMonth): List<MonthTotal> {
        val income = HashMap<YearMonth, BigDecimal>()
        val expense = HashMap<YearMonth, BigDecimal>()
        db.list(
            "select main.date, main.amount, category.cat_type from main, category where category.cat_id=main.cat_id " +
                "AND main.date BETWEEN ? AND ?",
            arrayOf(from.atDay(1).fmt(), to.atEndOfMonth().fmt()),
        ) { c ->
            val ym = c.getString(0).toDate()?.let { YearMonth.from(it) }
            if (ym != null) {
                val target = if (CatType.fromDb(c.getString(2)) == CatType.INCOME) income else expense
                target[ym] = (target[ym] ?: BigDecimal.ZERO) + c.getString(1).toAmount()
            }
        }
        val out = ArrayList<MonthTotal>()
        var ym = from
        while (!ym.isAfter(to)) {
            out.add(MonthTotal(ym, income[ym] ?: BigDecimal.ZERO, expense[ym] ?: BigDecimal.ZERO))
            ym = ym.plusMonths(1)
        }
        return out
    }

    /** Monthly totals of a single category between [from] and [to] inclusive. */
    fun categoryMonthlyTotals(catId: Int, from: YearMonth, to: YearMonth): List<Pair<YearMonth, BigDecimal>> {
        val sums = HashMap<YearMonth, BigDecimal>()
        db.list(
            "select date, amount from main where cat_id=? AND date BETWEEN ? AND ?",
            arrayOf(catId.toString(), from.atDay(1).fmt(), to.atEndOfMonth().fmt()),
        ) { c ->
            val ym = c.getString(0).toDate()?.let { YearMonth.from(it) }
            if (ym != null) sums[ym] = (sums[ym] ?: BigDecimal.ZERO) + c.getString(1).toAmount()
        }
        val out = ArrayList<Pair<YearMonth, BigDecimal>>()
        var ym = from
        while (!ym.isAfter(to)) {
            out.add(ym to (sums[ym] ?: BigDecimal.ZERO))
            ym = ym.plusMonths(1)
        }
        return out
    }

    /** Distinct `#tags` used in notes, most used first. */
    fun tags(): List<String> {
        val counts = HashMap<String, Int>()
        db.list("select comment from main where comment like '%#%'") { it.getString(0) ?: "" }.forEach { note ->
            TAG_REGEX.findAll(note).forEach { m -> val tag = m.value.lowercase(); counts[tag] = (counts[tag] ?: 0) + 1 }
        }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
    }

    // ------------------------------------------------------------------ misc

    fun addOpeningBalance(amount: BigDecimal, date: LocalDate = LocalDate.now()) {
        val cat = categoryByName(Category.OPENING_BALANCE) ?: return
        insertTransaction("", amount, cat.id, date)
    }

    fun databaseVersion(): String = DatabaseUpdater.currentVersion(db) ?: "?"

    /** Closes the connection, runs [block] on the raw file, then reopens and upgrades if needed. */
    fun <T> withDatabaseClosed(block: (File) -> T): T = database.withClosed(block).also { notifyChanged() }

    val databaseFile: File get() = database.file

    // ------------------------------------------------------------------ row mappers

    private fun Cursor.toCategory() = Category(
        id = getInt(0),
        name = getString(1),
        type = CatType.fromDb(getString(2)),
        count = getInt(3),
        colorId = getInt(4),
        colorCode = getString(5),
        colorTextType = getInt(6),
        isFavorite = getInt(7) == 1,
    )

    private fun Cursor.toTransaction() = Transaction(
        id = getInt(0),
        date = getString(1).toDate() ?: LocalDate.now(),
        catName = getString(2),
        amount = getString(3).toAmount(),
        note = getString(4) ?: "",
        colorCode = getString(5),
        colorTextType = getInt(6),
        isAutoAdded = getInt(7) == 1,
        imgPath = getString(8)?.takeIf { it.isNotBlank() },
        catType = CatType.fromDb(getString(9)),
        catId = getInt(10),
    )

    private fun Cursor.toTrashItem() = TrashItem(
        id = getInt(0),
        date = getString(1).toDate() ?: LocalDate.now(),
        catName = getString(2),
        amount = getString(3).toAmount(),
        note = getString(4) ?: "",
        isAutoAdded = getInt(5) == 1,
        imgPath = getString(6)?.takeIf { it.isNotBlank() },
        catType = CatType.fromDb(getString(7)),
        catId = getInt(8),
        deletedOn = getString(9)?.toDate(),
    )

    private fun Cursor.toRecurrence() = Recurrence(
        note = getString(0) ?: "",
        amount = getString(1).toAmount(),
        catName = getString(2),
        type = RecurType.fromCode(getInt(3)),
        nextOn = getString(4).toDateTime() ?: LocalDateTime.now(),
        id = getInt(5),
        catType = CatType.fromDb(getString(6)),
        colorCode = getString(7),
        colorTextType = getInt(8),
        catId = getInt(9),
        endDate = getString(10).toDate(),
    )

    companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)

        val TAG_REGEX = Regex("#[\\p{L}\\p{N}_-]+")

        private const val NOT_SPECIAL =
            "cat_name !='Other expenses' and cat_name !='Other incomes' and cat_name !='Opening balance'"

        private const val CATEGORY_SELECT =
            "select cat_id, cat_name, cat_type, cat_count, cat_color_id, color.color_code, color.color_text_type, cat_is_fav " +
                "from category, color where category.cat_color_id=color.color_id"

        private const val TX_SELECT =
            "select main.id, main.date, category.cat_name, main.amount, main.comment, color.color_code, color.color_text_type, " +
                "main.is_auto_added, main.img_path, category.cat_type, main.cat_id from main, category, color " +
                "where main.cat_id=category.cat_id AND category.cat_color_id=color.color_id"

        private const val TRASH_SELECT =
            "select trash.trash_id, trash.trash_date, category.cat_name, trash.trash_amount, trash.trash_comment, " +
                "trash.trash_is_auto_added, trash.trash_img_path, category.cat_type, trash.trash_cat_id, trash.trash_deleted_on " +
                "from trash, category where trash.trash_cat_id=category.cat_id"

        private const val RECUR_SELECT =
            "select recur_note, recur_amount, category.cat_name, recur_type, recur_on, recur_id, category.cat_type, " +
                "color.color_code, color.color_text_type, recur_cat_id, recur_end from recurrence, category, color " +
                "where recurrence.recur_cat_id=category.cat_id AND category.cat_color_id=color.color_id"

        fun advance(from: LocalDateTime, type: RecurType): LocalDateTime = when (type) {
            RecurType.DAILY -> from.plusDays(1)
            RecurType.WEEKLY -> from.plusWeeks(1)
            RecurType.MONTHLY -> from.plusMonths(1)
            RecurType.YEARLY -> from.plusYears(1)
        }

        fun LocalDate.fmt(): String = format(DATE_FORMAT)
        fun LocalDateTime.fmt(): String = format(DATE_TIME_FORMAT)

        fun String?.toDate(): LocalDate? =
            this?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10), DATE_FORMAT) }.getOrNull() }

        fun String?.toDateTime(): LocalDateTime? =
            this?.let { runCatching { LocalDateTime.parse(it, DATE_TIME_FORMAT) }.getOrNull() }

        fun String?.toAmount(): BigDecimal =
            this?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: BigDecimal.ZERO

        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }

        /** Drops the singletons so unit tests get a fresh database per test. */
        internal fun resetForTests() {
            synchronized(this) { instance = null }
            MonitoDatabase.resetForTests()
        }

        // ---- small SQLite helpers

        private fun <T> SQLiteDatabase.list(sql: String, args: Array<String>? = null, map: (Cursor) -> T): List<T> =
            rawQuery(sql, args).use { c ->
                val out = ArrayList<T>(c.count)
                while (c.moveToNext()) out.add(map(c))
                out
            }

        private fun SQLiteDatabase.scalarInt(sql: String): Int =
            rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        private fun SQLiteDatabase.scalarString(sql: String): String? =
            rawQuery(sql, null).use { if (it.moveToFirst()) it.getString(0) else null }

        private fun SQLiteDatabase.insertAndGetId(sql: String, args: Array<Any?>): Long =
            compileStatement(sql).use { st ->
                args.forEachIndexed { i, a ->
                    when (a) {
                        null -> st.bindNull(i + 1)
                        is Int -> st.bindLong(i + 1, a.toLong())
                        is Long -> st.bindLong(i + 1, a)
                        else -> st.bindString(i + 1, a.toString())
                    }
                }
                st.executeInsert()
            }

        private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
            beginTransaction()
            try {
                block()
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }
}
