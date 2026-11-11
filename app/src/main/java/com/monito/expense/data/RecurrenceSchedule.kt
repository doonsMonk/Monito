package com.monito.expense.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year
import java.time.YearMonth

/** Computes when a recurrence fires next, given its repeat type and the day/time the user picked. */
object RecurrenceSchedule {

    /** First occurrence strictly after [now] matching [type] with the anchor day of [anchorDate] and [time]. */
    fun nextOccurrence(type: RecurType, anchorDate: LocalDate, time: LocalTime, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var candidate = when (type) {
            RecurType.DAILY -> now.toLocalDate().atTime(time)
            RecurType.WEEKLY -> {
                var d = now.toLocalDate()
                while (d.dayOfWeek != anchorDate.dayOfWeek) d = d.plusDays(1)
                d.atTime(time)
            }
            RecurType.MONTHLY -> monthly(YearMonth.from(now), anchorDate.dayOfMonth, time)
            RecurType.YEARLY -> yearly(now.year, anchorDate, time)
        }
        var guard = 0
        while (!candidate.isAfter(now) && guard++ < 400) {
            candidate = when (type) {
                RecurType.DAILY -> candidate.plusDays(1)
                RecurType.WEEKLY -> candidate.plusWeeks(1)
                RecurType.MONTHLY -> monthly(YearMonth.from(candidate).plusMonths(1), anchorDate.dayOfMonth, time)
                RecurType.YEARLY -> yearly(candidate.year + 1, anchorDate, time)
            }
        }
        return candidate
    }

    private fun monthly(ym: YearMonth, day: Int, time: LocalTime): LocalDateTime =
        ym.atDay(minOf(day, ym.lengthOfMonth())).atTime(time)

    private fun yearly(year: Int, anchor: LocalDate, time: LocalTime): LocalDateTime {
        val day = if (anchor.monthValue == 2 && anchor.dayOfMonth == 29 && !Year.isLeap(year.toLong())) 28 else anchor.dayOfMonth
        return LocalDate.of(year, anchor.month, day).atTime(time)
    }
}
