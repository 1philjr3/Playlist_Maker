package com.practicum.playlist_maker.settings.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.FragmentSettingsBinding
import com.practicum.playlist_maker.marchAuto.ActivityLogin
import com.practicum.playlist_maker.settings.ui.view_model.SettingsViewModel
import com.practicum.playlist_maker.settings.ui.view_model.UserProfileViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel


class SettingsFragment : Fragment() {

    private var shareButton: FrameLayout? = null
    private var supportButton: FrameLayout? = null
    private var userAgreementButton: FrameLayout? = null
    private var themeSwitcher: SwitchMaterial? = null
    private var logoutButton: View? = null // Новая кнопка

    private val viewModel: SettingsViewModel by viewModel<SettingsViewModel>()
    private val userProfileViewModel: UserProfileViewModel by viewModels()

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun observeUserData() {
        userProfileViewModel.userName.observe(viewLifecycleOwner) { name ->
            println("Имя пользователя: $name")
        }

        userProfileViewModel.userEmail.observe(viewLifecycleOwner) { email ->
            binding.profileEmail.text = email
        }

        userProfileViewModel.userInstitute.observe(viewLifecycleOwner) { institute ->
            binding.profileInstitute.text = institute
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        observeUserData()

        // Загрузка данных пользователя
        userProfileViewModel.loadUserData()

        shareButton?.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.yandex_practicum_URL))

            startActivityOrShowErrorMessage(
                intent,
                getString(R.string.there_is_no_app_on_the_device_to_make_this_request)
            )
        }

        supportButton?.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.my_email)))
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.email_text))
            }

            startActivityOrShowErrorMessage(
                intent,
                getString(R.string.there_is_no_app_on_the_device_to_send_email)
            )
        }
        userAgreementButton?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(getString(R.string.user_agreement_URL))

            startActivityOrShowErrorMessage(
                intent,
                getString(R.string.there_is_no_browser_on_the_device)
            )
        }

        themeSwitcher?.isChecked = viewModel.isDarkTheme

        themeSwitcher?.setOnCheckedChangeListener { _, checked ->
            viewModel.switchTheme(checked)
        }

        logoutButton?.setOnClickListener { handleLogout() } // Обработка выхода
    }


    private fun handleLogout() {
        // Выход из Firebase Auth
        FirebaseAuth.getInstance().signOut()

        // Возврат на экран авторизации
        val intent = Intent(requireContext(), ActivityLogin::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        Toast.makeText(requireContext(), "Вы вышли из системы", Toast.LENGTH_SHORT).show()
    }

    private fun initViews() {
        shareButton = binding.shareButton
        supportButton = binding.supportButton
        userAgreementButton = binding.userAgreementButton
        themeSwitcher = binding.themeSwitcher
        logoutButton = binding.logoutButton // Инициализация кнопки выхода
    }

    private fun startActivityOrShowErrorMessage(intent: Intent, message: String) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
}