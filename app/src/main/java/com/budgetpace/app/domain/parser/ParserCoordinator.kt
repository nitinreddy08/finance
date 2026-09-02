package com.budgetpace.app.domain.parser

import javax.inject.Inject

class ParserCoordinator @Inject constructor() {
    private val parsers = listOf(
        KotakTransactionParser(),
        SbiTransactionParser()
    )
    
    fun parse(input: NotificationInput): ParsedTransaction? {
        for (parser in parsers) {
            if (parser.canParse(input)) {
                val result = parser.parse(input)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }
}
