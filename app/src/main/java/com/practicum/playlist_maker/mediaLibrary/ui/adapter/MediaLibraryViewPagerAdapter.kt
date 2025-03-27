package com.practicum.playlist_maker.mediaLibrary.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.practicum.playlist_maker.mediaLibrary.ui.fragment.FavoriteBooksFragment
import com.practicum.playlist_maker.mediaLibrary.ui.fragment.HistoryFragment
import com.practicum.playlist_maker.mediaLibrary.ui.activity.ReadingFragment

class MediaLibraryViewPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = 3 // Три вкладки

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ReadingFragment.newInstance() // Чтение
            1 -> FavoriteBooksFragment.newInstance() // Избранное
            2 -> HistoryFragment.newInstance() // История
            else -> throw IllegalStateException("Unexpected position: $position")
        }
    }
} 