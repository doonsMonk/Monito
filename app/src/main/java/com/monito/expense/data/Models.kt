package com.monito.expense.data

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** Category type exactly as stored in the `category.cat_type` column of the original Monito database. */
enum class CatType(val dbValue: String) {
    EXPENSE("Expense"),
    INCOME("Income");

    companion object {
        fun fromDb(value: String?): CatType = if (value == INCOME.dbValue) INCOME else EXPENSE
    }
}

/** Recurrence type codes as stored in `recurrence.recur_type` (1..4) by the original app. */
enum class RecurType(val code: Int) {
    DAILY(1),
    WEEKLY(2),
    MONTHLY(3),
    YEARLY(4);

    companion object {
        fun fromCode(code: Int): RecurType = entries.firstOrNull { it.code == code } ?: DAILY
    }
}

/** Row of the `color` table. [code] is a hex RGB string without '#'; [textType] 0 means dark text reads better on it. */
data class ColorItem(val id: Int, val code: String, val textType: Int)

data class Category(
    val id: Int,
    val name: String,
    val type: CatType,
    val count: Int,
    val colorId: Int,
    val colorCode: String,
    val colorTextType: Int,
    val isFavorite: Boolean,
) {
    val isSpecial: Boolean get() = name in SPECIAL_CATEGORY_NAMES

    companion object {
        const val OTHER_EXPENSES = "Other expenses"
        const val OTHER_INCOMES = "Other incomes"
        const val OPENING_BALANCE = "Opening balance"
        val SPECIAL_CATEGORY_NAMES = setOf(OTHER_EXPENSES, OTHER_INCOMES, OPENING_BALANCE)
    }
}

data class Transaction(
    val id: Int,
    val note: String,
    val amount: BigDecimal,
    val date: LocalDate,
    val catId: Int,
    val catName: String,
    val catType: CatType,
    val colorCode: String,
    val colorTextType: Int,
    val isAutoAdded: Boolean,
    val imgPath: String?,
)

data class TrashItem(
    val id: Int,
    val note: String,
    val amount: BigDecimal,
    val date: LocalDate,
    val catId: Int,
    val catName: String,
    val catType: CatType,
    val deletedOn: LocalDate?,
    val isAutoAdded: Boolean,
    val imgPath: String?,
)

data class Recurrence(
    val id: Int,
    val note: String,
    val amount: BigDecimal,
    val catId: Int,
    val catName: String,
    val catType: CatType,
    val colorCode: String,
    val colorTextType: Int,
    val type: RecurType,
    /** Next date/time this recurrence fires (`recur_on`, format yyyy-MM-dd HH:mm). */
    val nextOn: LocalDateTime,
    /** Last date on which the recurrence may still fire; null means forever. */
    val endDate: LocalDate? = null,
)

data class CategoryTotal(
    val catId: Int,
    val name: String,
    val colorCode: String,
    val colorTextType: Int,
    val total: BigDecimal,
)

data class BackupFile(val name: String, val path: String, val modified: Long, val size: Long)

/** Monthly spending limit for one expense category (new in this app). */
data class Budget(
    val catId: Int,
    val catName: String,
    val colorCode: String,
    val colorTextType: Int,
    val amount: BigDecimal,
)

/** A saved quick-entry preset: tap it to prefill a new transaction. */
data class Template(
    val id: Int,
    val name: String,
    val amount: BigDecimal?,
    val catId: Int?,
    val catName: String?,
    val catType: CatType?,
    val colorCode: String?,
    val colorTextType: Int,
    val note: String,
)

/** Income and expense totals of one calendar month, for the trends screen. */
data class MonthTotal(val month: java.time.YearMonth, val income: BigDecimal, val expense: BigDecimal) {
    val balance: BigDecimal get() = income - expense
}

/** Search criteria; every field is optional and combined with AND. */
data class SearchFilter(
    val query: String = "",
    val type: CatType? = null,
    val catIds: Set<Int> = emptySet(),
    val minAmount: BigDecimal? = null,
    val maxAmount: BigDecimal? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val withPhoto: Boolean = false,
    val tag: String? = null,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && type == null && catIds.isEmpty() && minAmount == null && maxAmount == null &&
            from == null && to == null && !withPhoto && tag.isNullOrBlank()
}
