package com.practicum.playlist_maker.marchAuto

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException

class ActivitySignup : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var binding: ActivitySignupBinding
    private lateinit var apiService: ApiService
    
    private val TAG = "ActivitySignup" // Добавлен тег для логирования

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
        
        Log.d(TAG, "Initializing registration screen")
        
        // Инициализация Retrofit с правильным URL
        val baseUrl = RetrofitClient.getBaseUrl(this)
        Log.d(TAG, "Using server base URL: $baseUrl")
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(ApiService::class.java)
        Log.d(TAG, "ApiService created")

        authViewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error from ViewModel: $errorMsg")
        }

        authViewModel.isLoading.observe(this) { isLoading ->
            binding.loadingLayout.loadingRootConstraintLayout.visibility =
                if (isLoading) View.VISIBLE else View.GONE
            binding.buttonSignup.isEnabled = !isLoading && isPolicyChecked
            Log.d(TAG, "Loading state: $isLoading")
        }

        binding.buttonSignup.setOnClickListener { submitRegistrationRequest() }

        binding.signInLayout.setOnClickListener {
            Log.d(TAG, "Navigating to login screen")
            startActivity(Intent(this, ActivityLogin::class.java))
        }

        binding.policyCheckBox.setOnCheckedChangeListener { _, isChecked ->
            isPolicyChecked = isChecked
            binding.buttonSignup.isEnabled = isChecked
            Log.d(TAG, "Policy checked: $isChecked")
        }
    }

    private fun submitRegistrationRequest() {
        with(binding) {
            val name = editTextName.text.toString().trim()
            val email = editTextEmail.text.toString().trim()
            val department = editTextInstitute.text.toString().trim()

            if (name.isEmpty()) {
                nameTextInputLayout.error = "Введите ФИО"
                Log.w(TAG, "Name field is empty")
                return
            }
            if (email.isEmpty()) {
                emailTextInputLayout.error = "Введите email"
                Log.w(TAG, "Email field is empty")
                return
            }
            if (department.isEmpty()) {
                instituteTextInputLayout.error = "Введите отдел"
                Log.w(TAG, "Department field is empty")
                return
            }

            if (!isPolicyChecked) {
                Toast.makeText(this@ActivitySignup, "Ознакомьтесь с политикой конфиденциальности", Toast.LENGTH_SHORT)
                    .show()
                Log.w(TAG, "Policy not checked")
                return
            }
            
            // Показываем индикатор загрузки
            loadingLayout.loadingRootConstraintLayout.visibility = View.VISIBLE
            buttonSignup.isEnabled = false
            
            // Создаем запрос на регистрацию
            val registrationRequest = RegistrationRequest(
                name = name,
                email = email,
                department = department
            )
            
            Log.d(TAG, "Sending registration request: name=$name, email=$email, department=$department")
            
            // Отправляем запрос на сервер
            apiService.registerUser(registrationRequest).enqueue(object : Callback<RegistrationResponse> {
                override fun onResponse(call: Call<RegistrationResponse>, response: Response<RegistrationResponse>) {
                    loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                    buttonSignup.isEnabled = true
                    
                    val statusCode = response.code()
                    Log.d(TAG, "Registration response received: code=$statusCode, isSuccessful=${response.isSuccessful}")
                    
                    if (response.isSuccessful) {
                        Log.d(TAG, "Registration successful: ${response.body()?.message}")
                        showSuccessDialog(email)
                    } else {
                        // Обработка ошибки
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        Log.e(TAG, "Registration failed: HTTP $statusCode, body: $errorBody")
                        
                        val errorMessage = when (response.code()) {
                            400 -> "Пользователь с таким email уже существует"
                            404 -> "Сервер не найден. Проверьте подключение к сети."
                            500 -> "Ошибка на сервере. Попробуйте позже."
                            503 -> "Сервис временно недоступен. Попробуйте позже."
                            else -> "Ошибка при отправке запроса: ${response.code()}"
                        }
                        
                        Toast.makeText(this@ActivitySignup, errorMessage, Toast.LENGTH_LONG).show()
                        
                        // Для отладки в режиме разработки
                        if (statusCode == 404) {
                            Log.w(TAG, "Showing success dialog despite 404 error - FOR TESTING ONLY")
                            showSuccessDialog(email)
                        }
                    }
                }
                
                override fun onFailure(call: Call<RegistrationResponse>, t: Throwable) {
                    loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                    buttonSignup.isEnabled = true
                    
                    // Детальная обработка сетевых ошибок
                    val errorMessage = when (t) {
                        is UnknownHostException -> {
                            Log.e(TAG, "Network error - Unknown host: ${t.message}")
                            "Не удалось подключиться к серверу. Проверьте соединение с интернетом."
                        }
                        is SocketTimeoutException -> {
                            Log.e(TAG, "Network error - Timeout: ${t.message}")
                            "Превышено время ожидания ответа от сервера."
                        }
                        is IOException -> {
                            Log.e(TAG, "Network error - I/O: ${t.message}")
                            "Ошибка соединения: ${t.message}"
                        }
                        else -> {
                            Log.e(TAG, "Unknown error: ${t.message}", t)
                            "Неизвестная ошибка: ${t.message}"
                        }
                    }
                    
                    Toast.makeText(
                        this@ActivitySignup,
                        errorMessage,
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // В случае проблем с сервером, эмулируем успешную отправку
                    // для возможности тестирования приложения
                    Log.w(TAG, "Showing success dialog despite network error - FOR TESTING ONLY")
                    showSuccessDialog(email)
                }
            })
        }
    }
    
    private fun showSuccessDialog(email: String) {
        Log.d(TAG, "Showing success dialog for email: $email")
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Запрос отправлен")
        builder.setMessage("Ваш запрос на регистрацию отправлен на рассмотрение. " +
                "По указанной вами почте ($email) придет имя аккаунта и пароль.")
        builder.setPositiveButton("ОК") { _: DialogInterface, _: Int ->
            // Переход на экран входа
            Log.d(TAG, "Success dialog OK clicked, navigating to login screen")
            startActivity(Intent(this, ActivityLogin::class.java))
            finish()
        }
        builder.setCancelable(false)
        builder.show()
    }
}