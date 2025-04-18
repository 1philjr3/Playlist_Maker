package com.practicum.playlist_maker.settings.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.FragmentSettingsBinding
import com.practicum.playlist_maker.databinding.DialogAvatarPreviewBinding
import com.practicum.playlist_maker.marchAuto.ActivityEditProfile
import com.practicum.playlist_maker.marchAuto.ActivityLogin
import com.practicum.playlist_maker.settings.ui.view_model.SettingsViewModel
import com.practicum.playlist_maker.settings.ui.view_model.UserProfileViewModel
import com.practicum.playlist_maker.settings.ui.view_model.UserProfileViewModelFactory
import org.koin.androidx.viewmodel.ext.android.viewModel
import android.app.Dialog
import java.io.File


class SettingsFragment : Fragment() {

    private var shareButton: FrameLayout? = null
    private var supportButton: FrameLayout? = null
    private var themeSwitcher: SwitchMaterial? = null
    private var logoutButton: View? = null
    private var editProfileButton: ImageView? = null
    private var avatarImage: ImageView? = null

    private val viewModel: SettingsViewModel by viewModel<SettingsViewModel>()
    private lateinit var userProfileViewModel: UserProfileViewModel

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        // Создаем фабрику для UserProfileViewModel
        val factory = UserProfileViewModelFactory(requireActivity().application)
        userProfileViewModel = ViewModelProvider(this, factory)[UserProfileViewModel::class.java]
        return binding.root
    }

    private fun observeUserData() {
        userProfileViewModel.userName.observe(viewLifecycleOwner) { name ->
            binding.profileName.text = name
            Log.d("SettingsFragment", "Обновляем имя пользователя: $name")
        }

        userProfileViewModel.userEmail.observe(viewLifecycleOwner) { email ->
            binding.profileEmail.text = email
            Log.d("SettingsFragment", "Обновляем email пользователя: $email")
        }

        userProfileViewModel.userInstitute.observe(viewLifecycleOwner) { institute ->
            binding.profileInstitute.text = institute
            Log.d("SettingsFragment", "Обновляем отдел пользователя: $institute")
        }
        
        userProfileViewModel.userAvatarUri.observe(viewLifecycleOwner) { avatarUri ->
            loadAvatar(avatarUri)
        }
    }

    private fun loadAvatar(avatarUri: String?) {
        if (avatarUri != null && avatarUri.isNotEmpty()) {
            Log.d("SettingsFragment", "Загрузка аватарки: $avatarUri")
            try {
                val uri = Uri.parse(avatarUri)
                
                Log.d("SettingsFragment", "Аватар URI после парсинга: $uri, scheme: ${uri.scheme}")
                
                // Очищаем кеш Glide для этого URI
                Glide.with(this).clear(binding.avatarImage)
                
                // Загружаем изображение с помощью Glide без listener
                Glide.with(this)
                    .load(uri)
                    .error(R.drawable.ic_person_placeholder)
                    .skipMemoryCache(true)  // Не используем кеш в памяти
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Не используем кеш на диске
                    .circleCrop()
                    .into(binding.avatarImage)
                
                // Добавляем обработчик нажатия
                binding.avatarImage.setOnClickListener {
                    openAvatarFullScreen(uri)
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Ошибка при обработке URI аватарки: ${e.message}", e)
                setDefaultAvatar()
            }
        } else {
            Log.d("SettingsFragment", "Аватарка не задана, устанавливаем значение по умолчанию")
            setDefaultAvatar()
        }
    }

    private fun setDefaultAvatar() {
        binding.avatarImage.setImageResource(R.drawable.ic_person_placeholder)
        binding.avatarImage.setOnClickListener(null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Добавим принудительную загрузку данных из SharedPreferences при создании представления
        val sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val avatarUri = sharedPreferences.getString("avatar_uri", null)
        Log.d("SettingsFragment", "При создании фрагмента, аватар из SharedPreferences: $avatarUri")
        
        initViews()
        
        // Вывод текущих значений в дебаг
        Log.d("SettingsFragment", "Текущее имя: ${binding.profileName.text}")
        Log.d("SettingsFragment", "Текущий email: ${binding.profileEmail.text}")
        Log.d("SettingsFragment", "Текущий отдел: ${binding.profileInstitute.text}")
        
        // Загрузка данных пользователя и подписка на обновления
        observeUserData()
        userProfileViewModel.loadUserData()
        
        // Проверяем SharedPreferences напрямую
        val name = sharedPreferences.getString("name", null)
        val email = sharedPreferences.getString("email", null)
        val institute = sharedPreferences.getString("institute", null)
        
        Log.d("SettingsFragment", "Значения в SharedPreferences: name=$name, email=$email, institute=$institute")
        
        editProfileButton?.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditProfile::class.java)
            intent.putExtra("name", binding.profileName.text.toString())
            intent.putExtra("institute", binding.profileInstitute.text.toString())
            startActivity(intent)
        }
        
        shareButton?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://c87a-45-85-105-89.ngrok-free.app/download")

            startActivityOrShowErrorMessage(
                intent,
                getString(R.string.there_is_no_browser_on_the_device)
            )
        }

        supportButton?.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("123uks123@mail.ru"))
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.email_text))
            }

            startActivityOrShowErrorMessage(
                intent,
                getString(R.string.there_is_no_app_on_the_device_to_send_email)
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

        // Очистка данных о сохраненной сессии
        val sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        with (sharedPreferences.edit()) {
            remove("token")
            remove("email")
            remove("name")
            remove("institute")
            remove("avatar_uri")
            putBoolean("is_logged_in", false)
            apply()
        }

        // Возврат на экран авторизации
        val intent = Intent(requireContext(), ActivityLogin::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        Toast.makeText(requireContext(), "Вы вышли из системы", Toast.LENGTH_SHORT).show()
    }

    private fun initViews() {
        shareButton = binding.shareButton
        supportButton = binding.supportButton
        themeSwitcher = binding.themeSwitcher
        logoutButton = binding.logoutButton
        editProfileButton = binding.editProfileButton
        avatarImage = binding.avatarImage
    }

    private fun startActivityOrShowErrorMessage(intent: Intent, message: String) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("SettingsFragment", "onResume: загружаем актуальные данные пользователя")
        
        // При возвращении на экран обновляем данные пользователя
        userProfileViewModel.loadUserData()
        
        // Отладочная информация - данные из SharedPreferences после возвращения
        val sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val name = sharedPreferences.getString("name", null)
        val institute = sharedPreferences.getString("institute", null)
        val avatarUri = sharedPreferences.getString("avatar_uri", null)
        
        Log.d("SettingsFragment", "onResume - SharedPreferences: name=$name, institute=$institute, avatarUri=$avatarUri")
        
        // Проверяем, если аватарка - это файловый URI, существует ли сам файл
        if (avatarUri != null && avatarUri.startsWith("file:")) {
            try {
                val file = File(Uri.parse(avatarUri).path!!)
                if (!file.exists()) {
                    Log.d("SettingsFragment", "Файл аватарки не существует: ${file.absolutePath}")
                    // Пробуем альтернативный путь из SharedPreferences
                    val alternativePath = sharedPreferences.getString("avatar_file_path", null)
                    if (alternativePath != null) {
                        val alternativeFile = File(alternativePath)
                        if (alternativeFile.exists()) {
                            Log.d("SettingsFragment", "Найден альтернативный файл аватарки: $alternativePath")
                            // Обновляем URI в SharedPreferences
                            val newUri = Uri.fromFile(alternativeFile).toString()
                            sharedPreferences.edit().putString("avatar_uri", newUri).commit()
                        }
                    }
                } else {
                    Log.d("SettingsFragment", "Файл аватарки существует: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Ошибка при проверке файла аватарки", e)
            }
        }
        
        // Принудительно обновляем профиль на случай, если LiveData не сработает
        updateProfileUIFromPreferences(sharedPreferences)
    }
    
    private fun updateProfileUIFromPreferences(sharedPreferences: android.content.SharedPreferences) {
        val name = sharedPreferences.getString("name", null)
        val email = sharedPreferences.getString("email", null)
        val institute = sharedPreferences.getString("institute", null)
        val avatarUri = sharedPreferences.getString("avatar_uri", null)
        
        Log.d("SettingsFragment", "Загружаем данные профиля из SharedPreferences: name=$name, email=$email, institute=$institute, avatarUri=$avatarUri")
        
        if (name != null) {
            binding.profileName.text = name
        }
        
        if (email != null) {
            binding.profileEmail.text = email
        }
        
        if (institute != null) {
            binding.profileInstitute.text = institute
        }
        
        if (avatarUri != null && avatarUri.isNotEmpty()) {
            try {
                // Проверяем, если это file URI, существует ли файл
                if (avatarUri.startsWith("file:")) {
                    val file = File(Uri.parse(avatarUri).path!!)
                    if (!file.exists()) {
                        Log.d("SettingsFragment", "Файл аватарки не существует: ${file.absolutePath}")
                        // Если файл не существует, показываем плейсхолдер и выходим
                        binding.avatarImage.setImageResource(R.drawable.ic_person_placeholder)
                        return
                    }
                }
                
                val uri = if (avatarUri.startsWith("http")) {
                    // Если это полный URL, используем его напрямую
                    Uri.parse(avatarUri)
                } else if (avatarUri.startsWith("/")) {
                    // Если это относительный путь, добавляем базовый URL сервера
                    val prefs = requireActivity().getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
                    val serverUrl = prefs.getString("SERVER_URL", "http://10.0.2.2:3000")
                    Uri.parse("$serverUrl$avatarUri")
                } else {
                    // Иначе просто парсим URI
                    Uri.parse(avatarUri)
                }
                
                Log.d("SettingsFragment", "Загружаем аватарку из URI: $uri")
                
                // Очищаем кеш Glide для этого URI
                Glide.with(this).clear(binding.avatarImage)
                
                // Используем Glide без RequestListener, отключая кеширование для HTTP URLs
                Glide.with(this)
                    .load(uri)
                    .error(R.drawable.ic_person_placeholder)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .circleCrop()
                    .into(binding.avatarImage)
                    
                // Добавляем обработчик клика по аватарке
                binding.avatarImage.setOnClickListener {
                    openAvatarFullScreen(uri)
                }
                
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Ошибка при загрузке аватарки из SharedPreferences", e)
                binding.avatarImage.setImageResource(R.drawable.ic_person_placeholder)
            }
        } else {
            Log.d("SettingsFragment", "Аватарка не найдена в SharedPreferences, устанавливаем плейсхолдер")
            binding.avatarImage.setImageResource(R.drawable.ic_person_placeholder)
        }
    }
    
    // Новый метод для открытия аватарки на полэкрана
    private fun openAvatarFullScreen(avatarUri: Uri) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        
        val binding = DialogAvatarPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(binding.root)
        
        val window = dialog.window
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER)
        
        // Очищаем кеш Glide для этого URI
        Glide.with(this).clear(binding.avatarFullImage)
        
        Glide.with(this)
            .load(avatarUri)
            .error(R.drawable.ic_person_placeholder)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(binding.avatarFullImage)
            
        binding.closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
}