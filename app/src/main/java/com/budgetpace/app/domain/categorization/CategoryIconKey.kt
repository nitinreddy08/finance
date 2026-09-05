package com.budgetpace.app.domain.categorization

/**
 * A category's iconKey is either an emoji chosen in the picker, or "default"/blank for categories
 * created before the picker existed.
 *
 * The rule lives in a pure file, not beside the Compose `CategoryIcon`, because the categorization
 * notification has to make the same call and cannot depend on Compose. Two copies would eventually
 * disagree, and the visible symptom - an emoji on the notification button but a letter avatar in
 * the app for the same category - is exactly the kind of drift spec section 4 forbids.
 */
fun isEmojiIcon(iconKey: String?): Boolean =
    !iconKey.isNullOrBlank() && iconKey != "default"
