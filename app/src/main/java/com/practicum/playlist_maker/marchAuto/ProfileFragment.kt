package com.practicum.playlist_maker.marchAuto

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.FragmentProfileBinding
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.regex.Pattern

class ProfileFragment : Fragment() {

    private val TAG = "ProfileFragment"
    private val IP_ADDRESS_PATTERN = Pattern.compile(
        "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$"
    )

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: UserProfileViewModel
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Создаем ViewModel с фабрикой
        viewModel = ViewModelProvider(
            this,
            UserProfileViewModelFactory(requireContext().getSharedPreferences("profile", Context.MODE_PRIVATE))
        )[UserProfileViewModel::class.java]

        setupImagePicker()
        setupButtons()
        observeProfileData()
    }

    private fun setupImagePicker() {
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val selectedImageUri = result.data?.data
                
                if (selectedImageUri != null) {
                    Log.d(TAG, "Выбрано изображение: $selectedImageUri")
                    
                    // Загружаем изображение для отображения
                    loadLocalAvatar(selectedImageUri)
                    
                    // Обновляем профиль с новым URI
                    val name = binding.etProfileName.text.toString()
                    val institute = binding.etProfileInstitute.text.toString()
                    viewModel.updateProfile(name, institute, selectedImageUri.toString())
                } else {
                    Log.e(TAG, "Не удалось получить URI изображения")
                    Toast.makeText(requireContext(), "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupButtons() {
        // Настраиваем кнопку выбора изображения
        binding.ivProfileAvatar.setOnClickListener {
            selectImageFromGallery()
        }

        // Настраиваем кнопку сохранения
        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etProfileName.text.toString()
            val institute = binding.etProfileInstitute.text.toString()
            
            if (name.isNotEmpty()) {
                viewModel.updateProfile(name, institute, null)
                Toast.makeText(requireContext(), "Профиль сохранен", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
            }
        }

        // Настраиваем кнопку очистки профиля
        binding.btnClearProfile.setOnClickListener {
            binding.etProfileName.setText("")
            binding.etProfileInstitute.setText("")
            setDefaultAvatar()
            viewModel.updateProfile("", "", null)
            Toast.makeText(requireContext(), "Профиль очищен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeProfileData() {
        viewModel.profileData.observe(viewLifecycleOwner) { profileData ->
            // Обновляем поля ввода
            binding.etProfileName.setText(profileData.name)
            binding.etProfileInstitute.setText(profileData.institute)
            
            // Обновляем аватар
            profileData.avatarUri?.let { uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    loadAvatar(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при парсинге URI аватара", e)
                    setDefaultAvatar()
                }
            } ?: setDefaultAvatar()
            
            Log.d(TAG, "Данные профиля обновлены: $profileData")
        }
    }

    private fun loadAvatar(avatarUri: Uri?) {
        Log.d(TAG, "Загрузка аватара с URI: $avatarUri")
        
        if (avatarUri == null) {
            Log.d(TAG, "URI аватара отсутствует, установка аватара по умолчанию")
            setDefaultAvatar()
            return
        }
        
        // Очищаем кэш Glide для аватара
        Glide.with(this).clear(binding.ivProfileAvatar)
        
        // Определяем тип URI (локальный или удаленный)
        when (avatarUri.scheme) {
            "content", "file" -> loadLocalAvatar(avatarUri)
            "http", "https" -> loadRemoteAvatar(avatarUri)
            else -> {
                Log.e(TAG, "Неподдерживаемая схема URI: ${avatarUri.scheme}")
                setDefaultAvatar()
                Toast.makeText(requireContext(), "Неподдерживаемый формат изображения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadLocalAvatar(uri: Uri) {
        Log.d(TAG, "Загрузка локального аватара: $uri")
        
        try {
            // Проверяем существование файла
            val path = uri.path
            if (path != null && uri.scheme == "file") {
                val file = File(path)
                if (!file.exists()) {
                    Log.e(TAG, "Файл не существует: $path")
                    setDefaultAvatar()
                    return
                }
            }
            
            // Загружаем изображение с помощью Glide
            Glide.with(this)
                .load(uri)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .centerCrop()
                .into(binding.ivProfileAvatar)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при загрузке локального аватара", e)
            setDefaultAvatar()
        }
    }

    private fun loadRemoteAvatar(uri: Uri) {
        val url = uri.toString()
        Log.d(TAG, "Загрузка удаленного аватара: $url")
        
        if (!isValidUrl(url)) {
            Log.e(TAG, "Невалидный URL аватара: $url")
            setDefaultAvatar()
            Toast.makeText(
                requireContext(), 
                "Некорректный URL аватара", 
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        try {
            // Очищаем кеш для этого изображения
            Glide.with(this).clear(binding.ivProfileAvatar)
            
            // Загружаем изображение
            Glide.with(this)
                .load(url)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .centerCrop()
                .into(binding.ivProfileAvatar)
                
            Log.d(TAG, "Запрос на загрузку удаленного аватара отправлен: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при загрузке удаленного аватара: $url", e)
            setDefaultAvatar()
        }
    }

    private fun isValidUrl(urlString: String): Boolean {
        try {
            val uri = Uri.parse(urlString)
            
            // Проверяем схему
            val scheme = uri.scheme
            if (scheme == null || (scheme != "http" && scheme != "https")) {
                Log.d(TAG, "Неподдерживаемая схема: $scheme")
                return false
            }
            
            // Проверяем хост
            val host = uri.host
            if (host.isNullOrEmpty()) {
                Log.d(TAG, "URL без хоста: $urlString")
                return false
            }
            
            // Проверяем, является ли хост IP-адресом
            if (IP_ADDRESS_PATTERN.matcher(host).matches()) {
                Log.d(TAG, "URL содержит валидный IP-адрес: $host")
                return true
            }
            
            // Проверяем домен (должен содержать хотя бы одну точку)
            if (!host.contains(".")) {
                Log.d(TAG, "Невалидный домен: $host")
                return false
            }
            
            Log.d(TAG, "URL прошел валидацию: $urlString")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при проверке URL: $urlString", e)
            return false
        }
    }

    private fun setDefaultAvatar() {
        Log.d(TAG, "Установка аватара по умолчанию")
        
        try {
            // Устанавливаем изображение по умолчанию
            binding.ivProfileAvatar.setImageResource(R.drawable.ic_person_placeholder)
            
            // Добавляем цветовой фильтр для визуального выделения
            val color = ContextCompat.getColor(requireContext(), R.color.gray)
            binding.ivProfileAvatar.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при установке аватара по умолчанию", e)
        }
    }

    private fun selectImageFromGallery() {
        try {
            Log.d(TAG, "Открытие галереи для выбора изображения")
            
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            
            pickImageLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при открытии галереи", e)
            Toast.makeText(requireContext(), "Не удалось открыть галерею", Toast.LENGTH_SHORT).show()
        }
    }
} 