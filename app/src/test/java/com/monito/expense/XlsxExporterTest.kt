package com.monito.expense

import com.monito.expense.data.CatType
import com.monito.expense.data.Transaction
import com.monito.expense.data.XlsxExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipInputStream

class XlsxExporterTest {

    private fun tx(id: Int, date: LocalDate, amount: String, type: CatType, note: String = "") = Transaction(
        id = id, note = note, amount = BigDecimal(amount), date = date, catId = 1, catName = "Food",
        catType = type, colorCode = "EF5350", colorTextType = 1, isAutoAdded = false, imgPath = null,
    )

    private fun entries(bytes: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                out[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        return out
    }

    @Test
    fun writesOneSheetPerMonthWithTotals() {
        val list = listOf(
            tx(1, LocalDate.of(2026, 11, 3), "100", CatType.EXPENSE, "Lunch & <dinner>"),
            tx(2, LocalDate.of(2026, 11, 5), "2500.50", CatType.INCOME),
            tx(3, LocalDate.of(2026, 10, 20), "40", CatType.EXPENSE),
        )
        val bytes = ByteArrayOutputStream().also { XlsxExporter.export(it, list, "9.0") }.toByteArray()
        val files = entries(bytes)
        assertTrue(files.containsKey("[Content_Types].xml"))
        assertTrue(files.containsKey("xl/workbook.xml"))
        assertTrue(files.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(files.containsKey("xl/worksheets/sheet2.xml"))
        assertEquals(false, files.containsKey("xl/worksheets/sheet3.xml"))
        val wb = files.getValue("xl/workbook.xml")
        assertTrue(wb.contains("November 2026"))
        assertTrue(wb.contains("October 2026"))
        val nov = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(nov.contains("Lunch &amp; &lt;dinner&gt;"))
        assertTrue(nov.contains("<v>100</v>"))
        assertTrue(nov.contains("<v>2500.50</v>"))
        assertTrue(nov.contains("Total expense"))
    }

    @Test
    fun emptyExportStillProducesAWorkbook() {
        val bytes = ByteArrayOutputStream().also { XlsxExporter.export(it, emptyList(), "9.0") }.toByteArray()
        val files = entries(bytes)
        assertTrue(files.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(files.getValue("xl/workbook.xml").contains("Transactions"))
    }
}
