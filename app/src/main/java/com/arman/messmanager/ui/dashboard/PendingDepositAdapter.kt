package com.arman.messmanager.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arman.messmanager.R
import com.arman.messmanager.data.model.Deposit

class PendingDepositAdapter(
    private val onApproveClicked: (Deposit) -> Unit
) : ListAdapter<Deposit, PendingDepositAdapter.ViewHolder>(DepositDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_deposit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onApproveClicked)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val infoTextView: TextView = view.findViewById(R.id.depositInfoTextView)
        private val approveButton: Button = view.findViewById(R.id.approveButton)

        fun bind(deposit: Deposit, onApproveClicked: (Deposit) -> Unit) {
            // In a real app, you'd look up the member's name from their UID
            infoTextView.text = "${deposit.memberUid} - ৳ ${deposit.amount}"
            approveButton.setOnClickListener { onApproveClicked(deposit) }
        }
    }
}

class DepositDiffCallback : DiffUtil.ItemCallback<Deposit>() {
    override fun areItemsTheSame(oldItem: Deposit, newItem: Deposit): Boolean {
        return oldItem.depositId == newItem.depositId
    }

    override fun areContentsTheSame(oldItem: Deposit, newItem: Deposit): Boolean {
        return oldItem == newItem
    }
}
