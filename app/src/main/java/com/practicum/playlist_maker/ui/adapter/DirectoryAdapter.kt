//package com.practicum.playlist_maker.ui.adapter
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.recyclerview.widget.RecyclerView
//import com.bumptech.glide.Glide
//import com.practicum.playlist_maker.R
//import com.practicum.playlist_maker.data.model.Directory
//import com.practicum.playlist_maker.mediaLibrary.ui.adapter.DirectoryAdapter
//
//class DirectoryAdapter(
//    private val onDirectoryClick: (Directory) -> Unit
//) : RecyclerView.Adapter<DirectoryAdapter.DirectoryViewHolder>() {
//
//    private var directories: List<Directory> = emptyList()
//
//    fun submitList(newDirectories: List<Directory>) {
//        directories = newDirectories
//        notifyDataSetChanged()
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectoryViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.adapter_directory_list, parent, false)
//        return DirectoryViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: DirectoryViewHolder, position: Int) {
//        holder.bind(directories[position])
//    }
//
//    override fun getItemCount() = directories.size
//
//    inner class DirectoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        private val imageView: ImageView = itemView.findViewById(R.id.ivDirectory)
//        private val nameTextView: TextView = itemView.findViewById(R.id.tvDirectoryName)
//        private val descriptionTextView: TextView = itemView.findViewById(R.id.tvDirectoryDescription)
//
//        fun bind(directory: Directory) {
//            nameTextView.text = directory.name
//            descriptionTextView.text = directory.description
//
//            Glide.with(itemView.context)
//                .load(directory.imageUrl)
//                .placeholder(R.drawable.ic_folder)
//                .into(imageView)
//
//            itemView.setOnClickListener {
//                onDirectoryClick(directory)
//            }
//        }
//    }
//}