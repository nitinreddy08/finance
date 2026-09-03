package com.budgetpace.app.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    /**
     * Regression: a real device showed "Budget ₹41,310" for a true total of ₹14,310, and later
     * "Budget ₹01,000" for a true total of ₹10,000 — in both cases the two digits directly before
     * the last-3-digit group were transposed. The old formatIndianNumber reversed the "rest"
     * string, chunked it, reversed each chunk again, then reversed the whole joined result a third
     * time; for a 2-digit "rest" that nets out to one reversal too many.
     */
    @Test
    fun testWholeRupeesWithTwoDigitThousandsGroupNotTransposed() {
        assertEquals("₹14,310", Money.formatRupeesWhole(14_310_00L))
        assertEquals("₹14,969", Money.formatRupeesWhole(14_969_00L))
        assertEquals("₹10,000", Money.formatRupeesWhole(10_000_00L))
        assertEquals("₹41,310", Money.formatRupeesWhole(41_310_00L))
    }

    @Test
    fun testWholeRupeesUnderOneThousand() {
        assertEquals("₹0", Money.formatRupeesWhole(0L))
        assertEquals("₹1", Money.formatRupeesWhole(1_00L))
        assertEquals("₹999", Money.formatRupeesWhole(999_00L))
    }

    @Test
    fun testWholeRupeesFourDigits() {
        assertEquals("₹1,000", Money.formatRupeesWhole(1_000_00L))
        assertEquals("₹9,999", Money.formatRupeesWhole(9_999_00L))
    }

    @Test
    fun testWholeRupeesLakhsAndCrores() {
        assertEquals("₹1,00,000", Money.formatRupeesWhole(1_00_000_00L))
        assertEquals("₹12,34,567", Money.formatRupeesWhole(12_34_567_00L))
        assertEquals("₹1,23,45,678", Money.formatRupeesWhole(1_23_45_678_00L))
    }

    @Test
    fun testFormatRupeesWithPaiseAndIndianGrouping() {
        assertEquals("₹14,310.00", Money.formatRupees(14_310_00L))
        assertEquals("-₹1,234.56", Money.formatRupees(-1_234_56L))
    }
}
