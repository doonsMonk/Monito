package com.monito.expense

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.monito.expense.data.CatType
import com.monito.expense.data.Category
import com.monito.expense.data.DatabaseUpdater
import com.monito.expense.data.MonitoDatabase
import com.monito.expense.data.RecurType
import com.monito.expense.data.Repository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Runs the real SQL against SQLite (through Robolectric) to validate schema, migrations and queries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Repository.resetForTests()
        context.deleteDatabase(MonitoDatabase.DB_NAME)
        repo = Repository.get(context)
    }

    @Test
    fun freshDatabaseHasOriginalSeedData() {
        assertEquals(DatabaseUpdater.LATEST_VERSION, repo.databaseVersion())
        val all = repo.categories(includeSpecial = true)
        assertEquals(9, all.size)
        assertEquals(6, repo.categories().size)
        assertEquals(19, repo.colors().size)
        val food = repo.categoryByName("Food")!!
        assertEquals(CatType.EXPENSE, food.type)
        assertTrue(food.colorId in 2..19)
        val other = repo.categoryByName(Category.OTHER_EXPENSES)!!
        assertEquals(1, other.colorId)
        assertEquals("9E9E9E", other.colorCode)
    }

    @Test
    fun insertUpdateTrashRestoreTransaction() {
        val food = repo.categoryByName("Food")!!
        val salary = repo.categoryByName("Salary")!!
        val id = repo.insertTransaction("Lunch", BigDecimal("120.50"), food.id, LocalDate.of(2026, 11, 3)).toInt()
        repo.insertTransaction("Pay", BigDecimal("50000"), salary.id, LocalDate.of(2026, 11, 1))
        assertEquals(1, repo.categoryByName("Food")!!.count)

        val list = repo.transactions(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(2, list.size)
        assertEquals("Lunch", list[0].note) // newest date first
        assertEquals(BigDecimal("120.50"), list[0].amount)
        assertEquals(BigDecimal("120.50"), repo.totalOfType(CatType.EXPENSE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        assertEquals(BigDecimal("50000"), repo.totalOfType(CatType.INCOME, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        assertEquals(BigDecimal.ZERO, repo.totalOfType(CatType.EXPENSE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))

        repo.updateTransaction(id, "Lunch out", BigDecimal("130"), food.id, LocalDate.of(2026, 11, 4), null)
        assertEquals("Lunch out", repo.transaction(id)!!.note)
        assertEquals(1, repo.search("lunch").size)

        repo.moveToTrash(id)
        assertNull(repo.transaction(id))
        val trash = repo.trashItems()
        assertEquals(1, trash.size)
        assertEquals(LocalDate.now(), trash[0].deletedOn)
        repo.restoreFromTrash(id)
        assertNotNull(repo.transaction(id))
        assertEquals(0, repo.trashCount())

        repo.moveToTrash(id)
        repo.deleteFromTrash(id)
        assertEquals(0, repo.trashCount())
        assertEquals(1, repo.transactionCount())
    }

    @Test
    fun categoryTotalsAndDeletionFallback() {
        val food = repo.categoryByName("Food")!!
        val fuel = repo.categoryByName("Fuel")!!
        repo.insertTransaction("a", BigDecimal("10"), food.id, LocalDate.of(2026, 11, 2))
        repo.insertTransaction("b", BigDecimal("15"), food.id, LocalDate.of(2026, 11, 2))
        repo.insertTransaction("c", BigDecimal("40"), fuel.id, LocalDate.of(2026, 11, 2))
        val totals = repo.categoryTotals(CatType.EXPENSE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(listOf("Fuel", "Food"), totals.map { it.name })
        assertEquals(BigDecimal("25"), totals[1].total)

        repo.deleteCategory(food.id)
        assertNull(repo.category(food.id))
        val other = repo.categoryByName(Category.OTHER_EXPENSES)!!
        val moved = repo.transactionsOfCategory(other.id, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(2, moved.size)
    }

    @Test
    fun recurrencesCatchUpAndAdvance() {
        val salary = repo.categoryByName("Salary")!!
        val now = LocalDateTime.of(2026, 11, 11, 10, 0)
        repo.insertRecurrence("Rent", salary.id, BigDecimal("1000"), RecurType.MONTHLY, LocalDateTime.of(2026, 9, 5, 9, 0))
        val added = repo.processDueRecurrences(now)
        assertEquals(3, added) // Sep, Oct, Nov
        val r = repo.recurrences().single()
        assertEquals(LocalDateTime.of(2026, 12, 5, 9, 0), r.nextOn)
        val list = repo.transactions(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30))
        assertEquals(3, list.size)
        assertTrue(list.all { it.isAutoAdded })
        assertEquals(0, repo.processDueRecurrences(now))
    }

    @Test
    fun upgradesAnOldVersionOneDatabase() {
        // Simulate a database left by Monito 1.x: only the first schema step applied.
        val db = MonitoDatabase.get(context)
        repo.withDatabaseClosed { file ->
            file.delete()
            val old = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
            old.execSQL("CREATE TABLE meta (db_version text not null)")
            old.execSQL("insert into meta (db_version) values ('1.0')")
            old.execSQL("CREATE TABLE main (id integer not null primary key autoincrement, comment text, amount text not null default '0', date date, acc_id integer , cat_id integer)")
            old.execSQL("CREATE TABLE category (cat_id integer not null primary key autoincrement, cat_name text not null, cat_type text, cat_count integer)")
            old.execSQL("CREATE TABLE account (acc_id integer not null primary key autoincrement, acc_name text not null, acc_balance text default '0')")
            old.execSQL("insert into category (cat_name, cat_type, cat_count) values ('Other expenses','Expense', 0)")
            old.execSQL("insert into category (cat_name, cat_type, cat_count) values ('Other incomes','Income', 0)")
            old.execSQL("insert into category (cat_name, cat_type, cat_count) values ('Opening balance','Income', 0)")
            old.execSQL("insert into category (cat_name, cat_type, cat_count) values ('Groceries','Expense', 7)")
            old.execSQL("insert into category (cat_name, cat_type, cat_count) values ('Rent','Expense', 2)")
            old.execSQL("insert into main (comment, amount, date, cat_id) values ('milk', '45', '2019-05-01', 4)")
            old.close()
        }
        assertEquals(DatabaseUpdater.LATEST_VERSION, repo.databaseVersion())
        val groceries = repo.categoryByName("Groceries")!!
        assertEquals(2, groceries.colorId)
        assertTrue(groceries.isFavorite) // top category by count becomes a favourite
        val rent = repo.categoryByName("Rent")!!
        assertEquals(3, rent.colorId)
        assertEquals(19, repo.colors().size)
        assertTrue(repo.colors().any { it.code == "FFD600" })
        val t = repo.transactions(LocalDate.of(2019, 1, 1), LocalDate.of(2019, 12, 31)).single()
        assertEquals(BigDecimal("45"), t.amount)
        assertEquals("Groceries", t.catName)
        db.close()
    }
}
