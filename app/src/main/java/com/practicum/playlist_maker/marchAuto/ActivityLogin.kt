package com.practicum.playlist_maker.marchAuto

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.practicum.playlist_maker.databinding.ActivityLoginBinding
import com.practicum.playlist_maker.marchAuto.model.AuthViewModel
import com.practicum.playlist_maker.network.RetrofitClient
import com.practicum.playlist_maker.root.RootActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class ActivityLogin : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var binding: ActivityLoginBinding
    private lateinit var apiService: ApiService

    // Интерфейс для API
    interface ApiService {
        @POST("/login")
        fun loginUser(@Body loginRequest: LoginRequest): Call<LoginResponse>
    }

    // Классы для запроса и ответа
    data class LoginRequest(
        val email: String,
        val password: String
    )

    data class LoginResponse(
        val token: String,
        val user: UserData
    )

    data class UserData(
        val email: String,
        val name: String,
        val department: String,
        val isAdmin: Boolean,
        val avatarUrl: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация Retrofit с правильным URL
        val retrofit = Retrofit.Builder()
            .baseUrl(RetrofitClient.getBaseUrl(this))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(ApiService::class.java)

        authViewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }

        authViewModel.isLoading.observe(this) { isLoading ->
            binding.loadingLayout.loadingRootConstraintLayout.visibility =
                if (isLoading) View.VISIBLE else View.GONE
            binding.logInButton.isEnabled = !isLoading
        }

        binding.logInButton.setOnClickListener { login() }

        binding.signUpLayout.setOnClickListener {
            startActivity(Intent(this, ActivitySignup::class.java))
        }
    }

    private fun login() {
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

            // Создаем запрос на авторизацию
            val loginRequest = LoginRequest(
                email = email,
                password = password
            )

            // Отправляем запрос на сервер
            apiService.loginUser(loginRequest).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    loadingLayout.loadingRootConstraintLayout.visibility = View.GONE

                    if (response.isSuccessful) {
                        // Сохраняем данные пользователя в SharedPreferences
                        response.body()?.let { loginResponse ->
                            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
                            with(sharedPreferences.edit()) {
                                putString("email", loginResponse.user.email)
                                putString("name", loginResponse.user.name)
                                putString("institute", loginResponse.user.department)
                                putString("token", loginResponse.token)
                                putBoolean("is_logged_in", true)
                                putString("uid", loginResponse.user.email) // Добавляем UID для будущих запросов
                                
                                // Сохраняем URL аватара, если он есть
                                loginResponse.user.avatarUrl?.let { avatarUrl ->
                                    // Проверяем, что URL не пустой
                                    if(avatarUrl.isNotEmpty()) {
                                        Log.d("ActivityLogin", "Сохраняем URL аватара: $avatarUrl")
                                        putString("avatar_uri", avatarUrl)
                                        // Проверяем сохранение
                                        apply()
                                        val savedAvatarUrl = sharedPreferences.getString("avatar_uri", null)
                                        Log.d("ActivityLogin", "Проверка сохраненного URL аватара: $savedAvatarUrl")
                                    } else {
                                        Log.d("ActivityLogin", "URL аватара пустой, не сохраняем")
                                    }
                                } ?: Log.d("ActivityLogin", "URL аватара отсутствует в ответе сервера")
                                
                                // Финальное применение всех изменений
                                apply()
                            }
                            
                            // Логируем сохраненные данные
                            Log.d("ActivityLogin", "Данные пользователя сохранены: email=${loginResponse.user.email}, " +
                                    "name=${loginResponse.user.name}, department=${loginResponse.user.department}, " +
                                    "avatarUrl=${loginResponse.user.avatarUrl}")
                        }

                        // Переходим на главный экран
                        startActivity(Intent(this@ActivityLogin, RootActivity::class.java))
                        finish()
                    } else {
                        // Обработка ошибки
                        val errorMessage = when (response.code()) {
                            401 -> "Неверный email или пароль"
                            403 -> "Аккаунт ожидает подтверждения"
                            else -> "Ошибка при входе: ${response.code()}"
                        }
                        Toast.makeText(this@ActivityLogin, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                    
                    Toast.makeText(
                        this@ActivityLogin,
                        "Ошибка соединения: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }
}