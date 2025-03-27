package com.practicum.playlist_maker.mediaLibrary.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.ItemContentBinding
import com.practicum.playlist_maker.mediaLibrary.model.Content
import com.practicum.playlist_maker.mediaLibrary.model.ContentType

class ContentAdapter(
    var contents: List<Content>,
    private val onContentClick: (Content) -> Unit
) : RecyclerView.Adapter<ContentAdapter.ContentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        val binding = ItemContentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        holder.bind(contents[position])
    }

    override fun getItemCount(): Int = contents.size

    inner class ContentViewHolder(private val binding: ItemContentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(content: Content) {
            binding.tvName.text = content.name
            binding.ivIcon.setImageResource(
                if (content.type == ContentType.DIRECTORY) R.drawable.ic_pdf else R.drawable.ic_launcher_foreground
            )
            binding.root.setOnClickListener {
                onContentClick(content)
            }
        }
    }
}