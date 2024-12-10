package com.practicum.playlist_maker.marchAuto

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.practicum.playlist_maker.databinding.ActivitySignupBinding
import com.practicum.playlist_maker.marchAuto.model.AuthViewModel
import com.practicum.playlist_maker.marchAuto.model.ModelUser
import com.practicum.playlist_maker.pdfreader.ui.activity.home.HomeFragment
import com.practicum.playlist_maker.root.RootActivity

class ActivitySignup : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var binding: ActivitySignupBinding

    private var isPolicyChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authViewModel.firebaseUser.observe(this) { user ->
            user?.let {
                startActivity(Intent(this, RootActivity::class.java))
                finish()
            }
        }

        authViewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }

        authViewModel.isLoading.observe(this) { isLoading ->
            binding.loadingLayout.loadingRootConstraintLayout.visibility =
                if (isLoading) View.VISIBLE else View.GONE
            binding.buttonSignup.isEnabled = !isLoading
        }

        binding.buttonSignup.setOnClickListener { signUp() }

        binding.signInLayout.setOnClickListener {
            startActivity(Intent(this, ActivityLogin::class.java))
        }

        binding.policyCheckBox.setOnCheckedChangeListener { _, isChecked ->
            isPolicyChecked = isChecked
            binding.buttonSignup.isEnabled = isChecked
        }
    }

    private fun signUp() {
        with(binding) {
            val name = editTextName.text.toString().trim()
            val email = editTextEmail.text.toString().trim()
            val institute = editTextInstitute.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            if (name.isEmpty()) {
                nameTextInputLayout.error = "Имя"
                return
            }
            if (email.isEmpty()) {
                emailTextInputLayout.error = "email"
                return
            }
            if (institute.isEmpty()) {
                instituteTextInputLayout.error = "Отдел"
                return
            }
            if (password.isEmpty()) {
                passwordTextInputLayout.error = "Пароль"
                return
            }

            if (!isPolicyChecked) {
                Toast.makeText(this@ActivitySignup, "Ознакомьтесь с политикой конфиденциальности", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            loadingLayout.loadingRootConstraintLayout.visibility = View.VISIBLE
            ModelUser(uid = "", email, name, institute).let {
                authViewModel.register(it, password)
            }
        }
    }
}