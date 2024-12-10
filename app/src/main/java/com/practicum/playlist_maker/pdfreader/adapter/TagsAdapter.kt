package com.practicum.playlist_maker.pdfreader.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.practicum.playlist_maker.databinding.AdapterTagBinding
import com.practicum.playlist_maker.pdfreader.model.TagModel
import com.practicum.playlist_maker.pdfreader.utils.TagColors

class TagsAdapter(
    private val tags: List<TagModel>,
    private val listener: Listener
) : RecyclerView.Adapter<TagsAdapter.ViewHolder>() {
    private val tagColor = TagColors()
    interface Listener{
        fun onTagClicked(tagModel: TagModel)
        fun onTagRemoveClicked(tagModel: TagModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            AdapterTagBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.tvTagName.text = tags[position].title
        holder.binding.tvTagName.setOnClickListener { listener.onTagClicked(tags[position]) }
        holder.binding.btRemoveTag.setOnClickListener { listener.onTagRemoveClicked(tags[position]) }
        holder.binding.root.backgroundTintList = ColorStateList.valueOf(tagColor.getColor(tags[position].id))
    }

    override fun getItemCount(): Int {
        return tags.size
    }

    inner class ViewHolder(val binding: AdapterTagBinding) :
        RecyclerView.ViewHolder(binding.root) {

    }
}