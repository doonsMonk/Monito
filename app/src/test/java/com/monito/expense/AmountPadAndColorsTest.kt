package com.monito.expense

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.monito.expense.data.CatType
import com.monito.expense.data.MonitoDatabase
import com.monito.expense.data.Repository
import com.monito.expense.ui.amountPadInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Calculator key handling and the category colour helpers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AmountPadAndColorsTest {

    private lateinit var repo: Repository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        Repository.resetForTests()
        context.deleteDatabase(MonitoDatabase.DB_NAME)
        repo = Repository.get(context)
    }

    private fun type(vararg keys: String, decimal: Char = '.'): String =
        keys.fold("") { expr, key -> amountPadInput(expr, key, decimal) }

    @Test
    fun digitsAndOperatorsBuildAnExpression() {
        assertEquals("120+35.5", type("1", "2", "0", "+", "3", "5", ".", "5"))
        assertEquals("800", type("8", "00"))
    }

    @Test
    fun operatorsAreNotDoubledAndCannotLead() {
        assertEquals("5×", type("5", "+", "×"))
        assertEquals("", type("+"))
        assertEquals("-", type("-", "+"))
        assertEquals("-5", type("-", "5"))
    }

    @Test
    fun onlyOneDecimalSeparatorPerNumber() {
        assertEquals("1.5", type("1", ".", "5", "."))
        assertEquals("0.", type("."))
        assertEquals("1.5+0.2", type("1", ".", "5", "+", ".", "2"))
        assertEquals("1,5", type("1", ".", "5", decimal = ','))
        assertEquals("0", type("00"))
    }

    @Test
    fun newCategoryGetsAColourNobodyUsesYet() {
        val used = repo.categories(includeSpecial = true).map { it.colorId }.toSet()
        val least = repo.leastUsedColorId()!!
        if (used.size < repo.colors().size) assertTrue(least !in used)
    }

    @Test
    fun assignDistinctColorsSeparatesCategoriesThatShareAColour() {
        val color = repo.colors().first().id
        val ids = (1..6).map { repo.insertCategory("Same colour $it", CatType.EXPENSE, color, false).toInt() }
        assertEquals(1, ids.map { repo.category(it)!!.colorId }.toSet().size)

        val changed = repo.assignDistinctColors()
        assertTrue(changed >= 5)
        val all = repo.categories(includeSpecial = true)
        if (all.size <= repo.colors().size) assertEquals(all.size, all.map { it.colorId }.toSet().size)
        assertNotEquals(1, ids.map { repo.category(it)!!.colorId }.toSet().size)
        // A second run has nothing left to do.
        assertEquals(0, repo.assignDistinctColors())
    }
}
