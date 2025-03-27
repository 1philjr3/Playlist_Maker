package com.practicum.playlist_maker.settings.ui.view_model

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "UserProfileViewModel"
    
    private val _userName = MutableLiveData<String>()
    private val _userEmail = MutableLiveData<String>()
    private val _userInstitute = MutableLiveData<String>()
    
    val userName: LiveData<String> get() = _userName
    val userEmail: LiveData<String> get() = _userEmail
    val userInstitute: LiveData<String> get() = _userInstitute
    
    init {
        loadUserData()
    }
    
    fun loadUserData() {
        Log.d(TAG, "Loading user data from SharedPreferences")
        val sharedPreferences = getApplication<Application>()
            .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        val name = sharedPreferences.getString("name", null)
        val email = sharedPreferences.getString("email", null)
        val institute = sharedPreferences.getString("institute", null)
        
        Log.d(TAG, "SharedPreferences data: name=$name, email=$email, institute=$institute")
        
        if (name != null && email != null) {
            _userName.value = name
            _userEmail.value = email
            _userInstitute.value = institute ?: "Отдел не указан"
            Log.d(TAG, "User data loaded successfully")
        } else {
            _userName.value = "Неизвестный пользователь"
            _userEmail.value = "Неизвестный email"
            _userInstitute.value = "Неизвестный отдел"
            Log.d(TAG, "No user data found in SharedPreferences")
        }
    }
}

