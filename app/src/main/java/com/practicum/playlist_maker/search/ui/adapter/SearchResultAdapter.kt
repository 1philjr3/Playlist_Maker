package com.practicum.playlist_maker.search.ui.adapter

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.search.data.api.SearchResponse

/**
 * Адаптер для отображения результатов поиска
 */
class SearchResultAdapter(
    private val onResultClicked: (SearchResponse) -> Unit
) : ListAdapter<SearchResponse, SearchResultAdapter.SearchResultViewHolder>(SearchResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val result = getItem(position)
        holder.bind(result)
    }

    inner class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileIcon: ImageView = itemView.findViewById(R.id.fileIcon)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val filePath: TextView = itemView.findViewById(R.id.filePath)
        private val fileSize: TextView = itemView.findViewById(R.id.fileSize)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onResultClicked(getItem(position))
                }
            }
        }

        fun bind(result: SearchResponse) {
            fileName.text = result.name
            
            // Используем parentPath для отображения пути к директории, если он есть
            val displayPath = if (!result.parentPath.isNullOrEmpty()) {
                // Если есть родительский путь, отображаем его
                result.parentPath + "/" + result.name
            } else {
                // Иначе очищаем URL от базовой части
                val cleanPath = when {
                    result.path.contains("/uploads/pdfs/") -> {
                        "pdfs/" + result.path.substringAfterLast("/uploads/pdfs/")
                    }
                    result.path.contains("http") -> {
                        result.path.substringAfterLast("/").let { 
                            if (result.type == "directory") "directory/$it" else "pdfs/$it" 
                        }
                    }
                    else -> result.path
                }
                cleanPath
            }
            
            filePath.text = displayPath
            
            // Установка иконки в зависимости от типа файла
            val iconResId = when (result.type) {
                "pdf" -> R.drawable.ic_pdf_vector
                "directory" -> R.drawable.ic_folder
                else -> R.drawable.ic_file // Дефолтная иконка файла
            }
            fileIcon.setImageResource(iconResId)
            
            // Отображение размера файла, если есть
            if (result.size != null && result.type != "directory") {
                fileSize.visibility = View.VISIBLE
                fileSize.text = Formatter.formatFileSize(itemView.context, result.size)
            } else {
                fileSize.visibility = View.GONE
            }
        }
    }

    private class SearchResultDiffCallback : DiffUtil.ItemCallback<SearchResponse>() {
        override fun areItemsTheSame(oldItem: SearchResponse, newItem: SearchResponse): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: SearchResponse, newItem: SearchResponse): Boolean {
            return oldItem == newItem
        }
    }
} 