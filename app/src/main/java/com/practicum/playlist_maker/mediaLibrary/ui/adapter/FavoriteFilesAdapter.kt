package com.practicum.playlist_maker.mediaLibrary.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.practicum.playlist_maker.databinding.ItemFavoriteFileBinding
import com.practicum.playlist_maker.mediaLibrary.model.FavoriteFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FavoriteFilesAdapter(
    private val onItemClick: (FavoriteFile) -> Unit
) : ListAdapter<FavoriteFile, FavoriteFilesAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFavoriteFileBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }
        
        fun bind(file: FavoriteFile) {
            binding.tvFileName.text = file.name
            
            try {
                // Если строка с датой - это timestamp
                if (file.addedToFavoritesAt.all { it.isDigit() }) {
                    val timestamp = file.addedToFavoritesAt.toLong()
                    val date = Date(timestamp)
                    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    binding.tvAddedDate.text = "Добавлено: ${formatter.format(date)}"
                } else {
                    // Если строка с датой в другом формате
                    binding.tvAddedDate.text = "Добавлено: ${file.addedToFavoritesAt}"
                }
            } catch (e: Exception) {
                binding.tvAddedDate.text = "Добавлено в избранное"
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FavoriteFile>() {
        override fun areItemsTheSame(oldItem: FavoriteFile, newItem: FavoriteFile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FavoriteFile, newItem: FavoriteFile): Boolean {
            return oldItem == newItem
        }
    }
} 