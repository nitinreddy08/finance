package com.budgetpace.app.domain.categorization

import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.core.model.SyncState
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PromptContentTest {

    private val monthId = UUID.fromString("00000000-0000-4000-8000-0000000000ff")
    private val txId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val otherTxId = UUID.fromString("22222222-2222-4222-8222-222222222222")

    // Apple (U+1F34E) as a surrogate pair; no literal emoji lands in source.
    private val apple = "\uD83C\uDF4E"

    private fun category(
        name: String,
        sortOrder: Int,
        iconKey: String = "default",
        periodCount: Int = 4,
        active: Boolean = true
    ): Category = Category(
        id = UUID.nameUUIDFromBytes(name.toByteArray()),
        monthId = monthId,
        name = name,
        monthlyBudgetMinor = 500_000L,
        periodCount = periodCount,
        iconKey = iconKey,
        sortOrder = sortOrder,
        active = active,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun transaction(
        id: UUID = txId,
        amountMinor: Long = 35_300L,
        recipient: String? = "Zepto Marketplace",
        bank: Bank = Bank.KOTAK,
        accountSuffix: String? = "7970",
        transactionDateTime: Instant? = Instant.ofEpochMilli(1_757_000_000_000L),
        notificationReceivedAt: Instant = Instant.ofEpochMilli(1_757_000_500_000L)
    ): Transaction = Transaction(
        id = id,
        monthId = monthId,
        amountMinor = amountMinor,
        currency = "INR",
        direction = TransactionDirection.DEBIT,
        categoryId = null,
        transactionDateTime = transactionDateTime,
        transactionDate = LocalDate.of(2026, 9, 4),
        notificationReceivedAt = notificationReceivedAt,
        bank = bank,
        accountSuffix = accountSuffix,
        recipient = recipient,
        sender = null,
        referenceNumber = "123456789012",
        sourcePackage = "sms:AX-KOTAKB-S",
        sourceSender = "AX-KOTAKB-S",
        sourceMessageHash = "hash",
        duplicateKey = "key",
        recordDecision = RecordDecision.RECORDED,
        syncState = SyncState.PENDING,
        parserVersion = "1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    // --- Quick action count ---------------------------------------------------

    @Test
    fun `five categories still leave a slot for don't record`() {
        val categories = (1..5).map { category("Cat $it", sortOrder = it) }

        val content = PromptContentFactory.build(transaction(), categories)

        assertEquals(2, PromptContentFactory.MAX_QUICK_CATEGORIES)
        assertEquals(2, content.quickActions.size)
        assertEquals(listOf("Cat 1", "Cat 2"), content.quickActions.map { it.label })
    }

    @Test
    fun `one category yields one quick action`() {
        val content = PromptContentFactory.build(transaction(), listOf(category("Fruits", 1)))

        assertEquals(1, content.quickActions.size)
    }

    @Test
    fun `no categories yields no quick actions`() {
        val content = PromptContentFactory.build(transaction(), emptyList())

        assertTrue(content.quickActions.isEmpty())
    }

    @Test
    fun `inactive and lump sum categories never reach the quick actions`() {
        val rent = category("Rent", sortOrder = 0, periodCount = 1)
        val retired = category("Old Gym", sortOrder = 1, active = false)
        val fruits = category("Fruits", sortOrder = 2)
        val protein = category("Protein", sortOrder = 3)

        val ranked = QuickCategoryRanker.rank(
            categories = listOf(rent, retired, fruits, protein),
            byPayee = listOf(CategoryUsage(rent.id.toString(), 30)),
            byMonth = listOf(CategoryUsage(retired.id.toString(), 30))
        )
        val content = PromptContentFactory.build(transaction(), ranked)

        assertEquals(listOf("Fruits", "Protein"), content.quickActions.map { it.label })
    }

    // --- Labels ---------------------------------------------------------------

    @Test
    fun `an emoji icon prefixes the name instead of replacing it`() {
        val content = PromptContentFactory.build(
            transaction(),
            listOf(category("Fruits", 1, iconKey = apple))
        )

        assertEquals("$apple Fruits", content.quickActions.single().label)
    }

    @Test
    fun `default and blank icon keys fall back to the plain name`() {
        assertEquals("Fruits", PromptContentFactory.actionLabel(category("Fruits", 1, "default")))
        assertEquals("Fruits", PromptContentFactory.actionLabel(category("Fruits", 1, "")))
        assertEquals("Fruits", PromptContentFactory.actionLabel(category("Fruits", 1, "   ")))
    }

    @Test
    fun `isEmojiIcon rejects null blank and default`() {
        assertTrue(isEmojiIcon(apple))
        assertEquals(false, isEmojiIcon(null))
        assertEquals(false, isEmojiIcon(""))
        assertEquals(false, isEmojiIcon("  "))
        assertEquals(false, isEmojiIcon("default"))
    }

    // --- Title ----------------------------------------------------------------

    @Test
    fun `title names the recipient when there is one`() {
        assertEquals(
            "\u20B9353.00 to Zepto Marketplace",
            PromptContentFactory.build(transaction(), emptyList()).title
        )
    }

    @Test
    fun `title falls back to spent without a recipient`() {
        assertEquals(
            "\u20B9353.00 spent",
            PromptContentFactory.build(transaction(recipient = null), emptyList()).title
        )
        assertEquals(
            "\u20B9353.00 spent",
            PromptContentFactory.build(transaction(recipient = "   "), emptyList()).title
        )
    }

    // --- Bank line ------------------------------------------------------------

    @Test
    fun `bank names come from an explicit map`() {
        assertEquals("Kotak", PromptContentFactory.shortBankName(Bank.KOTAK))
        assertEquals("SBI", PromptContentFactory.shortBankName(Bank.SBI))
        assertEquals("", PromptContentFactory.shortBankName(Bank.UNKNOWN))
    }

    @Test
    fun `text carries bank and masked account`() {
        assertEquals(
            "What was this for? \u00B7 Kotak \u2022\u2022\u20227970",
            PromptContentFactory.build(transaction(), emptyList()).text
        )
        assertEquals(
            "What was this for? \u00B7 SBI \u2022\u2022\u20221234",
            PromptContentFactory.build(
                transaction(bank = Bank.SBI, accountSuffix = "1234"),
                emptyList()
            ).text
        )
    }

    @Test
    fun `a null account suffix drops the mask`() {
        assertEquals(
            "What was this for? \u00B7 Kotak",
            PromptContentFactory.build(transaction(accountSuffix = null), emptyList()).text
        )
    }

    @Test
    fun `an unknown bank drops the bank part and can drop the whole line`() {
        assertEquals(
            "What was this for? \u00B7 \u2022\u2022\u20227970",
            PromptContentFactory.build(transaction(bank = Bank.UNKNOWN), emptyList()).text
        )
        assertEquals(
            "What was this for?",
            PromptContentFactory.build(
                transaction(bank = Bank.UNKNOWN, accountSuffix = null),
                emptyList()
            ).text
        )
    }

    @Test
    fun `big text adds the chooser hint on a second line`() {
        val content = PromptContentFactory.build(transaction(), emptyList())

        assertEquals("${content.text}\nTap to see all categories.", content.bigText)
    }

    // --- Timestamp and identity -----------------------------------------------

    @Test
    fun `when uses the bank timestamp and falls back to arrival`() {
        assertEquals(
            1_757_000_000_000L,
            PromptContentFactory.build(transaction(), emptyList()).whenEpochMillis
        )
        assertEquals(
            1_757_000_500_000L,
            PromptContentFactory.build(
                transaction(transactionDateTime = null),
                emptyList()
            ).whenEpochMillis
        )
    }

    @Test
    fun `tag is the transaction id`() {
        assertEquals(txId.toString(), PromptContentFactory.build(transaction(), emptyList()).tag)
    }

    @Test
    fun `every action uri is unique across transactions and categories`() {
        val categories = listOf(category("Fruits", 1), category("Protein", 2))
        val first = PromptContentFactory.build(transaction(id = txId), categories)
        val second = PromptContentFactory.build(transaction(id = otherTxId), categories)

        val uris = (first.quickActions + second.quickActions).map { it.dataUri } +
            listOf(
                PromptUris.dontRecord(txId.toString()),
                PromptUris.dontRecord(otherTxId.toString()),
                PromptUris.transaction(txId.toString()),
                PromptUris.transaction(otherTxId.toString())
            )

        assertEquals(uris.size, uris.toSet().size)
        assertEquals(
            "budgetpace://categorize/$txId/${categories[0].id}",
            first.quickActions[0].dataUri
        )
        assertEquals("budgetpace://dont-record/$txId", PromptUris.dontRecord(txId.toString()))
        assertEquals("budgetpace://transaction/$txId", PromptUris.transaction(txId.toString()))
    }

    @Test
    fun `quick action carries the category id the receiver needs`() {
        val fruits = category("Fruits", 1)
        val content = PromptContentFactory.build(transaction(), listOf(fruits))

        assertEquals(fruits.id.toString(), content.quickActions.single().categoryId)
    }

    // --- Confirmation ---------------------------------------------------------

    @Test
    fun `confirmation shows whole rupees and the category`() {
        assertEquals(
            "\u2713 \u20B9353 \u2192 Fruits",
            PromptContentFactory.confirmationText(35_250L, "Fruits")
        )
        assertEquals(
            "\u2713 \u20B9353 \u2192 Fruits",
            PromptContentFactory.confirmationText(35_300L, "Fruits")
        )
    }

    @Test
    fun `confirmation says not recorded when there is no category`() {
        assertEquals(
            "\u2713 \u20B9353 not recorded",
            PromptContentFactory.confirmationText(35_250L, null)
        )
    }
}
