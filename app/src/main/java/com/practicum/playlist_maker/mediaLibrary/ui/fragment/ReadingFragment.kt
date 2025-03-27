package com.practicum.playlist_maker.mediaLibrary.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.mediaLibrary.ui.fragment.DirectoryContentFragment

class ReadingFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reading, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DirectoryContentFragment.newInstance())
                .commit()
        }
    }

    companion object {
        fun newInstance(): ReadingFragment {
            return ReadingFragment()
        }
    }
} 