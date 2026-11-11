package com.monito.expense.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Opens the same `MonitoDB.sqlite` file the original Monito Expense Manager used, with the same
 * schema and version chain, so a database copied or restored from the old app works unchanged.
 */
class MonitoDatabase private constructor(private val context: Context) {

    @Volatile
    private var db: SQLiteDatabase? = null

    val file: File get() = context.getDatabasePath(DB_NAME)

    @Synchronized
    fun open(): SQLiteDatabase {
        db?.let { if (it.isOpen) return it }
        val opened = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        opened.disableWriteAheadLogging()
        DatabaseUpdater.ensureUpToDate(opened)
        AppSchema.ensure(opened)
        db = opened
        return opened
    }

    @Synchronized
    fun close() {
        db?.let { if (it.isOpen) it.close() }
        db = null
    }

    /** Close, run [block] (for example overwrite the file with a restored backup), then reopen. */
    @Synchronized
    fun <T> withClosed(block: (File) -> T): T {
        close()
        try {
            return block(file)
        } finally {
            open()
        }
    }

    companion object {
        const val DB_NAME = "MonitoDB.sqlite"

        @Volatile
        private var instance: MonitoDatabase? = null

        fun get(context: Context): MonitoDatabase =
            instance ?: synchronized(this) {
                instance ?: MonitoDatabase(context.applicationContext).also { instance = it }
            }

        /** Drops the singleton so unit tests get a fresh database per test. */
        internal fun resetForTests() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
