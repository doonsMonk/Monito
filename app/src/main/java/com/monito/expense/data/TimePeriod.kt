package com.monito.expense.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Time period codes. 1..6 are the same numbering the original app stored in its `timePeriodCode`
 * preference; MONTH (7) is new and means "one specific calendar month" reached with the arrows.
 */
enum class TimePeriod(val code: Int) {
    THIS_MONTH(1),
    LAST_MONTH(2),
    LAST_3_MONTHS(3),
    LAST_6_MONTHS(4),
    ALL_TIME(5),
    CUSTOM(6),
    MONTH(7);

    companion object {
        fun fromCode(code: Int): TimePeriod = entries.firstOrNull { it.code == code } ?: THIS_MONTH
    }
}

data class DateRange(val from: LocalDate, val to: LocalDate) {
    operator fun contains(d: LocalDate): Boolean = !d.isBefore(from) && !d.isAfter(to)

    /** True when the range is exactly one or more whole calendar months. */
    val isWholeMonths: Boolean
        get() = from.dayOfMonth == 1 && to == YearMonth.from(to).atEndOfMonth()
}

object TimePeriods {

    /**
     * Resolves a period to an inclusive date range the way the original app did:
     * this/last month are whole calendar months, "last N months" ends today, all time spans the
     * recorded data and custom uses the stored dates. MONTH uses [month].
     */
    fun range(
        period: TimePeriod,
        today: LocalDate = LocalDate.now(),
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null,
        minDate: LocalDate? = null,
        maxDate: LocalDate? = null,
        month: YearMonth? = null,
    ): DateRange = when (period) {
        TimePeriod.THIS_MONTH -> wholeMonth(YearMonth.from(today))
        TimePeriod.LAST_MONTH -> wholeMonth(YearMonth.from(today).minusMonths(1))
        TimePeriod.MONTH -> wholeMonth(month ?: YearMonth.from(today))
        TimePeriod.LAST_3_MONTHS -> DateRange(today.minusMonths(3), today)
        TimePeriod.LAST_6_MONTHS -> DateRange(today.minusMonths(6), today)
        TimePeriod.ALL_TIME -> DateRange(minDate ?: today, maxOf(maxDate ?: today, today))
        TimePeriod.CUSTOM -> {
            val s = customStart ?: today.withDayOfMonth(1)
            val e = customEnd ?: today
            if (e.isBefore(s)) DateRange(e, s) else DateRange(s, e)
        }
    }

    fun wholeMonth(ym: YearMonth): DateRange = DateRange(ym.atDay(1), ym.atEndOfMonth())

    /** Arrows do nothing for "all time"; every other period can be stepped. */
    fun canStep(period: TimePeriod): Boolean = period != TimePeriod.ALL_TIME

    /**
     * Moves the selected period one step back ([delta] = -1) or forward (+1) and returns the new
     * settings. Monthly periods walk calendar months (snapping back to "This month" / "Last month"
     * when they land there); "last N months" and custom ranges shift by their own length.
     */
    fun step(settings: Settings, delta: Int, current: DateRange, today: LocalDate = LocalDate.now()): Settings {
        if (delta == 0) return settings
        val thisMonth = YearMonth.from(today)

        fun monthly(ym: YearMonth): Settings = when (ym) {
            thisMonth -> settings.copy(timePeriodCode = TimePeriod.THIS_MONTH.code, monthAnchor = ym)
            thisMonth.minusMonths(1) -> settings.copy(timePeriodCode = TimePeriod.LAST_MONTH.code, monthAnchor = ym)
            else -> settings.copy(timePeriodCode = TimePeriod.MONTH.code, monthAnchor = ym)
        }

        return when (settings.timePeriod) {
            TimePeriod.THIS_MONTH -> monthly(thisMonth.plusMonths(delta.toLong()))
            TimePeriod.LAST_MONTH -> monthly(thisMonth.minusMonths(1).plusMonths(delta.toLong()))
            TimePeriod.MONTH -> monthly((settings.monthAnchor ?: thisMonth).plusMonths(delta.toLong()))
            TimePeriod.ALL_TIME -> settings
            TimePeriod.LAST_3_MONTHS -> shiftByMonths(settings, current, 3L * delta)
            TimePeriod.LAST_6_MONTHS -> shiftByMonths(settings, current, 6L * delta)
            TimePeriod.CUSTOM -> {
                if (current.isWholeMonths) {
                    val months = ChronoUnit.MONTHS.between(YearMonth.from(current.from), YearMonth.from(current.to)) + 1
                    val from = YearMonth.from(current.from).plusMonths(months * delta)
                    val to = from.plusMonths(months - 1)
                    settings.copy(timePeriodCode = TimePeriod.CUSTOM.code, customStart = from.atDay(1), customEnd = to.atEndOfMonth())
                } else {
                    val days = ChronoUnit.DAYS.between(current.from, current.to) + 1
                    settings.copy(
                        timePeriodCode = TimePeriod.CUSTOM.code,
                        customStart = current.from.plusDays(days * delta),
                        customEnd = current.to.plusDays(days * delta),
                    )
                }
            }
        }
    }

    private fun shiftByMonths(settings: Settings, current: DateRange, months: Long): Settings =
        settings.copy(
            timePeriodCode = TimePeriod.CUSTOM.code,
            customStart = current.from.plusMonths(months),
            customEnd = current.to.plusMonths(months),
        )
}
