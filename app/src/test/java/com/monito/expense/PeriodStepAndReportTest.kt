package com.monito.expense

import com.monito.expense.data.CatType
import com.monito.expense.data.CurrencySettings
import com.monito.expense.data.DateRange
import com.monito.expense.data.ReportHtml
import com.monito.expense.data.Settings
import com.monito.expense.data.TimePeriod
import com.monito.expense.data.TimePeriods
import com.monito.expense.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class PeriodStepAndReportTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 11)

    private fun rangeOf(s: Settings) = TimePeriods.range(
        s.timePeriod, today, s.customStart, s.customEnd, LocalDate.of(2020, 1, 1), today, s.monthAnchor,
    )

    @Test
    fun arrowsWalkCalendarMonthsAndSnapBackToNamedPeriods() {
        var s = Settings(timePeriodCode = TimePeriod.THIS_MONTH.code)
        s = TimePeriods.step(s, -1, rangeOf(s), today)
        assertEquals(TimePeriod.LAST_MONTH, s.timePeriod)
        assertEquals(DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), rangeOf(s))

        s = TimePeriods.step(s, -1, rangeOf(s), today)
        assertEquals(TimePeriod.MONTH, s.timePeriod)
        assertEquals(YearMonth.of(2026, 9), s.monthAnchor)
        assertEquals(DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), rangeOf(s))

        s = TimePeriods.step(s, -1, rangeOf(s), today)
        assertEquals(YearMonth.of(2026, 8), s.monthAnchor)

        s = TimePeriods.step(s, +1, rangeOf(s), today)
        s = TimePeriods.step(s, +1, rangeOf(s), today)
        assertEquals(TimePeriod.LAST_MONTH, s.timePeriod)
        s = TimePeriods.step(s, +1, rangeOf(s), today)
        assertEquals(TimePeriod.THIS_MONTH, s.timePeriod)
        s = TimePeriods.step(s, +1, rangeOf(s), today)
        assertEquals(TimePeriod.MONTH, s.timePeriod)
        assertEquals(YearMonth.of(2026, 12), s.monthAnchor)
    }

    @Test
    fun lastThreeMonthsShiftsByThreeMonths() {
        val s = Settings(timePeriodCode = TimePeriod.LAST_3_MONTHS.code)
        val r = rangeOf(s)
        val back = TimePeriods.step(s, -1, r, today)
        assertEquals(TimePeriod.CUSTOM, back.timePeriod)
        assertEquals(LocalDate.of(2026, 5, 11), back.customStart)
        assertEquals(LocalDate.of(2026, 8, 11), back.customEnd)
    }

    @Test
    fun customWholeMonthRangeShiftsByMonths() {
        val s = Settings(timePeriodCode = TimePeriod.CUSTOM.code, customStart = LocalDate.of(2026, 10, 1), customEnd = LocalDate.of(2026, 11, 30))
        val back = TimePeriods.step(s, -1, rangeOf(s), today)
        assertEquals(LocalDate.of(2026, 8, 1), back.customStart)
        assertEquals(LocalDate.of(2026, 9, 30), back.customEnd)
        val forward = TimePeriods.step(back, +1, rangeOf(back), today)
        assertEquals(LocalDate.of(2026, 10, 1), forward.customStart)
        assertEquals(LocalDate.of(2026, 11, 30), forward.customEnd)
    }

    @Test
    fun customArbitraryRangeShiftsByItsLength() {
        val s = Settings(timePeriodCode = TimePeriod.CUSTOM.code, customStart = LocalDate.of(2026, 11, 5), customEnd = LocalDate.of(2026, 11, 14))
        val back = TimePeriods.step(s, -1, rangeOf(s), today)
        assertEquals(LocalDate.of(2026, 10, 26), back.customStart)
        assertEquals(LocalDate.of(2026, 11, 4), back.customEnd)
    }

    @Test
    fun allTimeDoesNotStep() {
        val s = Settings(timePeriodCode = TimePeriod.ALL_TIME.code)
        assertEquals(s, TimePeriods.step(s, -1, rangeOf(s), today))
        assertFalse(TimePeriods.canStep(TimePeriod.ALL_TIME))
        assertTrue(TimePeriods.canStep(TimePeriod.MONTH))
    }

    private fun tx(id: Int, date: LocalDate, amount: String, type: CatType, note: String = "") = Transaction(
        id = id, note = note, amount = BigDecimal(amount), date = date, catId = 1, catName = "Food",
        catType = type, colorCode = "EF5350", colorTextType = 1, isAutoAdded = false, imgPath = null,
    )

    @Test
    fun reportGroupsByMonthWithTotalsAndPageBreaks() {
        val list = listOf(
            tx(1, LocalDate.of(2026, 10, 3), "40", CatType.EXPENSE, "Tea <hot>"),
            tx(2, LocalDate.of(2026, 11, 3), "100", CatType.EXPENSE),
            tx(3, LocalDate.of(2026, 11, 5), "2500", CatType.INCOME),
        )
        val html = ReportHtml.build(list, DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)), CurrencySettings(), true, true, "Custom", "9.0")
        assertTrue(html.contains("<h2>October 2026</h2>"))
        assertTrue(html.contains("<h2>November 2026</h2>"))
        assertTrue(html.contains("Tea &lt;hot&gt;"))
        assertTrue(html.contains("class=\"month break\""))
        assertTrue(html.contains("Whole period"))
        assertTrue(html.contains("₹2,400.00")) // overall balance
        assertTrue(html.contains("₹2,500.00"))
    }

    @Test
    fun reportSingleListHasNoMonthSections() {
        val list = listOf(tx(1, LocalDate.of(2026, 11, 3), "100", CatType.EXPENSE))
        val html = ReportHtml.build(list, DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), CurrencySettings(), false, false, "This month", "9.0")
        assertFalse(html.contains("<h2>November 2026</h2>"))
        assertTrue(html.contains("<h2>This month</h2>"))
        assertFalse(html.contains("Whole period"))
    }
}
