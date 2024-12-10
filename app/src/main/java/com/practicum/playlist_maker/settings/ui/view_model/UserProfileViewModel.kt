package com.practicum.playlist_maker.settings.ui.view_model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class UserProfileViewModel : ViewModel() {
    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> get() = _userName

    private val _userEmail = MutableLiveData<String>()
    val userEmail: LiveData<String> get() = _userEmail

    private val _userInstitute = MutableLiveData<String>()
    val userInstitute: LiveData<String> get() = _userInstitute

    fun loadUserData() {
        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            _userName.value = it.displayName ?: "Имя не задано" // Если имя отсутствует, используем заглушку
            _userEmail.value = it.email ?: "Email не задан"

            // Загрузка организации из базы данных
            FirebaseDatabase.getInstance().getReference("users/${it.uid}/institute")
                .get()
                .addOnSuccessListener { snapshot ->
                    _userInstitute.value = snapshot.value as? String ?: "Отдел не указан"
                }
                .addOnFailureListener {
                    _userInstitute.value = "Ошибка загрузки отдел"
                }
        } ?: run {
            _userName.value = "Неизвестный пользователь"
            _userEmail.value = "Неизвестный email"
            _userInstitute.value = "Неизвестный отдел"
        }
    }

}

