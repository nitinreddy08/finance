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
        val rest = s.dropLast(3)

        // Remaining groups of 2
        val grouped = rest.reversed().chunked(2).joinToString(",") { it.reversed() }.reversed()
        return "$grouped,$last3"
    }
}
