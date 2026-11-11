package com.monito.expense.data

import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an Excel workbook (xlsx) with one sheet per month, the same layout the original app's
 * "Export to Excel" produced: a title block, then Date / Category type / Category name / Note / Amount
 * rows followed by the month's total expense and total income. No external library needed.
 */
object XlsxExporter {

    private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.ENGLISH)

    fun export(out: OutputStream, transactions: List<Transaction>, appVersion: String, appName: String = "Monito Expense Manager") {
        val byMonth = transactions.groupBy { YearMonth.from(it.date) }.toSortedMap(compareByDescending { it })
        val sheets: List<Pair<String, String>> =
            if (byMonth.isEmpty()) listOf("Transactions" to sheetXml(emptyList(), "Transactions", appVersion, appName))
            else byMonth.map { (ym, list) ->
                val title = ym.format(MONTH_TITLE)
                title to sheetXml(list.sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id }), title, appVersion, appName)
            }

        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", contentTypes(sheets.size))
            zip.entry("_rels/.rels", RELS)
            zip.entry("xl/workbook.xml", workbook(sheets.map { it.first }))
            zip.entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, (_, xml) -> zip.entry("xl/worksheets/sheet${i + 1}.xml", xml) }
        }
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sheetXml(rows: List<Transaction>, title: String, appVersion: String, appName: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<cols><col min="1" max="1" width="14" customWidth="1"/><col min="2" max="3" width="18" customWidth="1"/><col min="4" max="4" width="40" customWidth="1"/><col min="5" max="5" width="14" customWidth="1"/></cols>""")
        sb.append("<sheetData>")
        var r = 1
        fun row(vararg cells: Cell) {
            sb.append("""<row r="$r">""")
            cells.forEachIndexed { i, c ->
                val ref = "${'A' + i}$r"
                when (c) {
                    is Cell.Text -> sb.append("""<c r="$ref" t="inlineStr"${if (c.bold) """ s="1"""" else ""}><is><t xml:space="preserve">${c.value.xml()}</t></is></c>""")
                    is Cell.Num -> sb.append("""<c r="$ref"${if (c.bold) """ s="1"""" else ""}><v>${c.value.toPlainString()}</v></c>""")
                }
            }
            sb.append("</row>")
            r++
        }
        row(Cell.Text(appName, bold = true))
        row(Cell.Text("Version $appVersion"))
        row(Cell.Text("Created on ${LocalDateTime.now().format(STAMP)}"))
        row(Cell.Text(title, bold = true))
        row()
        row(Cell.Text("Date", true), Cell.Text("Category type", true), Cell.Text("Category name", true), Cell.Text("Note", true), Cell.Text("Amount", true))
        var expense = BigDecimal.ZERO
        var income = BigDecimal.ZERO
        for (t in rows) {
            if (t.catType == CatType.EXPENSE) expense += t.amount else income += t.amount
            row(Cell.Text(t.date.format(DAY)), Cell.Text(t.catType.dbValue), Cell.Text(t.catName), Cell.Text(t.note), Cell.Num(t.amount))
        }
        row()
        row(Cell.Text(""), Cell.Text(""), Cell.Text(""), Cell.Text("Total expense", true), Cell.Num(expense, true))
        row(Cell.Text(""), Cell.Text(""), Cell.Text(""), Cell.Text("Total income", true), Cell.Num(income, true))
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private sealed class Cell {
        class Text(val value: String, val bold: Boolean = false) : Cell()
        class Num(val value: BigDecimal, val bold: Boolean = false) : Cell()
    }

    private fun String.xml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(names: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { i, n ->
            append("""<sheet name="${n.take(31).xml()}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
        """</Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        """</styleSheet>"""
}
