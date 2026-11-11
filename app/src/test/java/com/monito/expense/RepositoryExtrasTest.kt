package com.monito.expense

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.monito.expense.data.CatType
import com.monito.expense.data.MonitoDatabase
import com.monito.expense.data.RecurType
import com.monito.expense.data.Repository
import com.monito.expense.data.SearchFilter
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
import java.time.YearMonth

/** Covers the tables and queries this app adds on top of the original schema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RepositoryExtrasTest {

    private lateinit var repo: Repository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        Repository.resetForTests()
        context.deleteDatabase(MonitoDatabase.DB_NAME)
        repo = Repository.get(context)
    }

    @Test
    fun extraTablesDoNotChangeTheOriginalVersionMarker() {
        assertEquals("7.0", repo.databaseVersion())
        assertTrue(repo.budgets().isEmpty())
        assertTrue(repo.templates().isEmpty())
    }

    @Test
    fun budgetsRoundTrip() {
        val food = repo.categoryByName("Food")!!
        repo.setBudget(food.id, BigDecimal("3000"))
        assertEquals(BigDecimal("3000"), repo.budgetFor(food.id))
        repo.setBudget(food.id, BigDecimal("3500"))
        assertEquals(1, repo.budgets().size)
        assertEquals(BigDecimal("3500"), repo.budgets().single().amount)
        repo.deleteBudget(food.id)
        assertNull(repo.budgetFor(food.id))
    }

    @Test
    fun templatesRoundTrip() {
        val food = repo.categoryByName("Food")!!
        val id = repo.insertTemplate("Tea", BigDecimal("20"), food.id, "morning #tea").toInt()
        val t = repo.templates().single()
        assertEquals("Tea", t.name)
        assertEquals("Food", t.catName)
        assertEquals(CatType.EXPENSE, t.catType)
        repo.updateTemplate(id, "Chai", null, null, "")
        val u = repo.templates().single()
        assertEquals("Chai", u.name)
        assertNull(u.amount)
        assertNull(u.catId)
        repo.deleteTemplate(id)
        assertTrue(repo.templates().isEmpty())
    }

    @Test
    fun filteredSearchAndTags() {
        val food = repo.categoryByName("Food")!!
        val fuel = repo.categoryByName("Fuel")!!
        val salary = repo.categoryByName("Salary")!!
        repo.insertTransaction("Lunch #office", BigDecimal("150"), food.id, LocalDate.of(2026, 11, 3))
        repo.insertTransaction("Petrol #car", BigDecimal("2000"), fuel.id, LocalDate.of(2026, 11, 4), imgPath = "/tmp/x.jpg")
        repo.insertTransaction("Pay", BigDecimal("50000"), salary.id, LocalDate.of(2026, 10, 31))

        assertEquals(1, repo.search(SearchFilter(type = CatType.INCOME)).size)
        assertEquals(2, repo.search(SearchFilter(type = CatType.EXPENSE)).size)
        assertEquals(1, repo.search(SearchFilter(catIds = setOf(food.id))).size)
        assertEquals(1, repo.search(SearchFilter(minAmount = BigDecimal("1000"), maxAmount = BigDecimal("3000"))).size)
        assertEquals(2, repo.search(SearchFilter(from = LocalDate.of(2026, 11, 1))).size)
        assertEquals(1, repo.search(SearchFilter(withPhoto = true)).size)
        assertEquals(1, repo.search(SearchFilter(tag = "car")).size)
        assertEquals(1, repo.search(SearchFilter(query = "lunch", type = CatType.EXPENSE)).size)
        assertEquals(listOf("#car", "#office"), repo.tags())
    }

    @Test
    fun duplicateCopiesEverythingExceptPhotoAndDate() {
        val food = repo.categoryByName("Food")!!
        val id = repo.insertTransaction("Lunch", BigDecimal("150"), food.id, LocalDate.of(2026, 11, 3), imgPath = "/tmp/x.jpg").toInt()
        val copyId = repo.duplicateTransaction(id, LocalDate.of(2026, 11, 11))!!.toInt()
        val copy = repo.transaction(copyId)!!
        assertEquals("Lunch", copy.note)
        assertEquals(BigDecimal("150"), copy.amount)
        assertEquals(LocalDate.of(2026, 11, 11), copy.date)
        assertNull(copy.imgPath)
        assertEquals(2, repo.categoryByName("Food")!!.count)
    }

    @Test
    fun recurrenceEndDateAndSkip() {
        val salary = repo.categoryByName("Salary")!!
        val id = repo.insertRecurrence("Rent", salary.id, BigDecimal("1000"), RecurType.MONTHLY, LocalDateTime.of(2026, 9, 5, 9, 0), LocalDate.of(2026, 10, 31)).toInt()
        val r = repo.recurrence(id)!!
        assertEquals(LocalDate.of(2026, 10, 31), r.endDate)
        val added = repo.processDueRecurrencesDetailed(LocalDateTime.of(2026, 12, 1, 0, 0))
        assertEquals(2, added.size) // Sep and Oct only, Nov is past the end date
        assertEquals(LocalDate.of(2026, 9, 5), added[0].date)
        assertNotNull(repo.recurrence(id))

        val id2 = repo.insertRecurrence("Gym", salary.id, BigDecimal("500"), RecurType.MONTHLY, LocalDateTime.of(2027, 1, 1, 8, 0)).toInt()
        repo.skipNextRecurrence(id2)
        assertEquals(LocalDateTime.of(2027, 2, 1, 8, 0), repo.recurrence(id2)!!.nextOn)
        assertEquals(3, repo.previewRecurrence(RecurType.MONTHLY, LocalDateTime.of(2027, 1, 1, 8, 0), LocalDate.of(2027, 3, 15)).size)
    }

    @Test
    fun monthlyTotalsIncludeEmptyMonths() {
        val food = repo.categoryByName("Food")!!
        val salary = repo.categoryByName("Salary")!!
        repo.insertTransaction("a", BigDecimal("100"), food.id, LocalDate.of(2026, 9, 3))
        repo.insertTransaction("b", BigDecimal("700"), salary.id, LocalDate.of(2026, 11, 3))
        val totals = repo.monthlyTotals(YearMonth.of(2026, 9), YearMonth.of(2026, 11))
        assertEquals(3, totals.size)
        assertEquals(BigDecimal("100"), totals[0].expense)
        assertEquals(BigDecimal.ZERO, totals[1].expense)
        assertEquals(BigDecimal("700"), totals[2].income)
        assertEquals(BigDecimal("700"), totals[2].balance)
        val cat = repo.categoryMonthlyTotals(food.id, YearMonth.of(2026, 9), YearMonth.of(2026, 10))
        assertEquals(BigDecimal("100"), cat[0].second)
        assertEquals(BigDecimal.ZERO, cat[1].second)
    }
}
