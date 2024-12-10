package com.practicum.playlist_maker.marchAuto

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.practicum.playlist_maker.databinding.ActivityLoginBinding
import com.practicum.playlist_maker.marchAuto.model.AuthViewModel
import com.practicum.playlist_maker.pdfreader.ui.activity.home.HomeFragment
import com.practicum.playlist_maker.root.RootActivity

class ActivityLogin : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var binding: ActivityLoginBinding

    private var googleSignInLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                authViewModel.handleGoogleSignInResult(result.data!!)
            } else {
                Toast.makeText(this, "Что-то пошло не так", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authViewModel.firebaseUser.observe(this) { user ->
            user?.let {
                startActivity(Intent(this, RootActivity::class.java))
                finish()
            }
        }

        authViewModel.error.observe(this) { errorMsg ->
            val localizedError = when (errorMsg) {
                "The email address is badly formatted" -> "Некорректный формат email"
                "The password is invalid or the user does not have a password" -> "Неверный пароль"
                "There is no user record corresponding to this identifier" -> "Пользователь не найден"
                else -> "Некорректный формат email. Повторите попытку"
            }
            Toast.makeText(this, localizedError, Toast.LENGTH_SHORT).show()
        }

        authViewModel.isLoading.observe(this) { isLoading ->
            binding.loadingLayout.loadingRootConstraintLayout.visibility =
                if (isLoading) View.VISIBLE else View.GONE
            binding.logInButton.isEnabled = !isLoading
        }

        binding.singUp.setOnClickListener {
            startActivity(Intent(this, ActivitySignup::class.java))
        }

        binding.logInButton.setOnClickListener { signIn() }
    }


    private fun signIn() {
        with(binding) {
            val email = editTextEmail.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            if (email.isEmpty()) {
                emailTextInputLayout.error = "Введите email"
                return
            }
            if (password.isEmpty()) {
                passwordTextInputLayout.error = "Введите пароль"
                return
            }

            loadingLayout.loadingRootConstraintLayout.visibility = View.VISIBLE
            authViewModel.login(email, password)
        }
    }
}