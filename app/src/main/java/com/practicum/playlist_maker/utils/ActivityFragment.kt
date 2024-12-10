package com.practicum.playlist_maker.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

class ActivityFragment : Fragment() {

    companion object {
        private const val ARG_ACTIVITY_CLASS = "ARG_ACTIVITY_CLASS"

        fun newInstance(activityClass: Class<*>) = ActivityFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_ACTIVITY_CLASS, activityClass)
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val activityClass = arguments?.getSerializable(ARG_ACTIVITY_CLASS) as? Class<*>
            ?: throw IllegalStateException("Activity class is not provided")

        val intent = Intent(requireContext(), activityClass)
        startActivity(intent)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FrameLayout(requireContext()) // Возвращаем пустой контейнер
    }
}
