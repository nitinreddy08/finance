# Budget Pace --- V1 Build Specification

**Purpose:** Build-ready specification for an AI coding agent.\
**Platform:** Android, Kotlin, Jetpack Compose.\
**Distribution:** Private/direct APK sharing; no Play Store dependency
for V1.\
**V1 banks:** Kotak Mahindra Bank and SBI only.\
**Source:** Bank SMS content surfaced through Google Messages
notifications.\
**Primary database:** Local Room database.\
**Cloud:** User-owned Google Sheet used for backup/export, never as the
live database.\
**Design:** Premium, restrained, sharp/minimal, editorial. Take
inspiration from the qualities of products such as Claude---typography,
spacing, restrained surfaces, subtle borders---but do not copy branding,
assets, exact layouts, or proprietary UI.

------------------------------------------------------------------------

# 1. Product in one sentence

Automatically detect supported Kotak/SBI spending notifications, ask the
user for the category in one tap, store everything locally, calculate
monthly/four-period budgets instantly, and asynchronously export the
user's data and analytics to their own Google Sheet.

------------------------------------------------------------------------

# 2. Non-negotiable product principles

1.  **Local-first.**
2.  **Current-month data never requires a Google Sheets fetch.**
3.  **The local database is the source of truth.**
4.  **Google Sheets is backup/export, not a live database.**
5.  **One normal transaction should require one categorization tap.**
6.  **No floating-point money calculations.**
7.  **No custom backend storing financial transactions in V1.**
8.  **Only Kotak and SBI bank notification formats in V1.**
9.  **Credits are not spending.**
10. **Refunds/reversals/credit-card accounting are out of scope.**
11. **Manual cash entry is supported.**
12. **Every important operation must work offline.**

------------------------------------------------------------------------

# 3. Explicitly out of scope for V1

Do NOT build:

-   credit-card tracking;
-   credit-card bill payments;
-   merchant recognition/learning;
-   Paytm/PhonePe notification parsing;
-   HDFC/ICICI/Axis/etc.;
-   refund/reversal accounting;
-   complex pending/failed transaction state machines;
-   investments;
-   subscriptions;
-   debt;
-   bank APIs;
-   UPI APIs;
-   multi-device synchronization;
-   family/shared accounts;
-   AI spending coach;
-   gamification;
-   bidirectional Google Sheets editing/import.

If a possible transaction is not wanted, the user can press **Don't
record**.

------------------------------------------------------------------------

# 4. End-to-end architecture

``` text
Kotak / SBI
    ↓
SMS
    ↓
Google Messages
    ↓
Android notification
    ↓
NotificationListenerService
    ↓
Notification extraction
    ↓
Bank parser
    ↓
Duplicate detection
    ↓
Room database
    ↓
Categorization notification
    ↓
User selects category / Don't record
    ↓
Room database
    ↓
Budget engine
    ↓
Dashboard updates immediately
    ↓
WorkManager daily sync
    ↓
User-owned Google Sheet
```

The app must never require this path for normal UI:

``` text
Dashboard → Google Sheets → download → render
```

The dashboard must instead be:

``` text
Dashboard → Room → render
```

------------------------------------------------------------------------

# 5. Recommended technology

Use:

-   Kotlin;
-   Jetpack Compose;
-   Material 3 primitives where useful, with a custom visual system;
-   Room;
-   Kotlin Coroutines;
-   Flow / StateFlow;
-   WorkManager;
-   NotificationListenerService;
-   Credential Manager for Sign in with Google;
-   current Google authorization APIs for Drive/Sheets;
-   Google Sheets API;
-   Google Drive API where required;
-   KSP;
-   Gradle Kotlin DSL.

Use current stable versions at implementation time. Do not hard-code
obsolete dependency versions from this document.

Room is preferred over direct SQLite APIs for structured local data.

------------------------------------------------------------------------

# 6. Architecture layers

``` text
UI
 ↓
ViewModel
 ↓
Use Cases
 ↓
Domain
 ↓
Repositories
 ↓
Room / Google APIs
```

Recommended project structure:

``` text
app/
  core/
    common/
    model/
    money/
    time/
    logging/
    security/

  data/
    local/
      db/
      dao/
      entity/
      mapper/
    google/
      auth/
      sheets/
      drive/
    repository/

  domain/
    parser/
      BankTransactionParser.kt
      KotakTransactionParser.kt
      SbiTransactionParser.kt
    budget/
    duplicate/
    sync/
    usecase/

  notification/
    listener/
    extractor/
    presenter/

  feature/
    onboarding/
    dashboard/
    transactions/
    categories/
    settings/
    export/

  MainActivity
```

Do not put parsing or budget logic in Compose UI.

------------------------------------------------------------------------

# 7. Authentication

Use modern Android Credential Manager for Google sign-in.

Separate:

### Authentication

Who is the user?

``` text
Credential Manager
    ↓
Google identity
```

### Authorization

Can the app create/update the user's Google Sheet?

``` text
Google authorization flow
    ↓
Sheets/Drive access
```

Do not assume that signing in with Google automatically grants
Drive/Sheets access.

Use the narrowest practical Google scopes. Prefer `drive.file` over
broad full-Drive access when the implementation supports the required
workflow.

The coding agent must verify current Google OAuth requirements from
official Google documentation before implementation.

A custom backend is not required for V1.

------------------------------------------------------------------------

# 8. Privacy architecture

The app creator should not receive the user's financial data.

Prefer:

``` text
Phone
 ├── financial data → local Room DB
 └── authorized Google API → user's own Sheet
```

Do not send these to an app-owned backend:

-   SMS content;
-   amounts;
-   bank account details;
-   UPI reference numbers;
-   recipients;
-   transaction history.

Do not log them in production either.

------------------------------------------------------------------------

# 9. Onboarding flow

``` text
Welcome
  ↓
Sign in with Google
  ↓
Authorize Google Sheets
  ↓
Create monthly budget
  ↓
Create categories
  ↓
Enable notification access
  ↓
Verify Google Messages access
  ↓
Dashboard
```

### Welcome

Copy:

> **Spend at the speed you planned.**
>
> Automatically capture supported bank transactions, categorize them in
> one tap, and see whether you're spending too fast.

Button:

> **Continue with Google**

Do not overload onboarding with explanations.

------------------------------------------------------------------------

# 10. Notification permission/access onboarding

Explain:

> Budget Pace uses Android notification access to detect supported Kotak
> and SBI bank transaction notifications shown by Google Messages.

Then:

> **Enable notification access**

Open the appropriate Android settings page.

After return:

> **Notification access enabled**

Do not claim 100% detection. NotificationListenerService receives
notification events; if the notification does not expose usable text,
the parser cannot extract it.

------------------------------------------------------------------------

# 11. NotificationListenerService

Implement a dedicated service.

Responsibilities:

1.  Receive posted notification.
2.  Check source package.
3.  Extract title/text/big text when available.
4.  Normalize text.
5.  Pass to parser coordinator.
6.  Ignore unrelated notifications.
7.  Never block the listener callback with expensive work.
8.  Persist parsed results through repository/domain code.

Important Android behavior:

-   wait for `onListenerConnected()` before listener-dependent
    operations;
-   handle `onListenerDisconnected()`;
-   do not assume notification text exists;
-   do not scrape the Google Messages UI;
-   do not read the SMS database for V1.

The supported mechanism is Android `NotificationListenerService`.

------------------------------------------------------------------------

# 12. Source filtering

V1 should process notifications from Google Messages.

Do not rely only on the visible sender name.

Use the notification package plus content patterns.

The parser should then determine whether the content is a supported
Kotak/SBI transaction.

Everything else should be ignored.

------------------------------------------------------------------------

# 13. Transaction parsing architecture

Use a normalized parser interface:

``` kotlin
interface BankTransactionParser {
    fun canParse(input: NotificationInput): Boolean
    fun parse(input: NotificationInput): ParseResult
}
```

Implement:

``` text
KotakTransactionParser
SbiTransactionParser
```

and a coordinator:

``` text
ParserCoordinator
    ↓
Kotak parser
    ↓
SBI parser
    ↓
No match
```

The parser must return a normalized domain model.

------------------------------------------------------------------------

# 14. Normalized parsed transaction

``` kotlin
data class ParsedTransaction(
    val direction: TransactionDirection,
    val amountMinor: Long,
    val bank: Bank,
    val accountSuffix: String?,
    val transactionDate: LocalDate?,
    val transactionDateTime: Instant?,
    val recipient: String?,
    val sender: String?,
    val referenceNumber: String?,
    val confidence: ParseConfidence
)
```

Enums:

``` kotlin
enum class TransactionDirection {
    DEBIT,
    CREDIT
}

enum class Bank {
    KOTAK,
    SBI,
    UNKNOWN
}

enum class ParseConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

Only high-confidence expenses should automatically become normal
categorization prompts.

------------------------------------------------------------------------

# 15. Kotak parser

Observed debit example:

``` text
Sent Rs.27.00 from Kotak Bank AC X7970 to paytm.s2ebzrr@pty on 06-08-26.UPI Ref 621859049153. Not you, https://kotak.com/KBANKT/Fraud
```

Expected:

``` text
direction       DEBIT
amount          2700 paise
bank             KOTAK
accountSuffix    X7970
recipient        paytm.s2ebzrr@pty
date             2026-08-06
reference        621859049153
```

The parser must not accidentally extract numbers from:

-   URLs;
-   fraud warnings;
-   phone numbers;
-   unrelated text.

Observed credit example:

``` text
Received Rs.6000.00 in your Kotak Bank AC X7970 from nitinreddy@ptyes on 06-08-26.UPI Ref:212542994030.
```

Expected:

``` text
direction       CREDIT
amount          600000 paise
bank             KOTAK
accountSuffix    X7970
sender           nitinreddy@ptyes
date             2026-08-06
reference        212542994030
```

Credits must not enter spending totals or category budgets.

------------------------------------------------------------------------

# 16. SBI parser

Observed example:

``` text
Dear UPI user A/C X5326 debited by 272.00 on date 26Jul26 trf to Zepto Marketplace Refno 211674921516 If not u? call-1800111109 for other services-18001234-SBI
```

Expected:

``` text
direction       DEBIT
amount          27200 paise
bank             SBI
accountSuffix    X5326
date             2026-07-26
recipient        Zepto Marketplace
reference        211674921516
```

Another observed example:

``` text
Dear UPI user A/C X5326 debited by 30.00 on date 24Aug26 trf to DHRMPAL Refno 623624303161 If not u? call-1800111109 for other services-18001234-SBI
```

Expected:

``` text
direction       DEBIT
amount          3000 paise
bank             SBI
accountSuffix    X5326
date             2026-08-24
recipient        DHRMPAL
reference        623624303161
```

The parser must stop the recipient extraction before the standard
warning/ref text.

------------------------------------------------------------------------

# 17. Transaction date rule

Use the transaction date/time supplied by the bank message.

Do NOT use notification arrival time as the transaction date.

Store both when possible:

``` text
transactionDateTime
notificationReceivedAt
```

If the bank gives only a date, keep transaction time null.

A transaction received at 00:02 on October 1 but dated September 30
belongs to September.

The month is determined from the transaction date.

------------------------------------------------------------------------

# 18. Transaction data model

Use integer paise, never Float/Double.

``` kotlin
Transaction(
    id: UUID,
    monthId: UUID,
    amountMinor: Long,
    currency: String,              // INR in V1
    direction: TransactionDirection,
    categoryId: UUID?,
    transactionDateTime: Instant?,
    transactionDate: LocalDate,
    notificationReceivedAt: Instant,
    bank: Bank,
    accountSuffix: String?,
    recipient: String?,
    sender: String?,
    referenceNumber: String?,
    sourcePackage: String?,
    sourceSender: String?,
    sourceMessageHash: String?,
    duplicateKey: String?,
    recordDecision: RecordDecision,
    syncState: SyncState,
    parserVersion: String?,
    createdAt: Instant,
    updatedAt: Instant
)
```

Enums:

``` kotlin
enum class RecordDecision {
    RECORDED,
    IGNORED
}

enum class SyncState {
    PENDING,
    SYNCED,
    FAILED
}
```

------------------------------------------------------------------------

# 19. Duplicate detection

Use two levels.

## Primary: bank reference

If a reference exists:

``` text
bank + referenceNumber
```

is the primary identity.

Examples:

``` text
KOTAK:621859049153
SBI:211674921516
```

Add an appropriate database uniqueness constraint.

## Fallback: fingerprint

If no reference exists:

``` text
bank
+ accountSuffix
+ amountMinor
+ direction
+ transactionDate
+ normalized recipient/sender
```

Optionally include a normalized message hash.

Use a reasonable time window for fallback duplicate matching.

Deduplicate BEFORE displaying the category prompt.

If Android delivers the same notification twice, only one transaction
should exist.

User categorization actions must also be idempotent.

------------------------------------------------------------------------

# 20. Failed/reversed transactions

Do not build a complicated state machine.

If the user sees something they do not want recorded:

``` text
[ Record ]
[ Don't record ]
```

Choosing **Don't record** sets:

``` text
recordDecision = IGNORED
```

The ignored reference/message must not repeatedly prompt the user.

Refund/reversal accounting is intentionally out of scope.

------------------------------------------------------------------------

# 21. Categorization notification

This is the most important UI.

Example:

``` text
┌──────────────────────────────────┐
│  Transaction detected             │
│                                  │
│  ₹353.00 spent                   │
│  via UPI                         │
│                                  │
│  What was this for?              │
│                                  │
│  [ Fruits ]   [ Protein ]        │
│  [ Misc ]     [ Rent ]           │
│                                  │
│  [ Other ]    [ Don't record ]   │
└──────────────────────────────────┘
```

Do not require opening the app.

After action:

``` text
✓ ₹353 → Fruits
```

The dashboard updates immediately.

------------------------------------------------------------------------

# 22. Manual cash transaction

Provide:

``` text
+ Add
```

Form:

``` text
Amount
₹ ______

Category
[ Select ]

Date
[ Today ]

Note (optional)
[ __________ ]

[ Add transaction ]
```

Do not add unnecessary fields.

------------------------------------------------------------------------

# 23. Categories

Categories are fully user-defined.

Example user setup:

``` text
Fruits                  ₹1,000
Eggs + Protein + Curd   ₹3,000
Extra Food + Misc       ₹1,500
Rent                    ₹9,000
```

Another user can create:

``` text
Groceries
Fuel
Entertainment
Rent
Travel
```

Never hard-code the user's categories.

Each category should have:

``` text
id
name
monthlyBudgetMinor
iconKey
weeklyPacingEnabled
sortOrder
active
createdAt
updatedAt
```

------------------------------------------------------------------------

# 24. Rent and weekly pacing

Rent should normally have:

``` text
weeklyPacingEnabled = false
```

It participates in:

``` text
total monthly budget
total monthly spending
overall four-period pace
```

but does not need its own four-period category pacing.

Other categories can use four-period pacing.

------------------------------------------------------------------------

# 25. Monthly budget

Calculate total budget from categories:

``` text
totalMonthlyBudget =
Σ category.monthlyBudget
```

Example:

``` text
Rent                    ₹9,000
Eggs + Protein + Curd   ₹3,000
Fruits                  ₹1,000
Extra Food + Misc       ₹1,500
-------------------------------
Total                  ₹14,500
```

Do not store the total as an independent authoritative value.

------------------------------------------------------------------------

# 26. Four-period model

The UI always displays four equal-width blocks.

The underlying periods should be as mathematically fair as possible
based on actual days in the month.

Examples:

28 days:

``` text
7 / 7 / 7 / 7
```

30 days:

``` text
8 / 7 / 8 / 7
```

31 days:

``` text
8 / 8 / 8 / 7
```

The exact distribution should be deterministic and documented in code.

Do not use literal calendar weeks.

------------------------------------------------------------------------

# 27. Period budget formula

For monthly category budget `M`:

``` text
periodBudget = M × periodDays / daysInMonth
```

Use integer paise.

The sum of all four period budgets MUST equal exactly the monthly
budget.

If rounding creates a remainder, distribute the remainder
deterministically among periods.

------------------------------------------------------------------------

# 28. Carry-forward

Unused budget can be carried to a later period.

It does NOT have to move automatically into the immediate next period.

Example:

``` text
Period 1 budget = ₹250
Spent           = ₹180
Unused          = ₹70
```

₹70 becomes a carry-forward reserve.

The user can apply it to Period 2, 3, or 4.

Do not mutate the historical base budget.

Use an explicit allocation record:

``` kotlin
BudgetCarryForward(
    id,
    monthId,
    categoryId,
    sourcePeriod,
    targetPeriod,
    amountMinor,
    createdAt
)
```

------------------------------------------------------------------------

# 29. Period status colors

The user wants:

-   green = under budget;
-   orange = slightly over;
-   red = significantly over.

Do not use static rupee thresholds.

For a completed period:

``` text
ratio = spent / effectiveBudget
```

Use:

``` text
GREEN:
ratio <= 1.00

ORANGE:
1.00 < ratio <= 1.20

RED:
ratio > 1.20
```

Examples:

``` text
₹250 budget:
₹240 → green
₹275 → orange
₹310 → red

₹1,000 budget:
₹950 → green
₹1,150 → orange
₹1,250 → red
```

This scales with the budget.

------------------------------------------------------------------------

# 30. Current-period pace

A partially completed period must not be judged against the full period
budget.

Calculate:

``` text
elapsedFraction =
elapsedDays / totalPeriodDays

expectedBudgetToDate =
effectivePeriodBudget × elapsedFraction

paceRatio =
spentToDate / expectedBudgetToDate
```

Use the same percentage thresholds.

This means the UI answers:

> "Am I spending too fast?"

rather than merely:

> "Have I exceeded the final period budget yet?"

If expected budget is zero, handle explicitly.

------------------------------------------------------------------------

# 31. Overall monthly pace

Calculate the same concept for the total budget.

Overall spending includes rent.

Category pacing for rent can be disabled.

The top-level four blocks should communicate overall monthly spending
speed.

------------------------------------------------------------------------

# 32. Safe-to-spend

Show a prominent number:

``` text
SAFE TO SPEND THIS PERIOD
₹327
```

Calculate using:

-   current date;
-   current period;
-   remaining current-period allocation;
-   future period budgets;
-   valid carry-forward allocations;
-   total monthly remaining budget.

If the user is over budget:

``` text
Safe to spend: ₹0
```

and show the overage separately.

Do not show a negative number as "safe to spend."

------------------------------------------------------------------------

# 33. Dashboard layout

``` text
┌─────────────────────────────────────┐
│ September 2026                  ⋮   │
│                                     │
│ ₹11,183                             │
│ remaining                           │
│                                     │
│ SAFE TO SPEND THIS PERIOD           │
│ ₹327                                │
│                                     │
│ OVERALL PACE                        │
│                                     │
│ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐
│ │ WEEK1 │ │ WEEK2 │ │ WEEK3 │ │ WEEK4 │
│ │ ₹...  │ │ ₹...  │ │  —    │ │  —    │
│ │ BLUE  │ │ORANGE  │ │ GREY  │ │ GREY  │
│ └───────┘ └───────┘ └───────┘ └───────┘
│                                     │
│ CATEGORIES                          │
│                                     │
│ Fruits              ₹593 / ₹1,000  │
│ ┌────┐ ┌────┐ ┌────┐ ┌────┐        │
│ │ W1 │ │ W2 │ │ W3 │ │ W4 │        │
│ │180 │ │320 │ │ —  │ │ —  │        │
│ │ 🟦 │ │ 🟧 │ │ ▫  │ │ ▫  │        │
│ └────┘ └────┘ └────┘ └────┘        │
│ ₹407 remaining                       │
│                                     │
│ Protein + Curd      ₹1,921 / ₹3,000│
│ ...                                 │
│                                     │
│ Rent                ₹0 / ₹9,000    │
│ Monthly                             │
│                                     │
│ + Add          Transactions         │
└─────────────────────────────────────┘
```

Do not literally render emoji/color squares in production. Use the
design-system status colors and icons.

------------------------------------------------------------------------

# 34. Design system

## Visual personality

Target:

-   premium;
-   quiet;
-   intelligent;
-   trustworthy;
-   editorial;
-   slightly sharp;
-   low decoration;
-   strong typography;
-   restrained color.

Avoid:

-   excessive rounded cards;
-   giant pills;
-   heavy shadows;
-   gradients;
-   glassmorphism;
-   childish gamification;
-   excessive illustrations.

Use the design qualities of modern minimalist products as inspiration,
not imitation.

------------------------------------------------------------------------

# 35. Shape tokens

Recommended:

``` text
Small controls: 8dp
Buttons: 8–10dp
Cards: 12dp
Large containers: 14–16dp
Inputs: 8–10dp
```

Do not use 28--32dp radii everywhere.

The interface should have slightly sharp corners.

------------------------------------------------------------------------

# 36. Spacing tokens

Use an 8dp grid:

``` text
4
8
12
16
20
24
32
40
48
```

Default screen horizontal padding:

``` text
16dp
```

Card padding:

``` text
16dp
```

------------------------------------------------------------------------

# 37. Typography

Use Android system sans or Inter if licensing/build policy permits.

Suggested:

``` text
Large balance:
32–40sp / Medium or Semibold

Screen title:
22–24sp / Semibold

Section title:
14–16sp / Medium

Body:
14–16sp / Regular

Metadata:
12–13sp
```

Typography should do most of the visual work.

------------------------------------------------------------------------

# 38. Color tokens

Light:

``` text
Background  #F7F6F2
Surface     #FCFBF8
Text        #1D1D1B
Secondary   #686762
Border      #D9D7D0
```

Dark:

``` text
Background  #171817
Surface     #1D1E1C
Text        #F1F0EB
Secondary   #A6A49D
Border      #343530
```

Semantic:

``` text
Blue        #3B82F6
Orange      #E98A15
Red         #D64545
Green       #3D8B5F
```

Do not make the entire UI colorful. Use status colors semantically.

------------------------------------------------------------------------

# 39. Status must never rely on color alone

For accessibility:

``` text
GREEN  → ON TRACK
ORANGE → SLIGHTLY OVER
RED    → OVER BUDGET
GREY   → UPCOMING
```

Use icon/text plus color.

------------------------------------------------------------------------

# 40. Cards and surfaces

Prefer:

``` text
thin border
+
subtle surface contrast
+
whitespace
```

over heavy shadows.

The dashboard should feel like one continuous canvas with structured
sections.

------------------------------------------------------------------------

# 41. Four-period tile design

Each tile:

``` text
WEEK 2
₹320 / ₹250

● OVER 28%
```

The four tiles must have equal widths.

Use a subtle background/border state.

Future period:

``` text
WEEK 3
—
UPCOMING
```

Current:

``` text
WEEK 2
CURRENT
ON TRACK
```

------------------------------------------------------------------------

# 42. Transactions screen

Use compact rows.

``` text
TODAY

₹353
Fruits
Kotak •••7970
10:42 AM

₹120
Misc
SBI •••5326
09:18 AM
```

Amount is primary.

Bank/account is secondary.

Do not display raw SMS text by default.

------------------------------------------------------------------------

# 43. Transaction detail

``` text
₹353.00

Fruits

02 Sep 2026
10:42 AM

Kotak Mahindra Bank
Account •••7970

UPI
paytm.s2ebzrr@...

Reference
621859049153

[ Change category ]
[ Delete ]
```

------------------------------------------------------------------------

# 44. Category editing

Changing a transaction:

``` text
Misc → Fruits
```

must immediately recalculate:

-   old category;
-   new category;
-   total;
-   period spending;
-   status;
-   safe-to-spend;
-   analytics;
-   sync state.

Do not store lots of derived totals as independent authoritative data.

------------------------------------------------------------------------

# 45. Category deletion

Never silently delete transactions.

If deleting a category with transactions:

``` text
Delete "Fruits"?

12 transactions use this category.

[ Move all to another category ]
[ Select transactions ]
[ Cancel ]
```

For selection:

``` text
[ Select all ]

☑ ₹353   Sep 02
☑ ₹120   Sep 02
☐ ₹240   Sep 03

Move selected to:
[ Misc ▼ ]

[ Move ]
```

------------------------------------------------------------------------

# 46. Monthly data model

``` kotlin
BudgetMonth(
    id,
    year,
    month,
    status,          // ACTIVE / ARCHIVED
    createdAt,
    archivedAt
)
```

Unique month per local user:

``` text
year + month
```

The month of a transaction is determined by transaction date.

------------------------------------------------------------------------

# 47. Category data model

``` kotlin
Category(
    id,
    monthId,
    name,
    monthlyBudgetMinor,
    weeklyPacingEnabled,
    iconKey,
    sortOrder,
    active,
    createdAt,
    updatedAt
)
```

Categories can change month-to-month.

Optional onboarding convenience:

> Use last month's categories

This copies configuration, not transactions.

------------------------------------------------------------------------

# 48. Local database indexes

Index:

``` text
Transaction.referenceNumber
Transaction.transactionDate
Transaction.monthId
Transaction.categoryId
Transaction.syncState
```

Use a suitable uniqueness constraint for:

``` text
bank + referenceNumber
```

Do not make a bare reference number globally unique.

------------------------------------------------------------------------

# 49. Repository design

Example:

``` kotlin
interface TransactionRepository {
    suspend fun add(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: UUID)
    fun observeMonth(monthId: UUID): Flow<List<Transaction>>
}
```

Budget:

``` kotlin
interface BudgetRepository {
    fun observeMonthSummary(monthId: UUID): Flow<MonthSummary>
}
```

Cloud:

``` kotlin
interface CloudSyncRepository {
    suspend fun ensureWorkbook(): Result<Workbook>
    suspend fun syncPendingChanges(): Result<SyncResult>
    suspend fun exportMonth(monthId: UUID): Result<SyncResult>
}
```

------------------------------------------------------------------------

# 50. Room/source-of-truth rule

Room is authoritative.

The UI reads Room.

Budget calculations use Room/domain data.

Google Sheets never becomes a required read path for the dashboard.

------------------------------------------------------------------------

# 51. Google Sheets workbook

On first authorized export, create a workbook owned by the user.

Suggested tabs:

``` text
Dashboard
Transactions
Analytics
Categories
```

## Transactions columns

``` text
Transaction ID
Date
Time
Amount
Direction
Category
Bank
Account
Recipient
Reference
Source
Created At
Updated At
```

## Categories

``` text
Category ID
Name
Monthly Budget
Weekly Pacing
Active
Created At
Updated At
```

## Analytics

Include:

``` text
Monthly budget
Total spent
Remaining
Average daily spend

Category
Budget
Spent
Remaining
Percentage used

Period 1
Period 2
Period 3
Period 4

Category-period breakdown
```

## Dashboard

Human-readable summary:

``` text
September 2026
Total budget
Spent
Remaining
Safe to spend
Overall pace
Category summaries
Period summaries
```

------------------------------------------------------------------------

# 52. Google Sheets sync

Daily sync should upload changes, not blindly export the entire
database.

``` text
PENDING transactions
     ↓
Find stable transaction UUID in Sheet
     ↓
If absent → append
If present → update
     ↓
Mark SYNCED
```

Use stable local UUIDs.

If a transaction is edited:

``` text
SYNCED → PENDING → update Sheet → SYNCED
```

If deleted, use a tombstone or equivalent reliable deletion mechanism so
the row is not recreated.

------------------------------------------------------------------------

# 53. Offline sync

If offline:

``` text
Transaction
  ↓
Room ✓
  ↓
Sync = PENDING
```

When connectivity returns:

``` text
WorkManager
  ↓
sync
  ↓
Sheet updated
  ↓
SYNCED
```

The user should never lose a transaction because the internet is
unavailable.

------------------------------------------------------------------------

# 54. WorkManager

Use WorkManager for daily/deferred synchronization.

Work should be:

-   network-aware;
-   retryable;
-   idempotent.

Do not retry forever when authorization is missing.

Show:

``` text
Google Sheets
Connected

Last backup:
Today, 02:14

3 changes waiting

[ Sync now ]
```

------------------------------------------------------------------------

# 55. Manual export

Settings:

``` text
Export & Backup

Current month
[ Export to Google Sheets ]

Previous months
[ Export ]

CSV
[ Export CSV ]
```

CSV must work independently of Google authorization.

------------------------------------------------------------------------

# 56. Current-month performance

Opening the app:

``` text
Launch
 ↓
Room
 ↓
Current month
 ↓
Dashboard
```

Do not:

``` text
Launch
 ↓
Google API
 ↓
download
 ↓
parse
 ↓
dashboard
```

Google sync can run after the UI is already usable.

------------------------------------------------------------------------

# 57. Month rollover

At the first suitable app/background execution after a calendar month
changes:

``` text
Old month
 ↓
ARCHIVED
 ↓
Queue final sync/export
 ↓
Create new ACTIVE month
```

The old month remains locally accessible.

If offline:

``` text
old month = ARCHIVED
sync = PENDING
```

October must work normally even if September has not yet synced.

------------------------------------------------------------------------

# 58. Previous month retention

Keep archived local months.

Current month is the hot dataset.

Older months can remain local for normal personal usage.

Do not force network fetching merely to view history.

------------------------------------------------------------------------

# 59. Google account switching

Settings:

``` text
Google account
[ Change ]
```

Before switching:

``` text
Pending changes exist.

[ Export & switch ]
[ Switch without export ]
[ Cancel ]
```

Never silently attach local data to a different Google Sheet.

------------------------------------------------------------------------

# 60. Google Sheets conflict model

V1 is **one-way authoritative export**:

``` text
Local DB → Google Sheet
```

If the user manually edits the Sheet:

> The app does not import that change in V1.

The next export can restore the local authoritative value.

Clearly communicate that Sheets is:

> backup/export

not:

> editable live database.

------------------------------------------------------------------------

# 61. Error UX

Never expose raw technical errors.

Bad:

``` text
HTTP 403
SQLiteConstraintException
NullPointerException
```

Good:

``` text
Couldn't sync with Google Sheets.

Your local data is safe.

[ Try again ]
```

------------------------------------------------------------------------

# 62. Empty states

No transactions:

> No transactions yet.
>
> Supported bank transactions will appear here automatically. You can
> also add cash spending manually.

No categories:

> Create your first spending category.

Google disconnected:

> Google Sheets isn't connected.
>
> Your app still works normally.

------------------------------------------------------------------------

# 63. Accessibility

Required:

-   TalkBack labels;
-   semantic descriptions;
-   minimum touch targets;
-   dynamic font scaling;
-   strong contrast;
-   color + text/icon status;
-   no information conveyed only through color.

------------------------------------------------------------------------

# 64. Animation

Keep motion restrained.

Use roughly 150--250ms for:

-   adding a transaction;
-   changing category;
-   status change;
-   navigation transitions.

Avoid:

-   bouncing;
-   excessive spring animation;
-   decorative animation.

The app should feel fast.

------------------------------------------------------------------------

# 65. Security

Never log:

-   OAuth access tokens;
-   ID tokens;
-   full SMS;
-   full account number;
-   full UPI IDs unnecessarily;
-   transaction references unnecessarily.

Debug parser lab may show sensitive values only in debug builds.

Release logs must be redacted or disabled.

Do not invent cryptography. If encryption is needed later, use
established Android-supported mechanisms.

------------------------------------------------------------------------

# 66. Parser diagnostic screen --- debug only

Provide:

``` text
Parser Lab

Paste notification text:

[________________________]

Detected:
Bank
Direction
Amount
Date
Reference
Account
Recipient
Confidence

[ Parse ]
```

This is important for quickly adding/tuning real bank formats.

Do not expose in release UI.

------------------------------------------------------------------------

# 67. Parser tests

Mandatory unit tests:

### Kotak

-   debit;
-   credit;
-   UPI reference;
-   decimal amount;
-   account suffix;
-   URL after transaction;
-   recipient UPI ID.

### SBI

-   debit;
-   `trf to`;
-   `Refno`;
-   merchant with spaces;
-   account suffix;
-   date `26Jul26`;
-   warning phone numbers after transaction.

### General

-   duplicate notification;
-   whitespace changes;
-   punctuation changes;
-   missing optional fields;
-   unrelated SMS containing numbers;
-   malformed SMS;
-   credit not recorded as spending.

------------------------------------------------------------------------

# 68. Budget tests

Mandatory tests:

-   28-day month;
-   30-day month;
-   31-day month;
-   exact period-budget sum;
-   rounding;
-   green at 100%;
-   orange just above 100%;
-   red above 120%;
-   current-period pace;
-   carry-forward to later period;
-   category move;
-   category deletion;
-   overspending;
-   safe-to-spend;
-   month rollover.

------------------------------------------------------------------------

# 69. Critical acceptance tests

### A. Kotak

Input:

``` text
Sent Rs.27.00 from Kotak Bank AC X7970 to paytm.s2ebzrr@pty on 06-08-26.UPI Ref 621859049153.
```

Expected:

``` text
DEBIT
₹27
Kotak
X7970
2026-08-06
621859049153
```

Exactly one categorization prompt.

### B. Same notification twice

Expected:

``` text
One transaction.
One prompt.
```

### C. SBI

Input:

``` text
Dear UPI user A/C X5326 debited by 272.00 on date 26Jul26 trf to Zepto Marketplace Refno 211674921516 ...
```

Expected:

``` text
₹272
SBI
X5326
2026-07-26
Zepto Marketplace
211674921516
```

### D. Kotak credit

Expected:

``` text
No spending category prompt.
No expense budget change.
```

### E. Don't record

Expected:

``` text
No budget change.
No repeated prompt for same transaction.
```

### F. Offline

Expected:

``` text
Transaction saves.
Dashboard updates.
Sync waits.
```

### G. Category change

Expected:

``` text
Old category decreases.
New category increases.
All summaries recalculate.
```

### H. Month change

Expected:

``` text
Old month archived.
New month active.
Old transactions remain in old month.
```

------------------------------------------------------------------------

# 70. Performance

Targets:

-   dashboard from local data should feel immediate;
-   transaction save should not wait for network;
-   no Google Sheet fetch on normal dashboard launch;
-   notification parsing should be lightweight;
-   no full-database recalculation on every Compose recomposition.

Use Room Flow and derived domain calculations appropriately.

------------------------------------------------------------------------

# 71. Navigation

Keep only:

``` text
Home
Transactions
Settings
```

Possible routes:

``` text
/onboarding
/home
/transactions
/transactions/{id}
/categories
/categories/{id}
/settings
/settings/google
/settings/export
/settings/notification-access
```

Do not build a large navigation hierarchy.

------------------------------------------------------------------------

# 72. Settings

Recommended:

``` text
ACCOUNT
Google account

TRANSACTION DETECTION
Notification access
Supported banks
Detection enabled

BUDGET
Categories
Monthly budget
Carry-forward

GOOGLE SHEETS
Connected sheet
Last sync
Sync now
Automatic daily export

DATA
Export CSV
Delete local data

ABOUT
Version
Privacy
```

------------------------------------------------------------------------

# 73. Data deletion

If deleting local data:

``` text
This removes transactions and budgets
stored on this phone.

Your Google Sheet will not be deleted.

[ Cancel ]
[ Delete local data ]
```

Never delete the user's Google Sheet automatically.

------------------------------------------------------------------------

# 74. UI quality bar

The app should feel like a premium productivity/finance tool, not a
generic template.

Prioritize:

1.  typography;
2.  spacing;
3.  alignment;
4.  information hierarchy;
5.  subtle borders;
6.  semantic status color;
7.  restrained motion.

Do NOT prioritize:

-   gradients;
-   glass effects;
-   huge shadows;
-   giant rounded cards;
-   decorative illustrations.

------------------------------------------------------------------------

# 75. Implementation phases

## Phase 1 --- Foundation

Build:

-   project;
-   Compose;
-   theme;
-   Room;
-   Month;
-   Category;
-   Transaction;
-   BudgetPeriod;
-   CarryForward;
-   repositories;
-   budget engine;
-   dashboard with fake/local data;
-   tests.

Do NOT implement Google or notification parsing yet.

## Phase 2 --- UI

Build:

-   onboarding;
-   dashboard;
-   categories;
-   transactions;
-   detail;
-   manual add;
-   settings.

## Phase 3 --- Notifications

Build:

-   NotificationListenerService;
-   notification extractor;
-   parser coordinator;
-   Kotak parser;
-   SBI parser;
-   duplicate detector;
-   categorization notification;
-   Don't record.

## Phase 4 --- Google

Build:

-   Google sign-in;
-   Google authorization;
-   workbook creation;
-   Sheets tabs;
-   daily sync;
-   manual export;
-   CSV export.

## Phase 5 --- Hardening

Build:

-   parser edge cases;
-   offline tests;
-   sync failures;
-   month rollover;
-   category migration;
-   accessibility;
-   release build.

------------------------------------------------------------------------

# 76. Definition of Done

V1 is complete only when:

-   [ ] Google sign-in works.
-   [ ] Google Sheets authorization works.
-   [ ] User can create arbitrary categories.
-   [ ] User can assign monthly budgets.
-   [ ] Weekly pacing can be enabled/disabled per category.
-   [ ] Rent can remain monthly-only.
-   [ ] Dashboard uses Room only.
-   [ ] Kotak debit parser works.
-   [ ] SBI debit parser works.
-   [ ] Kotak credit is excluded from spending.
-   [ ] Duplicate references are rejected.
-   [ ] Fallback duplicates are rejected.
-   [ ] Notification categorization works in one tap.
-   [ ] Don't record works.
-   [ ] Manual cash transactions work.
-   [ ] Category editing works.
-   [ ] Category deletion safely migrates transactions.
-   [ ] Four periods use actual month days.
-   [ ] Carry-forward can target a later period.
-   [ ] Green/orange/red is percentage-based.
-   [ ] Current-period pace is time-adjusted.
-   [ ] Safe-to-spend works.
-   [ ] Current month never requires cloud fetch.
-   [ ] Daily Google Sheet sync works.
-   [ ] Offline transactions work.
-   [ ] Failed sync retries.
-   [ ] Manual Sheet export works.
-   [ ] CSV export works.
-   [ ] Month rollover works.
-   [ ] Archived months remain locally accessible.
-   [ ] Parser tests pass.
-   [ ] Budget tests pass.
-   [ ] Release logs do not expose financial data.

------------------------------------------------------------------------

# 77. AI coding-agent rules

1.  Treat this file as the V1 source of truth.
2.  Do not invent extra product features.
3.  Do not replace local-first with cloud-first.
4.  Do not replace Room with a cloud database.
5.  Do not put business logic inside Composables.
6.  Do not use Float/Double for money.
7.  Do not put financial data in logs.
8.  Do not implement credit cards.
9.  Do not implement merchant recognition.
10. Do not add other banks without an explicit requirement.
11. Do not read the SMS database for V1.
12. Use NotificationListenerService.
13. Verify current Android/Google API documentation before
    implementation.
14. Add unit tests with every parser/budget feature.
15. Prefer the simplest implementation that satisfies the specification.
16. Keep parser and budget logic platform-independent where practical.
17. Never block local UI on Google APIs.
18. Never make Google Sheets the authoritative source.
19. If an ambiguity remains, choose the option that preserves data
    integrity and offline operation.
20. Before large architectural changes, explain the trade-off and stop
    for approval.

------------------------------------------------------------------------

# 78. First coding-agent prompt

Use the following as the first implementation instruction:

> Read `Budget_Pace_V1_Engineering_UI_Spec.md` completely before
> changing code.
>
> Implement **Phase 1 only**.
>
> Create the Android project foundation using Kotlin, Jetpack Compose,
> Room, Coroutines/Flow, and a clean layered architecture.
>
> Implement:
>
> -   design system;
> -   Month;
> -   Category;
> -   Transaction;
> -   BudgetPeriod;
> -   BudgetCarryForward;
> -   Room entities/DAOs;
> -   repositories;
> -   budget engine;
> -   four-period calculation;
> -   percentage-based status;
> -   current-period pace;
> -   safe-to-spend;
> -   local dashboard using deterministic sample data;
> -   unit tests.
>
> Do not implement:
>
> -   Google APIs;
> -   OAuth;
> -   NotificationListenerService;
> -   Kotak parser;
> -   SBI parser;
> -   WorkManager sync.
>
> The result must compile, run, and pass all Phase 1 tests.
>
> Keep the UI visually aligned with the design system in this document:
> neutral, premium, restrained, slightly sharp, low-radius,
> typography-led, minimal shadows, and no excessive rounded cards.

------------------------------------------------------------------------

# 79. Official implementation references

The coding agent should verify current APIs from official sources:

-   Android NotificationListenerService:
    https://developer.android.com/reference/android/service/notification/NotificationListenerService.html

-   Android Sign in with Google / Credential Manager:
    https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation

-   Sign in with Google overview:
    https://developer.android.com/identity/sign-in/credential-manager-siwg

-   Android Room:
    https://developer.android.com/training/data-storage/room

-   Google Drive API:
    https://developers.google.com/workspace/drive/api/guides/create-file

-   Google OAuth scopes:
    https://developers.google.com/identity/protocols/oauth2/scopes

------------------------------------------------------------------------

# 80. Final architecture

``` text
                         KOTAK / SBI
                              │
                              ▼
                             SMS
                              │
                              ▼
                       GOOGLE MESSAGES
                              │
                              ▼
                    Android notification
                              │
                              ▼
              NotificationListenerService
                              │
                              ▼
                     Transaction Parser
                       │             │
                       ▼             ▼
                    Kotak          SBI
                       │             │
                       └──────┬──────┘
                              ▼
                     Duplicate Detector
                              │
                              ▼
                        ROOM DATABASE
                              │
                ┌─────────────┼─────────────┐
                ▼             ▼             ▼
            Dashboard    Transactions   Budget Engine
                │             │             │
                └─────────────┼─────────────┘
                              ▼
                   Categorization action
                              │
                              ▼
                         ROOM UPDATE
                              │
                              ▼
                     Dashboard refresh
                              │
                              ▼
                       WorkManager
                              │
                              ▼
                    Google Sheets export
                              │
               ┌──────────────┼──────────────┐
               ▼              ▼              ▼
           Dashboard      Transactions    Analytics
```

The central product rule is:

> **Capture locally. Calculate locally. Display locally. Sync
> remotely.**

That rule should remain true throughout V1.
