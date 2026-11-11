package com.monito.expense.data

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the printable report (HTML) for a date range: one section per month, or a single list,
 * each with income / expense / balance totals. The HTML is handed to Android's print framework,
 * which renders it to paper or PDF.
 */
object ReportHtml {

    private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

    fun build(
        transactions: List<Transaction>,
        range: DateRange,
        currency: CurrencySettings,
        groupByMonth: Boolean,
        pageBreakPerMonth: Boolean,
        periodLabel: String,
        appVersion: String,
        appName: String = "Monito Expense Manager",
    ): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Monito report</title><style>")
        sb.append(CSS)
        sb.append("</style></head><body>")
        sb.append("<header><h1>").append(appName.esc()).append("</h1>")
        sb.append("<div class=\"meta\">").append(periodLabel.esc()).append(" &middot; ")
            .append(range.from.format(DAY)).append(" &ndash; ").append(range.to.format(DAY)).append("</div>")
        sb.append("<div class=\"meta small\">Printed on ").append(LocalDateTime.now().format(STAMP))
            .append(" &middot; ").append(appName.esc()).append(" ").append(appVersion.esc()).append("</div></header>")

        val sorted = transactions.sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id })
        if (groupByMonth) {
            val months = sorted.groupBy { YearMonth.from(it.date) }.toSortedMap()
            if (months.isEmpty()) {
                sb.append("<p class=\"empty\">No transactions in this period.</p>")
            }
            var first = true
            for ((ym, list) in months) {
                val cls = if (pageBreakPerMonth && !first) "month break" else "month"
                sb.append("<section class=\"").append(cls).append("\"><h2>").append(ym.format(MONTH_TITLE)).append("</h2>")
                appendTable(sb, list, currency)
                sb.append("</section>")
                first = false
            }
            if (months.size > 1) {
                sb.append("<section class=\"grand\"><h2>Whole period</h2>")
                appendTotals(sb, sorted, currency, "table")
                sb.append("</section>")
            }
        } else {
            sb.append("<section class=\"month\"><h2>").append(periodLabel.esc()).append("</h2>")
            if (sorted.isEmpty()) sb.append("<p class=\"empty\">No transactions in this period.</p>")
            else appendTable(sb, sorted, currency)
            sb.append("</section>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun appendTable(sb: StringBuilder, list: List<Transaction>, currency: CurrencySettings) {
        sb.append("<table><thead><tr><th class=\"d\">Date</th><th>Category</th><th>Note</th><th class=\"n\">Income</th><th class=\"n\">Expense</th></tr></thead><tbody>")
        for (t in list) {
            val amount = AmountFormatter.format(t.amount, currency)
            sb.append("<tr><td class=\"d\">").append(t.date.format(DAY)).append("</td>")
            sb.append("<td>").append(t.catName.esc()).append("</td>")
            sb.append("<td>").append(t.note.esc())
            if (t.isAutoAdded) sb.append(" <span class=\"auto\">(recurring)</span>")
            sb.append("</td>")
            if (t.catType == CatType.INCOME) sb.append("<td class=\"n inc\">").append(amount).append("</td><td class=\"n\"></td>")
            else sb.append("<td class=\"n\"></td><td class=\"n exp\">").append(amount).append("</td>")
            sb.append("</tr>")
        }
        sb.append("</tbody>")
        appendTotals(sb, list, currency, "tfoot")
        sb.append("</table>")
    }

    private fun appendTotals(sb: StringBuilder, list: List<Transaction>, currency: CurrencySettings, mode: String) {
        val income = list.filter { it.catType == CatType.INCOME }.fold(BigDecimal.ZERO) { a, t -> a + t.amount }
        val expense = list.filter { it.catType == CatType.EXPENSE }.fold(BigDecimal.ZERO) { a, t -> a + t.amount }
        val balance = income - expense
        val rows = "<tr class=\"total\"><td colspan=\"3\">Total income</td><td class=\"n inc\">${AmountFormatter.format(income, currency)}</td><td class=\"n\"></td></tr>" +
            "<tr class=\"total\"><td colspan=\"3\">Total expense</td><td class=\"n\"></td><td class=\"n exp\">${AmountFormatter.format(expense, currency)}</td></tr>" +
            "<tr class=\"total balance\"><td colspan=\"3\">Balance</td><td class=\"n\" colspan=\"2\">${AmountFormatter.format(balance, currency)}</td></tr>"
        if (mode == "tfoot") sb.append("<tfoot>").append(rows).append("</tfoot>")
        else sb.append("<table>").append(rows).append("</table>")
    }

    private fun String.esc(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private const val CSS = """
        body { font-family: sans-serif; font-size: 11pt; color: #1b1b1b; margin: 16px; }
        header { border-bottom: 2px solid #2e7d32; margin-bottom: 14px; padding-bottom: 6px; }
        h1 { font-size: 16pt; margin: 0 0 4px 0; color: #2e7d32; }
        h2 { font-size: 13pt; margin: 18px 0 6px 0; }
        .meta { font-size: 11pt; } .small { font-size: 9pt; color: #616161; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 4px 6px; border-bottom: 1px solid #e0e0e0; text-align: left; vertical-align: top; }
        th { background: #f1f8e9; font-size: 10pt; }
        td.d, th.d { white-space: nowrap; width: 7em; }
        .n { text-align: right; white-space: nowrap; width: 7em; }
        .inc { color: #2e7d32; } .exp { color: #c62828; }
        .auto { color: #757575; font-size: 9pt; }
        tr.total td { font-weight: bold; border-bottom: none; }
        tr.balance td { border-top: 1px solid #9e9e9e; }
        .empty { color: #616161; font-style: italic; }
        .grand { margin-top: 24px; border-top: 2px solid #2e7d32; padding-top: 6px; }
        @media print { .break { page-break-before: always; } }
    """
}
