package com.practicum.playlist_maker.mediaLibrary

import com.practicum.playlist_maker.marchAuto.models.ActivityHistoryItem
import retrofit2.Call
import retrofit2.http.GET

interface ActivityHistoryService {
    @GET("activity-history")
    fun getHistory(): Call<List<ActivityHistoryItem>>
} 