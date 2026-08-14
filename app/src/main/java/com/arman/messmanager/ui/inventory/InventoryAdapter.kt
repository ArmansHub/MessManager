package com.arman.messmanager.ui.inventory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arman.messmanager.R
import com.arman.messmanager.data.model.InventoryItem
import com.google.android.material.switchmaterial.SwitchMaterial

class InventoryAdapter(
    private val onStockStatusChanged: (InventoryItem, Boolean) -> Unit
) : ListAdapter<InventoryItem, InventoryAdapter.ViewHolder>(InventoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onStockStatusChanged)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val itemName: TextView = view.findViewById(R.id.itemNameTextView)
        private val lowStockSwitch: SwitchMaterial = view.findViewById(R.id.lowStockSwitch)

        fun bind(item: InventoryItem, onStockStatusChanged: (InventoryItem, Boolean) -> Unit) {
            itemName.text = item.itemName
            lowStockSwitch.setOnCheckedChangeListener(null) // prevent listener firing on bind
            lowStockSwitch.isChecked = item.isLowStock
            lowStockSwitch.setOnCheckedChangeListener { _, isChecked ->
                onStockStatusChanged(item, isChecked)
            }
        }
    }
}

class InventoryDiffCallback : DiffUtil.ItemCallback<InventoryItem>() {
    override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean {
        return oldItem.itemId == newItem.itemId
    }

    override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean {
        return oldItem == newItem
    }
}
