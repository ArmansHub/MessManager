package com.arman.messmanager.data.model

// One member's slice of a MonthSummary - the numbers behind their carried-over balance
// (SRS section 8, "Month Closing" step 3: "Carries over the final balances ... as the
// Opening Balance for the new month").
data class MemberMonthSummary(
    val uid: String = "",
    val name: String = "",
    val openingBalance: Double = 0.0,
    val totalDeposits: Double = 0.0,
    val totalMeals: Double = 0.0,
    val mealCost: Double = 0.0,
    val fixedBillShare: Double = 0.0,
    val specialMealCost: Double = 0.0,
    val closingBalance: Double = 0.0
)

// One month's archived snapshot, written once by "Close Month" (SRS section 8). Mirrors
// what the Finance Manager Dashboard shows live during the month, frozen at the moment
// the month closes, plus which members won the Manager Election for the month that
// follows (null on both if no election was open at close time).
data class MonthSummary(
    val summaryId: String = "",
    val messId: String = "",
    val monthId: String = "",
    val totalStandardBazaarCost: Double = 0.0,
    val totalSpecialMealCost: Double = 0.0,
    val totalFixedBillsCost: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalDeposits: Double = 0.0,
    val totalStandardMeals: Double = 0.0,
    val mealRate: Double = 0.0,
    val memberSummaries: List<MemberMonthSummary> = emptyList(),
    val newFinanceManagerUid: String? = null,
    val newMealManagerUid: String? = null,
    val closedByUid: String = "",
    val closedAtEpochMs: Long = 0L
)
