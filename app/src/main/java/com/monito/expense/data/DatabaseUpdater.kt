package com.monito.expense.data

import android.database.sqlite.SQLiteDatabase

/**
 * Port of the original app's `DatabaseUpdater`: the `meta.db_version` value selects which of the
 * numbered steps still have to run. The SQL is kept byte for byte so old databases end up identical.
 */
object DatabaseUpdater {

    const val LATEST_VERSION = "7.0"

    /** Known version markers in order; steps run for every marker greater than the stored version. */
    private val VERSIONS = listOf("0.0", "1.0", "2.0", "2.1", "3.0", "3.4", "3.5", "4.1", "7.0")

    private val DEFAULT_CATEGORIES = listOf(
        "Other expenses" to "Expense",
        "Other incomes" to "Income",
        "Opening balance" to "Income",
        "Food" to "Expense",
        "Entertainment" to "Expense",
        "Clothing" to "Expense",
        "Fuel" to "Expense",
        "Health" to "Expense",
        "Salary" to "Income",
    )

    private val DEFAULT_COLORS = listOf(
        "9E9E9E" to 1, "EF5350" to 1, "EC407A" to 1, "AB47BC" to 1, "7E57C2" to 1, "5C6BC0" to 1,
        "42A5F5" to 1, "29B6F6" to 1, "26C6DA" to 1, "26A69A" to 1, "66BB6A" to 1, "9CCC65" to 1,
        "D4E157" to 0, "FFEE58" to 0, "FFCA28" to 0, "FFA726" to 1, "FF7043" to 1, "8D6E63" to 1,
        "78909C" to 1,
    )

    fun ensureUpToDate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE if not exists meta (db_version text not null)")
        val current = currentVersion(db)
        if (current == null) {
            db.execSQL("insert into meta (db_version) values ('0.0')")
        }
        update(db, current ?: "0.0")
    }

    fun currentVersion(db: SQLiteDatabase): String? =
        db.rawQuery("select db_version from meta", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun isAfter(step: String, current: String): Boolean {
        val ci = VERSIONS.indexOf(current)
        val si = VERSIONS.indexOf(step)
        // Unknown stored version: run every step. Creates are "if not exists" and the ALTERs are guarded.
        return if (ci < 0) true else si > ci
    }

    fun update(db: SQLiteDatabase, current: String) {
        if (current == LATEST_VERSION) return
        db.beginTransaction()
        try {
            if (isAfter("1.0", current)) step1_0(db)
            if (isAfter("2.0", current)) db.execSQL("DROP TABLE if exists account")
            if (isAfter("2.1", current)) {
                db.execSQL("CREATE TABLE if not exists trash (trash_id integer, trash_comment text, trash_amount text, trash_date date, trash_cat_id integer, trash_deleted_on date)")
            }
            if (isAfter("3.0", current)) step3_0(db)
            if (isAfter("4.1", current)) db.execSQL("update color set color_code = 'FFD600' where color_code = 'FFEE58'")
            if (isAfter("7.0", current)) {
                db.execSQL("CREATE TABLE if not exists donation (donate_id integer not null primary key autoincrement, razorpay_id text, amount text not null default '0', donated_on text, is_success integer default 0)")
            }
            db.execSQL("update meta set db_version='$LATEST_VERSION'")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun step1_0(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE if not exists main (id integer not null primary key autoincrement, comment text, amount text not null default '0', date date, acc_id integer , cat_id integer, FOREIGN KEY(acc_id) REFERENCES account(acc_id), FOREIGN KEY(cat_id) REFERENCES category(cat_id))")
        db.execSQL("CREATE TABLE if not exists category (cat_id integer not null primary key autoincrement, cat_name text not null, cat_type text, cat_count integer)")
        db.execSQL("CREATE TABLE if not exists account (acc_id integer not null primary key autoincrement, acc_name text not null, acc_balance text default '0')")
        for ((name, type) in DEFAULT_CATEGORIES) {
            db.execSQL("insert into category (cat_name, cat_type, cat_count) values ('$name','$type', 0)")
        }
    }

    private fun step3_0(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE if not exists recurrence (recur_id integer not null primary key autoincrement, recur_amount text, recur_note text, recur_cat_id integer, recur_type integer, recur_on text)")
        db.execSQL("CREATE TABLE if not exists color (color_id integer not null primary key autoincrement, color_code text, color_text_type integer)")
        for ((code, textType) in DEFAULT_COLORS) {
            db.execSQL("INSERT into color (color_code, color_text_type) values ('$code',$textType)")
        }
        addColumnIfMissing(db, "category", "cat_color_id", "integer")
        // Assign colours 2..19 round robin to user categories, grey (1) to the special ones.
        db.rawQuery("select cat_id from category where cat_name !='Other expenses' and cat_name !='Other incomes' and cat_name !='Opening balance'", null).use { c ->
            var color = 2
            while (c.moveToNext()) {
                if (color > 19) color = 2
                db.execSQL("update category set cat_color_id=$color where cat_id=${c.getInt(0)}")
                color++
            }
        }
        db.execSQL("update category set cat_color_id = 1 where cat_name='Other expenses'")
        db.execSQL("update category set cat_color_id = 1 where cat_name='Other incomes'")
        db.execSQL("update category set cat_color_id = 1 where cat_name='Opening balance'")
        addColumnIfMissing(db, "main", "is_auto_added", "integer default 0")
        addColumnIfMissing(db, "trash", "trash_is_auto_added", "integer default 0")
        addColumnIfMissing(db, "main", "img_path", "text")
        addColumnIfMissing(db, "trash", "trash_img_path", "text")
        addColumnIfMissing(db, "category", "cat_is_fav", "integer default 0")
        db.execSQL("update category set cat_is_fav = 1 where cat_id in (select cat_id from category where cat_name !='Other expenses' and cat_name !='Other incomes' and cat_name !='Opening balance' and cat_count != 0 order by cat_count desc limit 3)")
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            var found = false
            while (c.moveToNext()) if (c.getString(1).equals(column, ignoreCase = true)) found = true
            found
        }
        if (!exists) db.execSQL("ALTER TABLE $table add column $column $definition")
    }
}
