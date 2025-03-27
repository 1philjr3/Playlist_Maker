package com.practicum.playlist_maker.marchAuto.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.marchAuto.models.ActivityHistoryItem

class ActivityHistoryAdapter : RecyclerView.Adapter<ActivityHistoryAdapter.ViewHolder>() {
    private var items: List<ActivityHistoryItem> = emptyList()

    fun updateItems(newItems: List<ActivityHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDateTime: TextView = view.findViewById(R.id.tvDateTime)
        private val tvAction: TextView = view.findViewById(R.id.tvAction)
        private val tvPath: TextView = view.findViewById(R.id.tvPath)

        fun bind(item: ActivityHistoryItem) {
            tvDateTime.text = item.getFormattedDate()
            
            val userName = item.userName ?: "Неизвестный пользователь"
            val actionText = item.getActionText()
            val itemTypeText = item.getItemTypeText()
            val itemName = item.itemName ?: "Неизвестный объект"
            
            tvAction.text = "$userName $actionText $itemTypeText $itemName"
            
            val path = item.path
            if (path != null && path.isNotEmpty()) {
                tvPath.visibility = View.VISIBLE
                tvPath.text = "Путь: $path"
            } else {
                tvPath.visibility = View.GONE
            }
        }
    }
} 