package com.monito.expense

import com.monito.expense.data.DateRange
import com.monito.expense.data.RecurType
import com.monito.expense.data.RecurrenceSchedule
import com.monito.expense.data.Repository
import com.monito.expense.data.TimePeriod
import com.monito.expense.data.TimePeriods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TimePeriodsAndRecurrenceTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 11)

    @Test
    fun thisMonthIsWholeCalendarMonth() {
        assertEquals(DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), TimePeriods.range(TimePeriod.THIS_MONTH, today))
    }

    @Test
    fun lastMonthIsPreviousCalendarMonth() {
        assertEquals(DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), TimePeriods.range(TimePeriod.LAST_MONTH, today))
        val jan = LocalDate.of(2026, 1, 15)
        assertEquals(DateRange(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)), TimePeriods.range(TimePeriod.LAST_MONTH, jan))
    }

    @Test
    fun lastThreeAndSixMonthsEndToday() {
        assertEquals(DateRange(LocalDate.of(2026, 8, 11), today), TimePeriods.range(TimePeriod.LAST_3_MONTHS, today))
        assertEquals(DateRange(LocalDate.of(2026, 5, 11), today), TimePeriods.range(TimePeriod.LAST_6_MONTHS, today))
    }

    @Test
    fun allTimeSpansRecordedData() {
        val r = TimePeriods.range(TimePeriod.ALL_TIME, today, minDate = LocalDate.of(2019, 3, 2), maxDate = LocalDate.of(2026, 10, 1))
        assertEquals(DateRange(LocalDate.of(2019, 3, 2), today), r)
        assertEquals(DateRange(today, today), TimePeriods.range(TimePeriod.ALL_TIME, today))
    }

    @Test
    fun customSwapsReversedDates() {
        val r = TimePeriods.range(TimePeriod.CUSTOM, today, customStart = LocalDate.of(2026, 3, 10), customEnd = LocalDate.of(2026, 2, 1))
        assertEquals(DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 10)), r)
    }

    @Test
    fun recurrenceCodesMatchOriginalApp() {
        assertEquals(RecurType.DAILY, RecurType.fromCode(1))
        assertEquals(RecurType.WEEKLY, RecurType.fromCode(2))
        assertEquals(RecurType.MONTHLY, RecurType.fromCode(3))
        assertEquals(RecurType.YEARLY, RecurType.fromCode(4))
    }

    @Test
    fun advanceMovesByOnePeriod() {
        val base = LocalDateTime.of(2026, 1, 31, 9, 0)
        assertEquals(LocalDateTime.of(2026, 2, 1, 9, 0), Repository.advance(base, RecurType.DAILY))
        assertEquals(LocalDateTime.of(2026, 2, 7, 9, 0), Repository.advance(base, RecurType.WEEKLY))
        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 0), Repository.advance(base, RecurType.MONTHLY))
        assertEquals(LocalDateTime.of(2027, 1, 31, 9, 0), Repository.advance(base, RecurType.YEARLY))
    }

    @Test
    fun nextOccurrenceIsStrictlyInTheFuture() {
        val now = LocalDateTime.of(2026, 11, 11, 10, 0)
        val daily = RecurrenceSchedule.nextOccurrence(RecurType.DAILY, now.toLocalDate(), LocalTime.of(9, 0), now)
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 0), daily)
        val weekly = RecurrenceSchedule.nextOccurrence(RecurType.WEEKLY, LocalDate.of(2026, 11, 9), LocalTime.of(8, 0), now) // Monday
        assertEquals(DayOfWeek.MONDAY, weekly.dayOfWeek)
        assertTrue(weekly.isAfter(now))
        val monthly = RecurrenceSchedule.nextOccurrence(RecurType.MONTHLY, LocalDate.of(2026, 1, 31), LocalTime.of(8, 0), now)
        assertEquals(LocalDateTime.of(2026, 11, 30, 8, 0), monthly)
        val yearly = RecurrenceSchedule.nextOccurrence(RecurType.YEARLY, LocalDate.of(2024, 2, 29), LocalTime.of(8, 0), now)
        assertEquals(LocalDateTime.of(2027, 2, 28, 8, 0), yearly)
    }
}
