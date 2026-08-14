package com.arman.messmanager.ui.meal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.Meal
import com.arman.messmanager.data.repository.MealRepository
import kotlinx.coroutines.launch
import java.util.*

class MealViewModel : ViewModel() {

    private val mealRepository = MealRepository()

    private val _selectedDate = MutableLiveData<Date>().apply { value = Date() }
    val selectedDate: LiveData<Date> = _selectedDate

    private val _meal = MutableLiveData<Meal?>()
    val meal: LiveData<Meal?> = _meal

    // Placeholder for messId and userId. In a real app, this would be retrieved from user session/auth.
    private val messId = "default_mess_id"
    private var currentUserId = "user_self_uid" // This can be changed by admin selection

    init {
        loadMealForCurrentUser()
    }

    fun loadMealForCurrentUser() {
        viewModelScope.launch {
            try {
                val date = _selectedDate.value ?: Date()
                _meal.postValue(mealRepository.getMealForUser(messId, currentUserId, date))
            } catch (e: Exception) {
                // Handle error
                _meal.postValue(null)
            }
        }
    }

    fun changeDate(days: Int) {
        val calendar = Calendar.getInstance()
        calendar.time = _selectedDate.value ?: Date()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        _selectedDate.value = calendar.time
        loadMealForCurrentUser()
    }

    fun updateMealCount(mealType: MealType, change: Double) {
        val currentMeal = _meal.value ?: return
        val updatedMeal = when (mealType) {
            MealType.BREAKFAST -> currentMeal.copy(breakfastCount = (currentMeal.breakfastCount + change).coerceAtLeast(0.0))
            MealType.LUNCH -> currentMeal.copy(lunchCount = (currentMeal.lunchCount + change).coerceAtLeast(0.0))
            MealType.DINNER -> currentMeal.copy(dinnerCount = (currentMeal.dinnerCount + change).coerceAtLeast(0.0))
        }
        _meal.value = updatedMeal
    }

    fun saveChanges() {
        val mealToSave = _meal.value ?: return
        viewModelScope.launch {
            try {
                mealRepository.updateMeal(mealToSave)
                // Optionally show a success message through a LiveData event
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun setSelectedUser(userId: String) {
        currentUserId = userId
        loadMealForCurrentUser()
    }
}

enum class MealType {
    BREAKFAST, LUNCH, DINNER
}
