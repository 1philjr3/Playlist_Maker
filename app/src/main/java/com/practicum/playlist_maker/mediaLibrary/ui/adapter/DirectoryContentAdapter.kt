package com.practicum.playlist_maker.mediaLibrary.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.mediaLibrary.data.model.Directory
import com.practicum.playlist_maker.mediaLibrary.data.model.PdfFile

sealed class DirectoryContentItem {
    data class DirectoryItem(val directory: Directory) : DirectoryContentItem()
    data class PdfItem(val pdfFile: PdfFile) : DirectoryContentItem()
}

class DirectoryContentAdapter(
    private val onDirectoryClick: (Directory) -> Unit,
    private val onDirectoryLongClick: (Directory) -> Unit,
    private val onPdfClick: (PdfFile) -> Unit,
    private val onPdfLongClick: (PdfFile) -> Unit,
    private val onPdfDownloadClick: (PdfFile) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<DirectoryContentItem, RecyclerView.ViewHolder>(DirectoryContentDiffCallback()) {

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

    fun getSelectedItems(): List<DirectoryContentItem> {
        return currentList.filter { item ->
            when (item) {
                is DirectoryContentItem.DirectoryItem -> selectedItems.contains(item.directory.id)
                is DirectoryContentItem.PdfItem -> selectedItems.contains(item.pdfFile.id)
            }
        }
    }

    fun isInSelectionMode() = selectionMode

    fun clearSelection() {
        selectedItems.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DirectoryContentItem.DirectoryItem -> VIEW_TYPE_DIRECTORY
            is DirectoryContentItem.PdfItem -> VIEW_TYPE_PDF
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_DIRECTORY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_directory, parent, false)
                DirectoryViewHolder(view)
            }
            VIEW_TYPE_PDF -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_pdf, parent, false)
                PdfViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DirectoryContentItem.DirectoryItem -> (holder as DirectoryViewHolder).bind(item.directory)
            is DirectoryContentItem.PdfItem -> (holder as PdfViewHolder).bind(item.pdfFile)
        }
    }

    inner class DirectoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivDirectory: ImageView = itemView.findViewById(R.id.ivDirectory)
        private val tvDirectoryName: TextView = itemView.findViewById(R.id.tvDirectoryName)
        private val tvDirectoryDescription: TextView = itemView.findViewById(R.id.tvDirectoryDescription)

        fun bind(directory: Directory) {
            tvDirectoryName.text = directory.name
            tvDirectoryDescription.text = directory.description

            Glide.with(itemView.context)
                .load(directory.imageUrl)
                .placeholder(R.drawable.ic_directory)
                .error(R.drawable.ic_directory)
                .into(ivDirectory)

            val isSelected = selectedItems.contains(directory.id)
            itemView.setBackgroundResource(
                if (isSelected) R.color.selected_item_background 
                else android.R.color.transparent
            )

            itemView.setOnClickListener {
                if (selectionMode) {
                    toggleDirectorySelection(directory)
                } else {
                    onDirectoryClick(directory)
                }
            }

            itemView.setOnLongClickListener {
                if (!selectionMode) {
                    selectionMode = true
                    toggleDirectorySelection(directory)
                    onSelectionChanged(selectedItems.size)
                    notifyDataSetChanged()
                } else {
                    toggleDirectorySelection(directory)
                }
                true
            }
        }
        
        private fun toggleDirectorySelection(directory: Directory) {
            if (selectedItems.contains(directory.id)) {
                selectedItems.remove(directory.id)
            } else {
                selectedItems.add(directory.id)
            }
            notifyItemChanged(adapterPosition)
            onSelectionChanged(selectedItems.size)
        }
    }

    inner class PdfViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        private val tvLastOpened: TextView = itemView.findViewById(R.id.tvLastOpened)
        private val ivPdfIcon: ImageView = itemView.findViewById(R.id.ivPdfIcon)
        private val ivDownload: ImageView = itemView.findViewById(R.id.ivDownload)

        fun bind(pdfFile: PdfFile) {
            tvFileName.text = pdfFile.name
            tvFileSize.text = pdfFile.getFormattedSize()
            tvLastOpened.text = pdfFile.getFormattedLastOpened()
            
            val isSelected = selectedItems.contains(pdfFile.id)
            itemView.setBackgroundResource(
                if (isSelected) R.color.selected_item_background 
                else android.R.color.transparent
            )
            
            itemView.setOnClickListener {
                if (selectionMode) {
                    togglePdfSelection(pdfFile)
                } else {
                    onPdfClick(pdfFile)
                }
            }
            
            itemView.setOnLongClickListener {
                println("DEBUG: Долгое нажатие на PDF файл: ${pdfFile.name}, ID: ${pdfFile.id}")
                if (!selectionMode) {
                    selectionMode = true
                    togglePdfSelection(pdfFile)
                    onSelectionChanged(selectedItems.size)
                    notifyDataSetChanged()
                } else {
                    togglePdfSelection(pdfFile)
                }
                true
            }
            
            ivDownload.setOnClickListener {
                onPdfDownloadClick(pdfFile)
            }
        }
        
        private fun togglePdfSelection(pdfFile: PdfFile) {
            if (selectedItems.contains(pdfFile.id)) {
                selectedItems.remove(pdfFile.id)
            } else {
                selectedItems.add(pdfFile.id)
            }
            notifyItemChanged(adapterPosition)
            onSelectionChanged(selectedItems.size)
        }
    }

    companion object {
        private const val VIEW_TYPE_DIRECTORY = 0
        private const val VIEW_TYPE_PDF = 1
    }
}

class DirectoryContentDiffCallback : DiffUtil.ItemCallback<DirectoryContentItem>() {
    override fun areItemsTheSame(oldItem: DirectoryContentItem, newItem: DirectoryContentItem): Boolean {
        return when {
            oldItem is DirectoryContentItem.DirectoryItem && newItem is DirectoryContentItem.DirectoryItem ->
                oldItem.directory.id == newItem.directory.id
            oldItem is DirectoryContentItem.PdfItem && newItem is DirectoryContentItem.PdfItem ->
                oldItem.pdfFile.id == newItem.pdfFile.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: DirectoryContentItem, newItem: DirectoryContentItem): Boolean {
        return oldItem == newItem
    }
} 