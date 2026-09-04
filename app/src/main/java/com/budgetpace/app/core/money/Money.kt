package com.budgetpace.app.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * All monetary values in Budget Pace are stored as Long in paise (minor units).
 * 100 paise = ₹1.00
 *
 * Rule §18 / §6: No Float/Double for money.
 */
object Money {

    /** Convert paise to a display rupee string like "₹1,234.50" */
    fun formatRupees(paise: Long): String {
        val negative = paise < 0
        val absPaise = if (negative) -paise else paise
        val rupees = absPaise / 100L
        val remainder = absPaise % 100L

        // Indian number system grouping: last 3 digits then groups of 2
        val rupeesStr = formatIndianNumber(rupees)
        val decimalStr = remainder.toString().padStart(2, '0')

        return "${if (negative) "-" else ""}₹$rupeesStr.$decimalStr"
    }

    /** Format as rupees without paise fraction (for large round amounts) */
    fun formatRupeesWhole(paise: Long): String {
        val negative = paise < 0
        val absPaise = if (negative) -paise else paise
        val rupees = (absPaise + 50) / 100L   // round to nearest rupee
        return "${if (negative) "-" else ""}₹${formatIndianNumber(rupees)}"
    }

    /**
     * Machine-readable amount for exports: "1234.56", never Indian-grouped. Grouping commas in a
     * CSV field shift every following column, and a grouped string in a Sheets cell is text rather
     * than a number, so both exporters share this.
     */
    fun toDecimalString(paise: Long): String = BigDecimal(paise).movePointLeft(2).toPlainString()

    /** Convert a decimal rupee string (e.g. "353.00") to paise Long */
    fun rupeesToPaise(rupeesDecimal: String): Long {
        return try {
            BigDecimal(rupeesDecimal.trim())
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .toLong()
        } catch (e: NumberFormatException) {
            0L
        }
    }

    /** Convert rupee Long to paise (whole rupees, no decimals) */
    fun rupeesToPaise(rupees: Long): Long = rupees * 100L

    private fun formatIndianNumber(n: Long): String {
        if (n == 0L) return "0"
        val s = n.toString()
        if (s.length <= 3) return s

        // Last 3 digits
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)

        // Everything before the last 3 digits groups into pairs, right to left (Indian numbering:
        // 1,00,000 not 100,000) — e.g. rest="14" -> "14", rest="123" -> "1,23", rest="1234" -> "12,34".
        // A previous version of this reversed the string, chunked it, reversed each chunk again,
        // then reversed the whole result a third time — for a 2-digit rest that net-reverses the
        // pair once too many, silently swapping its digits (14310 rendered as "41,310").
        val groups = ArrayDeque<String>()
        while (rest.length > 2) {
            groups.addFirst(rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) groups.addFirst(rest)

        return "${groups.joinToString(",")},$last3"
    }
}
