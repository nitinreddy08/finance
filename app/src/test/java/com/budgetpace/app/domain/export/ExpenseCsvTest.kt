package com.budgetpace.app.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCsvTest {

    private fun row(
        amountMinor: Long = 35300L,
        recipient: String = "Swiggy",
        category: String = "Food",
        reference: String = "123456789012"
    ): CsvExpenseRow = CsvExpenseRow(
        expenseId = "txn-1",
        date = "2026-09-05",
        time = "13:45",
        amountMinor = amountMinor,
        direction = "DEBIT",
        category = category,
        bank = "KOTAK",
        account = "1234",
        recipient = recipient,
        reference = reference,
        source = "SMS",
        createdAt = "2026-09-05T13:45:10Z",
        updatedAt = "2026-09-05T13:46:00Z"
    )

    private fun lines(csv: String): List<String> {
        // Records are CRLF-terminated, so the split leaves a trailing "" that is not a record.
        val parts = csv.removePrefix(ExpenseCsv.BOM).split("\r\n")
        assertEquals("last record must be CRLF-terminated", "", parts.last())
        return parts.dropLast(1)
    }

    @Test
    fun `header is the spec section 51 column list`() {
        val expected = "Expense ID,Date,Time,Amount,Direction,Category,Bank,Account," +
            "Recipient,Reference,Source,Created At,Updated At"
        assertEquals(expected, lines(ExpenseCsv.write(emptyList())).single())
        assertEquals(expected, ExpenseCsv.EXPENSE_COLUMNS.joinToString(","))
        assertEquals(13, ExpenseCsv.EXPENSE_COLUMNS.size)
    }

    @Test
    fun `output starts with the UTF-8 BOM`() {
        val csv = ExpenseCsv.write(listOf(row()))
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals('\uFEFF', csv[0])
        assertEquals("\uFEFF", ExpenseCsv.BOM)
    }

    @Test
    fun `every record ends with CRLF and no bare LF is emitted`() {
        val csv = ExpenseCsv.write(listOf(row(), row(recipient = "Zomato")))
        assertTrue(csv.endsWith("\r\n"))
        assertEquals(3, csv.split("\r\n").size - 1)
        // Header + 2 rows: every LF in the file belongs to a CRLF.
        assertEquals(3, csv.count { it == '\n' })
        assertEquals(3, csv.count { it == '\r' })
    }

    @Test
    fun `amounts are plain decimals with no grouping`() {
        val csv = ExpenseCsv.write(
            listOf(row(amountMinor = 123456789L), row(amountMinor = 5L), row(amountMinor = 100000L))
        )
        val amounts = lines(csv).drop(1).map { it.split(",")[3] }
        assertEquals(listOf("1234567.89", "0.05", "1000.00"), amounts)
        assertFalse("a grouped amount would shift every following column", csv.contains("1,234"))
        assertFalse(csv.contains("12,34,567"))
    }

    @Test
    fun `a plain field is not quoted`() {
        val fields = lines(ExpenseCsv.write(listOf(row(recipient = "Swiggy")))).last().split(",")
        assertEquals("Swiggy", fields[8])
        assertFalse(fields[8].startsWith("\""))
    }

    @Test
    fun `a field containing a comma is quoted`() {
        val record = lines(ExpenseCsv.write(listOf(row(recipient = "Swiggy, Bangalore")))).last()
        assertTrue(record.contains("\"Swiggy, Bangalore\""))
    }

    @Test
    fun `a double quote is doubled and the field is quoted`() {
        val record = lines(ExpenseCsv.write(listOf(row(recipient = "Bob's \"Cafe\"")))).last()
        assertTrue(record.contains("\"Bob's \"\"Cafe\"\"\""))
        assertEquals("\"Bob's \"\"Cafe\"\"\"", ExpenseCsv.quote("Bob's \"Cafe\""))
    }

    @Test
    fun `a field containing a newline is quoted`() {
        assertEquals("\"line1\nline2\"", ExpenseCsv.quote("line1\nline2"))
        assertEquals("\"line1\r\nline2\"", ExpenseCsv.quote("line1\r\nline2"))
        val csv = ExpenseCsv.write(listOf(row(recipient = "AMAZON\nPAY")))
        assertTrue(csv.contains("\"AMAZON\nPAY\""))
    }

    @Test
    fun `fileName zero-pads the month`() {
        assertEquals("BudgetPace_2026-09.csv", ExpenseCsv.fileName(2026, 9))
        assertEquals("BudgetPace_2026-12.csv", ExpenseCsv.fileName(2026, 12))
    }

    @Test
    fun `a recipient with a comma survives a round trip as one field`() {
        val csv = ExpenseCsv.write(listOf(row(recipient = "Swiggy, Bangalore")))
        val records = parseCsv(csv.removePrefix(ExpenseCsv.BOM))

        assertEquals(2, records.size)
        assertEquals(ExpenseCsv.EXPENSE_COLUMNS, records[0])

        val data = records[1]
        assertEquals("the whole point of this file: 13 columns, never 14", 13, data.size)
        assertEquals("Swiggy, Bangalore", data[8])
        assertEquals("353.00", data[3])
        assertEquals("Food", data[5])
        assertEquals("2026-09-05T13:46:00Z", data[12])
    }

    @Test
    fun `every escaping case survives a round trip`() {
        val nasty = "a,b \"quoted\"\r\nnext, end"
        val csv = ExpenseCsv.write(listOf(row(recipient = nasty, category = "Food, Dining")))
        val data = parseCsv(csv.removePrefix(ExpenseCsv.BOM))[1]

        assertEquals(13, data.size)
        assertEquals(nasty, data[8])
        assertEquals("Food, Dining", data[5])
    }

    /**
     * A deliberately independent RFC 4180 reader: if the writer and the reader shared code the
     * round-trip would prove nothing about what Excel or Sheets will see.
     */
    private fun parseCsv(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                }
                !inQuotes && c == '\r' && i + 1 < text.length && text[i + 1] == '\n' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                    records.add(fields)
                    fields = mutableListOf()
                    i++
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(field.toString())
            records.add(fields)
        }
        return records
    }
}
