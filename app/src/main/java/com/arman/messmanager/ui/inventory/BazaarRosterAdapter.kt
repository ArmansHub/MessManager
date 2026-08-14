package com.arman.messmanager.ui.inventory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arman.messmanager.R
import com.arman.messmanager.data.model.BazaarRoster
import java.text.SimpleDateFormat
import java.util.Locale

class BazaarRosterAdapter : ListAdapter<BazaarRoster, BazaarRosterAdapter.ViewHolder>(BazaarRosterDiffCallback()) {

    private val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bazaar_roster, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.date.text = dateFormat.format(item.date.toDate())
        holder.memberName.text = item.assignedMemberName
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val date: TextView = view.findViewById(R.id.rosterDateTextView)
        val memberName: TextView = view.findViewById(R.id.assignedMemberTextView)
    }
}

class BazaarRosterDiffCallback : DiffUtil.ItemCallback<BazaarRoster>() {
    override fun areItemsTheSame(oldItem: BazaarRoster, newItem: BazaarRoster): Boolean {
        return oldItem.rosterId == newItem.rosterId
    }

    override fun areContentsTheSame(oldItem: BazaarRoster, newItem: BazaarRoster): Boolean {
        return oldItem == newItem
    }
}
