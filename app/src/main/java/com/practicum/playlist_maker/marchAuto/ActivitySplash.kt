package com.practicum.playlist_maker.marchAuto

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        authViewModel.firebaseUser.observe(this) { user ->
            if (user != null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    navigateToActivity(RootActivity::class.java)
                }, 2500) // Увеличиваем задержку для лучшего отображения анимации
            } else {
                navigateToActivity(ActivityLogin::class.java)
            }
        }
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish()
    }
}

