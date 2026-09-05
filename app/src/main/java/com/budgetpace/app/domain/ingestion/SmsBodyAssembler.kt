package com.budgetpace.app.domain.ingestion

/**
 * Rebuilds a multipart SMS body. The platform splits long messages at arbitrary character offsets,
 * so the parts are concatenated with no separator at all — anything else would insert characters in
 * the middle of a word or a UPI reference and break the parser regexes.
 */
object SmsBodyAssembler {

    fun join(parts: List<String?>): String? {
        val body = parts.filterNotNull().joinToString(separator = "")
        return if (body.isBlank()) null else body
    }
}
