package com.practicum.playlist_maker.mediaLibrary.ui.fragment

import android.os.Bundle
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HistoryFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ActivityHistoryAdapter
    private lateinit var historyService: ActivityHistoryService

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

        val retrofit = Retrofit.Builder()
            .baseUrl("http://172.17.3.10:3000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        historyService = retrofit.create(ActivityHistoryService::class.java)
        loadHistory()
    }

    private fun loadHistory() {
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
                }
            }

            override fun onFailure(call: Call<List<ActivityHistoryItem>>, t: Throwable) {
                tvEmpty.text = "Ошибка загрузки истории"
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        })
    }

    companion object {
        fun newInstance() = HistoryFragment()
    }
} 