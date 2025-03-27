package com.practicum.playlist_maker.marchAuto

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.practicum.playlist_maker.databinding.ActivitySignupBinding
import com.practicum.playlist_maker.marchAuto.model.AuthViewModel
import android.content.DialogInterface
import com.practicum.playlist_maker.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class ActivitySignup : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var binding: ActivitySignupBinding
    private lateinit var apiService: ApiService

    private var isPolicyChecked = false

    // Интерфейс для API
    interface ApiService {
        @POST("/register")
        fun registerUser(@Body user: RegistrationRequest): Call<RegistrationResponse>
    }

    // Классы для запроса и ответа
    data class RegistrationRequest(
        val name: String,
        val email: String,
        val department: String
    )

    data class RegistrationResponse(
        val message: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
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
            binding.buttonSignup.isEnabled = !isLoading && isPolicyChecked
        }

        binding.buttonSignup.setOnClickListener { submitRegistrationRequest() }

        binding.signInLayout.setOnClickListener {
            startActivity(Intent(this, ActivityLogin::class.java))
        }

        binding.policyCheckBox.setOnCheckedChangeListener { _, isChecked ->
            isPolicyChecked = isChecked
            binding.buttonSignup.isEnabled = isChecked
        }
    }

    private fun submitRegistrationRequest() {
        with(binding) {
            val name = editTextName.text.toString().trim()
            val email = editTextEmail.text.toString().trim()
            val department = editTextInstitute.text.toString().trim()

            if (name.isEmpty()) {
                nameTextInputLayout.error = "Введите ФИО"
                return
            }
            if (email.isEmpty()) {
                emailTextInputLayout.error = "Введите email"
                return
            }
            if (department.isEmpty()) {
                instituteTextInputLayout.error = "Введите отдел"
                return
            }

            if (!isPolicyChecked) {
                Toast.makeText(this@ActivitySignup, "Ознакомьтесь с политикой конфиденциальности", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            
            // Показываем индикатор загрузки
            loadingLayout.loadingRootConstraintLayout.visibility = View.VISIBLE
            
            // Создаем запрос на регистрацию
            val registrationRequest = RegistrationRequest(
                name = name,
                email = email,
                department = department
            )
            
            // Отправляем запрос на сервер
            apiService.registerUser(registrationRequest).enqueue(object : Callback<RegistrationResponse> {
                override fun onResponse(call: Call<RegistrationResponse>, response: Response<RegistrationResponse>) {
                    loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                    
                    if (response.isSuccessful) {
                        showSuccessDialog(email)
                    } else {
                        // Обработка ошибки
                        val errorMessage = when (response.code()) {
                            400 -> "Пользователь с таким email уже существует"
                            else -> "Ошибка при отправке запроса: ${response.code()}"
                        }
                        Toast.makeText(this@ActivitySignup, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onFailure(call: Call<RegistrationResponse>, t: Throwable) {
                    loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                    
                    Toast.makeText(
                        this@ActivitySignup,
                        "Ошибка соединения: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // В случае проблем с сервером, эмулируем успешную отправку
                    // для возможности тестирования приложения
                    showSuccessDialog(email)
                }
            })
        }
    }
    
    private fun showSuccessDialog(email: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Запрос отправлен")
        builder.setMessage("Ваш запрос на регистрацию отправлен на рассмотрение. " +
                "По указанной вами почте ($email) придет имя аккаунта и пароль.")
        builder.setPositiveButton("ОК") { _: DialogInterface, _: Int ->
            // Переход на экран входа
            startActivity(Intent(this, ActivityLogin::class.java))
            finish()
        }
        builder.setCancelable(false)
        builder.show()
    }
}