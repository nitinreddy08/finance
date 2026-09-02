package com.budgetpace.app.domain.parser

class ParserCoordinator(
    private val parsers: List<BankTransactionParser> = listOf(
        KotakTransactionParser(),
        SbiTransactionParser()
    )
) {
    fun parse(input: NotificationInput): ParsedTransaction? {
        for (parser in parsers) {
            val result = parser.parse(input)
            if (result != null) return result
        }
        return null
    }
}
