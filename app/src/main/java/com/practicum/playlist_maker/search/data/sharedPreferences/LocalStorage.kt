package com.practicum.playlist_maker.search.data.sharedPreferences

import android.content.SharedPreferences

class LocalStorage(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val KEY_SEARCH_HISTORY = "search_history"
    }

    fun getSearchHistory(): List<String> {
        val history = sharedPreferences.getString(KEY_SEARCH_HISTORY, "") ?: ""
        return if (history.isEmpty()) {
            emptyList()
        } else {
            history.split(",")
        }
    }

    fun saveSearchQuery(query: String) {
        val history = getSearchHistory().toMutableList()
        if (!history.contains(query)) {
            history.add(query)
            sharedPreferences.edit().putString(KEY_SEARCH_HISTORY, history.joinToString(",")).apply()
        }
    }

    fun clearHistory() {
        sharedPreferences.edit().remove(KEY_SEARCH_HISTORY).apply()
    }
}
