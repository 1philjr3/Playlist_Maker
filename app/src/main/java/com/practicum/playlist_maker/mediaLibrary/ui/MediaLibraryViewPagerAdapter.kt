package com.practicum.playlist_maker.mediaLibrary.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.practicum.playlist_maker.mediaLibrary.ui.activity.FavoriteTracksFragment
//import com.practicum.playlist_maker.mediaLibrary.ui.activity.PlaylistsFragment
import com.practicum.playlist_maker.pdfreader.ui.activity.home.HomeFragment

class MediaLibraryViewPagerAdapter(
    fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = 2 // Указываем количество вкладок

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FavoriteTracksFragment.newInstance() // Первая вкладка
//            1 -> PlaylistsFragment.newInstance()      // Вторая вкладка
            1 -> HomeFragment.newInstance()
            else -> throw IllegalStateException("Unexpected position: $position")
        }
    }
}