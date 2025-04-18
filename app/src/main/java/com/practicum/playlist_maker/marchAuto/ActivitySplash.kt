package com.practicum.playlist_maker.marchAuto

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.ActivitySplashBinding
import com.practicum.playlist_maker.marchAuto.model.AuthViewModel
import com.practicum.playlist_maker.pdfreader.ui.activity.home.HomeFragment
import com.practicum.playlist_maker.root.RootActivity


class ActivitySplash : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Загружаем GIF-анимацию с персонажем
        Glide.with(this)
            .asGif()
            .load(R.raw.peanut_loading)
            .into(binding.gifImageView)
        
        binding.gifImageView.visibility = android.view.View.VISIBLE
        binding.progressBar.visibility = android.view.View.GONE

        // Проверяем наличие сохраненной сессии
        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)
        val token = sharedPreferences.getString("token", null)
        
        Log.d("ActivitySplash", "Checking login status: isLoggedIn=$isLoggedIn, token=$token")

        Handler(Looper.getMainLooper()).postDelayed({
            if (isLoggedIn && !token.isNullOrEmpty()) {
                // Пользователь уже авторизован, перенаправляем на главный экран
                Log.d("ActivitySplash", "User is already logged in, redirecting to RootActivity")
                navigateToActivity(RootActivity::class.java)
            } else {
                // Проверяем Firebase авторизацию как запасной вариант
                Log.d("ActivitySplash", "No saved login session, checking Firebase auth")
                authViewModel.firebaseUser.observe(this) { user ->
                    if (user != null) {
                        navigateToActivity(RootActivity::class.java)
                    } else {
                        navigateToActivity(ActivityLogin::class.java)
                    }
                }
            }
        }, 2000) // Задержка для отображения анимации
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish()
    }
}

