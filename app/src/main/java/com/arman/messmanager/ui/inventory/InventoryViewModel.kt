package com.arman.messmanager.ui.inventory

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arman.messmanager.data.model.BazaarRoster
import com.arman.messmanager.data.model.InventoryItem
import com.arman.messmanager.data.repository.BazaarRosterRepository
import com.arman.messmanager.data.repository.InventoryRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class InventoryViewModel : ViewModel() {

    private val inventoryRepository = InventoryRepository()
    private val bazaarRosterRepository = BazaarRosterRepository()

    private val _inventoryItems = MutableLiveData<List<InventoryItem>>()
    val inventoryItems: LiveData<List<InventoryItem>> = _inventoryItems

    private val _bazaarRoster = MutableLiveData<List<BazaarRoster>>()
    val bazaarRoster: LiveData<List<BazaarRoster>> = _bazaarRoster

    // Placeholder for messId. In a real app, this would be retrieved from user session/auth.
    private val messId = "default_mess_id"

    init {
        loadInventory()
        loadBazaarRoster()
    }

    private fun loadInventory() {
        viewModelScope.launch {
            try {
                _inventoryItems.postValue(inventoryRepository.getInventory(messId))
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun loadBazaarRoster() {
        viewModelScope.launch {
            try {
                val calendar = Calendar.getInstance()
                // Show roster for the current month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                _bazaarRoster.postValue(bazaarRosterRepository.getBazaarRoster(messId, calendar.time))
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateInventoryStockStatus(item: InventoryItem, isLow: Boolean) {
        viewModelScope.launch {
            try {
                val updatedItem = item.copy(isLowStock = isLow)
                inventoryRepository.updateInventoryItem(updatedItem)
                // Optimistic update
                val currentList = _inventoryItems.value?.toMutableList() ?: mutableListOf()
                val index = currentList.indexOfFirst { it.itemId == item.itemId }
                if (index != -1) {
                    currentList[index] = updatedItem
                    _inventoryItems.postValue(currentList)
                }
            } catch (e: Exception) {
                // Handle error, maybe revert UI state
            }
        }
    }

    fun assignBazaarDuty(date: Date, memberUid: String, memberName: String) {
        viewModelScope.launch {
            try {
                val newRosterEntry = BazaarRoster(
                    messId = messId,
                    date = Timestamp(date),
                    assignedMemberUid = memberUid,
                    assignedMemberName = memberName
                )
                bazaarRosterRepository.assignBazaarDuty(newRosterEntry)
                loadBazaarRoster() // Refresh the roster list
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
