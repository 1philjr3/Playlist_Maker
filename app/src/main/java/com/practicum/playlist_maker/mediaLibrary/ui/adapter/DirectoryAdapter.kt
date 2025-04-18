package com.practicum.playlist_maker.mediaLibrary.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.mediaLibrary.data.model.Directory

class DirectoryAdapter(
    private val onDirectoryClick: (Directory) -> Unit,
    private val onDirectoryLongClick: (Directory) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<Directory, DirectoryAdapter.DirectoryViewHolder>(DirectoryDiffCallback()) {

    private var selectionMode = false
    private val selectedItems = mutableSetOf<String>()

    fun toggleSelectionMode() {
        selectionMode = !selectionMode
        if (!selectionMode) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    fun getSelectedItems(): List<Directory> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun isInSelectionMode() = selectionMode

    fun clearSelection() {
        selectedItems.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_directory, parent, false)
        return DirectoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DirectoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DirectoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivDirectory: ImageView = itemView.findViewById(R.id.ivDirectory)
        private val tvDirectoryName: TextView = itemView.findViewById(R.id.tvDirectoryName)
        private val tvDirectoryDescription: TextView = itemView.findViewById(R.id.tvDirectoryDescription)

        fun bind(directory: Directory) {
            tvDirectoryName.text = directory.name
            tvDirectoryDescription.text = directory.description

            Glide.with(itemView.context)
                .load("http://192.168.0.86:3000" + directory.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_directory)
                .fallback(R.drawable.ic_directory)
                .error(R.drawable.ic_directory)
                .into(ivDirectory)

            val isSelected = selectedItems.contains(directory.id)
            itemView.setBackgroundResource(if (isSelected) R.color.selected_item_background else android.R.color.transparent)

            itemView.setOnClickListener {
                if (selectionMode) {
                    if (isSelected) {
                        selectedItems.remove(directory.id)
                    } else {
                        selectedItems.add(directory.id)
                    }
                    notifyItemChanged(adapterPosition)
                    onSelectionChanged(selectedItems.size)
                } else {
                    onDirectoryClick(directory)
                }
            }

            itemView.setOnLongClickListener {
                if (!selectionMode) {
                    selectionMode = true
                    selectedItems.add(directory.id)
                    notifyDataSetChanged()
                    onSelectionChanged(selectedItems.size)
                }
                true
            }
        }
    }

    private class DirectoryDiffCallback : DiffUtil.ItemCallback<Directory>() {
        override fun areItemsTheSame(oldItem: Directory, newItem: Directory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Directory, newItem: Directory): Boolean {
            return oldItem == newItem
        }
    }
}