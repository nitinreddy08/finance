package com.budgetpace.app.domain.export

import com.budgetpace.app.core.money.Money

/**
 * One exported expense, already flattened to strings except the amount, which stays in paise so
 * only this file decides how money is rendered (spec section 6/18: money is never a Double).
 *
 * Every nullable column in the database maps to "" here rather than to null, so a row can never
 * silently shorten and shift the columns after it.
 */
data class CsvExpenseRow(
    val expenseId: String,
    val date: String,
    val time: String,
    val amountMinor: Long,
    val direction: String,
    val category: String,
    val bank: String,
    val account: String,
    val recipient: String,
    val reference: String,
    val source: String,
    val createdAt: String,
    val updatedAt: String
)

/**
 * RFC 4180 writer for the expense export.
 *
 * This exists because the previous export interpolated Indian-grouped amounts ("1,234.56")
 * straight into the line: every expense of a thousand rupees or more split into two fields and
 * shifted every following column. Amounts therefore go through [Money.toDecimalString] and every
 * field goes through [quote] - no caller ever builds a CSV line by hand.
 */
object ExpenseCsv {

    /**
     * Spec section 51 column order. The Google Sheets exporter writes the same header from this
     * list, so the two exports cannot drift apart; changing the order here changes both, and any
     * existing sheet must be re-created rather than appended to.
     */
    val EXPENSE_COLUMNS: List<String> = listOf(
        "Expense ID",
        "Date",
        "Time",
        "Amount",
        "Direction",
        "Category",
        "Bank",
        "Account",
        "Recipient",
        "Reference",
        "Source",
        "Created At",
        "Updated At"
    )

    /**
     * Excel reads a CSV as the system code page unless the file opens with a UTF-8 BOM. Written as
     * an escape on purpose: as a literal character it is invisible in every editor and diff, and
     * the first person to "clean up whitespace" here would silently delete it.
     */
    const val BOM: String = "\uFEFF"

    private const val CRLF: String = "\r\n"

    /** The four characters that force a field to be quoted; a leading/trailing space does not. */
    private const val MUST_QUOTE: String = ",\"\r\n"

    fun toFields(row: CsvExpenseRow): List<String> = listOf(
        row.expenseId,
        row.date,
        row.time,
        Money.toDecimalString(row.amountMinor),
        row.direction,
        row.category,
        row.bank,
        row.account,
        row.recipient,
        row.reference,
        row.source,
        row.createdAt,
        row.updatedAt
    )

    fun write(rows: List<CsvExpenseRow>): String = buildString { write(rows, this) }

    /**
     * Streaming form: the Android layer hands this the writer over the user-picked document so a
     * long export never materialises the whole file in memory.
     */
    fun write(rows: List<CsvExpenseRow>, out: Appendable) {
        out.append(BOM)
        writeRecord(EXPENSE_COLUMNS, out)
        for (row in rows) {
            writeRecord(toFields(row), out)
        }
    }

    /** Every record is terminated, including the last one, so appending later cannot join rows. */
    private fun writeRecord(fields: List<String>, out: Appendable) {
        for ((index, field) in fields.withIndex()) {
            if (index > 0) out.append(',')
            out.append(quote(field))
        }
        out.append(CRLF)
    }

    fun quote(field: String): String {
        if (field.none { it in MUST_QUOTE }) return field
        val escaped = field.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /** e.g. fileName(2026, 9) == "BudgetPace_2026-09.csv"; suggested to the document picker. */
    fun fileName(year: Int, month: Int): String {
        val paddedMonth = month.toString().padStart(2, '0')
        return "BudgetPace_$year-$paddedMonth.csv"
    }
}
