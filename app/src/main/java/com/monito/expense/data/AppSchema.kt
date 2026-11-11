package com.monito.expense.data

import android.database.sqlite.SQLiteDatabase

/**
 * Additions this app makes on top of the original Monito schema. Everything here is idempotent and
 * leaves `meta.db_version` untouched, so a database written by this app still opens in the old app
 * (which simply ignores the extra tables and columns).
 */
object AppSchema {

    fun ensure(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS budget (budget_id integer not null primary key autoincrement, " +
                "cat_id integer not null unique, amount text not null default '0')",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS template (template_id integer not null primary key autoincrement, " +
                "name text not null, amount text, cat_id integer, comment text, sort_order integer default 0)",
        )
        addColumnIfMissing(db, "recurrence", "recur_end", "text")
    }

    fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            var found = false
            while (c.moveToNext()) if (c.getString(1).equals(column, ignoreCase = true)) found = true
            found
        }
        if (!exists) db.execSQL("ALTER TABLE $table add column $column $definition")
    }
}
