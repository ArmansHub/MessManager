package com.arman.messmanager.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.Deposit
import com.arman.messmanager.data.repository.FinanceRepository
import kotlinx.coroutines.launch
import java.util.*

data class DashboardUiState(
    val mealRate: Double = 0.0,
    val totalBazaar: Double = 0.0,
    val totalFixedBills: Double = 0.0,
    val totalMeals: Double = 0.0,
    val myDeposits: Double = 0.0,
    val myMealCost: Double = 0.0,
    val myFixedCost: Double = 0.0,
    val myBalance: Double = 0.0,
    val pendingDeposits: List<Deposit> = emptyList()
)

class DashboardViewModel : ViewModel() {

    private val financeRepository = FinanceRepository()

    private val _uiState = MutableLiveData<DashboardUiState>()
    val uiState: LiveData<DashboardUiState> = _uiState

    // Placeholders - replace with actual user and mess data
    private val messId = "default_mess_id"
    private val currentUserId = "user_self_uid"
    private val totalMembers = 10 // This should be fetched from a user repository

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            try {
                val month = Date()
                val bazaarList = financeRepository.getBazaarForMonth(messId, month)
                val fixedBillsList = financeRepository.getFixedBillsForMonth(messId, month)
                val allMealsList = financeRepository.getMealsForMonth(messId, month)
                val myDepositsList = financeRepository.getDepositsForUser(messId, currentUserId, month)
                val pendingDepositsList = financeRepository.getPendingDeposits(messId)

                val totalBazaar = bazaarList.sumOf { it.cost }
                val totalFixedBills = fixedBillsList.sumOf { it.amount }
                val totalMeals = allMealsList.sumOf { it.breakfastCount + it.lunchCount + it.dinnerCount }
                val mealRate = if (totalMeals > 0) totalBazaar / totalMeals else 0.0

                val myMeals = allMealsList.filter { it.userId == currentUserId }
                val myTotalMeals = myMeals.sumOf { it.breakfastCount + it.lunchCount + it.dinnerCount }
                val myMealCost = myTotalMeals * mealRate

                val myDeposits = myDepositsList.filter { it.status == "approved" }.sumOf { it.amount }
                val myFixedCost = if (totalMembers > 0) totalFixedBills / totalMembers else 0.0

                val myBalance = myDeposits - myMealCost - myFixedCost

                _uiState.postValue(
                    DashboardUiState(
                        mealRate = mealRate,
                        totalBazaar = totalBazaar,
                        totalFixedBills = totalFixedBills,
                        totalMeals = totalMeals,
                        myDeposits = myDeposits,
                        myMealCost = myMealCost,
                        myFixedCost = myFixedCost,
                        myBalance = myBalance,
                        pendingDeposits = pendingDepositsList
                    )
                )
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun addBazaar(cost: Double) {
        viewModelScope.launch {
            val bazaar = com.arman.messmanager.data.model.DailyBazaar(
                messId = messId,
                cost = cost,
                addedBy = currentUserId
            )
            financeRepository.addBazaar(bazaar)
            loadDashboardData() // Refresh
        }
    }

    fun addFixedBill(amount: Double, description: String) {
        viewModelScope.launch {
            val bill = com.arman.messmanager.data.model.FixedBill(
                messId = messId,
                amount = amount,
                description = description,
                addedBy = currentUserId
            )
            financeRepository.addFixedBill(bill)
            loadDashboardData() // Refresh
        }
    }

    fun approveDeposit(depositId: String) {
        viewModelScope.launch {
            financeRepository.approveDeposit(depositId)
            loadDashboardData() // Refresh
        }
    }

    fun closeMonth() {
        viewModelScope.launch {
            financeRepository.closeMonth(messId, Date())
            loadDashboardData()
        }
    }
}
