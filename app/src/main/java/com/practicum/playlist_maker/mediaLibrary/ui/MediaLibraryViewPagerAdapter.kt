package com.practicum.playlist_maker.mediaLibrary.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.practicum.playlist_maker.mediaLibrary.ui.fragment.FavoriteBooksFragment
import com.practicum.playlist_maker.mediaLibrary.ui.activity.ReadingFragment
import com.practicum.playlist_maker.marchAuto.fragments.HistoryFragment

class MediaLibraryViewPagerAdapter(
    fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = 3 // Указываем количество вкладок (теперь их 3)

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ReadingFragment.newInstance() // Первая вкладка
            1 -> FavoriteBooksFragment.newInstance() // Вторая вкладка
            2 -> HistoryFragment.newInstance() // Третья вкладка с историей
            else -> throw IllegalStateException("Unexpected position: $position")
        }
    }
}