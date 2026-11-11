package com.monito.expense

import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.CurrencySettings
import com.monito.expense.data.ExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ExpressionAndAmountTest {

    @Test
    fun evaluatesPlainNumbers() {
        assertEquals(BigDecimal("120"), ExpressionEvaluator.evaluate("120"))
        assertEquals(BigDecimal("35.5"), ExpressionEvaluator.evaluate("35.50"))
    }

    @Test
    fun evaluatesArithmetic() {
        assertEquals(BigDecimal("155.5"), ExpressionEvaluator.evaluate("120+35.5"))
        assertEquals(BigDecimal("14"), ExpressionEvaluator.evaluate("2+3*4"))
        assertEquals(BigDecimal("20"), ExpressionEvaluator.evaluate("(2+3)*4"))
        assertEquals(BigDecimal("2.5"), ExpressionEvaluator.evaluate("10/4"))
        assertEquals(BigDecimal("6"), ExpressionEvaluator.evaluate("12÷2"))
        assertEquals(BigDecimal("6"), ExpressionEvaluator.evaluate("3×2"))
    }

    @Test
    fun rejectsGarbageAndDivisionByZero() {
        assertNull(ExpressionEvaluator.evaluate("abc"))
        assertNull(ExpressionEvaluator.evaluate("12+"))
        assertNull(ExpressionEvaluator.evaluate("5/0"))
        assertNull(ExpressionEvaluator.evaluate(""))
    }

    @Test
    fun formatsWithDefaultIndianRupeeSettings() {
        val c = CurrencySettings()
        assertEquals("₹1,234,567.89", AmountFormatter.format(BigDecimal("1234567.89"), c))
        assertEquals("₹0.00", AmountFormatter.format(BigDecimal.ZERO, c))
        assertEquals("₹12.50", AmountFormatter.format(BigDecimal("12.5"), c))
    }

    @Test
    fun formatsWithEuropeanSeparatorsAndSymbolOnRight() {
        val c = CurrencySettings(symbol = "€", symbolOnLeft = false, decimalSeparator = ',', thousandSeparator = '.')
        assertEquals("1.234,50 €", AmountFormatter.format(BigDecimal("1234.5"), c))
        assertEquals(BigDecimal("1234.5"), AmountFormatter.parse("1.234,50", c))
    }

    @Test
    fun parsesInputWithThousandSeparatorsAndSymbol() {
        val c = CurrencySettings()
        assertEquals(BigDecimal("1234.5"), AmountFormatter.parse("₹1,234.50", c))
        assertEquals(BigDecimal("155.5"), AmountFormatter.parse("120 + 35.5", c))
        assertNull(AmountFormatter.parse("", c))
    }
}
