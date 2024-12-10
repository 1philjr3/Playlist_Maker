//package com.practicum.playlist_maker.mediaLibrary.ui.activity
//
//import android.content.Intent
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.fragment.app.Fragment
//import com.practicum.playlist_maker.databinding.FragmentThemesBinding
//import com.practicum.playlist_maker.presentation.MainActivity
//
//class ThemesFragment : Fragment() {
//
//    companion object {
//        fun newInstance(): ThemesFragment {
//            return ThemesFragment()
//        }
//    }
//
//    private var _binding: FragmentThemesBinding? = null
//    private val binding get() = _binding!!
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentThemesBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        // Слушатель нажатия для перехода в MainActivity
//        binding.openMainActivityButton.setOnClickListener {
//            val intent = Intent(requireContext(), MainActivity::class.java)
//            startActivity(intent)
//        }
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}
//
