package com.practicum.playlist_maker.marchAuto.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.marchAuto.adapters.ActivityHistoryAdapter
import com.practicum.playlist_maker.marchAuto.models.ActivityHistoryItem
import com.practicum.playlist_maker.mediaLibrary.ActivityHistoryService
import com.practicum.playlist_maker.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HistoryFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ActivityHistoryAdapter
    private lateinit var historyService: ActivityHistoryService
    private var autoRefreshRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private val AUTO_REFRESH_DELAY_MS = 5000L // 5 секунд

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ActivityHistoryAdapter()
        recyclerView.adapter = adapter

        // Используем сервис с правильным URL
        historyService = RetrofitClient.createAuthenticatedService(ActivityHistoryService::class.java, requireContext())
        
        // Обновляем данные сразу и настраиваем регулярное обновление
        loadActivityHistory()
        startAutoRefresh()
    }
    
    override fun onResume() {
        super.onResume()
        // Обновляем данные при возвращении к фрагменту
        loadActivityHistory()
        // Запускаем автоматическое обновление
        startAutoRefresh()
    }
    
    override fun onPause() {
        super.onPause()
        // Останавливаем автоматическое обновление
        stopAutoRefresh()
    }

    private fun setupAutoRefresh() {
        autoRefreshRunnable = Runnable {
            loadActivityHistory()
            startAutoRefresh() // Планируем следующее обновление
        }
    }
    
    private fun startAutoRefresh() {
        autoRefreshRunnable?.let {
            handler.removeCallbacks(it) // Удаляем предыдущее обновление, если есть
            handler.postDelayed(it, AUTO_REFRESH_DELAY_MS)
        }
    }
    
    private fun stopAutoRefresh() {
        autoRefreshRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    private fun loadActivityHistory() {
        historyService.getHistory().enqueue(object : Callback<List<ActivityHistoryItem>> {
            override fun onResponse(
                call: Call<List<ActivityHistoryItem>>,
                response: Response<List<ActivityHistoryItem>>
            ) {
                if (response.isSuccessful) {
                    response.body()?.let { items ->
                        if (items.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        } else {
                            tvEmpty.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            adapter.updateItems(items)
                        }
                    }
                } else {
                    tvEmpty.text = "Ошибка загрузки: ${response.code()}"
                    tvEmpty.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
            }

            override fun onFailure(call: Call<List<ActivityHistoryItem>>, t: Throwable) {
                tvEmpty.text = "Ошибка загрузки: ${t.message}"
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        })
    }

    companion object {
        fun newInstance() = HistoryFragment()
    }
} 