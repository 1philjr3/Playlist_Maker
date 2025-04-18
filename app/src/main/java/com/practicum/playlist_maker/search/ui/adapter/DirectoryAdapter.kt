package com.practicum.playlist_maker.search.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.search.data.api.DirectoryResponse

/**
 * Адаптер для списка директорий
 */
class DirectoryAdapter(
    private val onDirectoryClicked: (DirectoryResponse) -> Unit
) : ListAdapter<DirectoryResponse, DirectoryAdapter.DirectoryViewHolder>(DirectoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_directory, parent, false)
        return DirectoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DirectoryViewHolder, position: Int) {
        val directory = getItem(position)
        holder.bind(directory)
    }

    inner class DirectoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val directoryName: TextView = itemView.findViewById(R.id.directoryName)
        private val directoryPath: TextView = itemView.findViewById(R.id.directoryPath)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDirectoryClicked(getItem(position))
                }
            }
        }

        fun bind(directory: DirectoryResponse) {
            directoryName.text = directory.name
            directoryPath.text = directory.path
        }
    }

    private class DirectoryDiffCallback : DiffUtil.ItemCallback<DirectoryResponse>() {
        override fun areItemsTheSame(oldItem: DirectoryResponse, newItem: DirectoryResponse): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: DirectoryResponse, newItem: DirectoryResponse): Boolean {
            return oldItem == newItem
        }
    }
} 