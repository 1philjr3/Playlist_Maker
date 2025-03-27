package com.practicum.playlist_maker.marchAuto.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.practicum.playlist_maker.marchAuto.fragments.DocumentationFragment
import com.practicum.playlist_maker.marchAuto.fragments.FavoritesFragment
import com.practicum.playlist_maker.marchAuto.fragments.HistoryFragment

class DocumentationPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getCount(): Int = 3

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> DocumentationFragment.newInstance()
            1 -> FavoritesFragment.newInstance()
            2 -> HistoryFragment.newInstance()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }

    override fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> "Чтение"
            1 -> "Избранное"
            2 -> "История"
            else -> ""
        }
    }
} 