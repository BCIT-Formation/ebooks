package com.ebooks.reader.data.settings

import android.content.Context
import android.content.SharedPreferences

class FirstRunManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("first_run", Context.MODE_PRIVATE)

    fun isFirstRun(): Boolean = !prefs.getBoolean(KEY_FIRST_RUN_COMPLETED, false)

    fun markFirstRunComplete() {
        prefs.edit().putBoolean(KEY_FIRST_RUN_COMPLETED, true).apply()
    }

    companion object {
        private const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"

        @Volatile
        private var instance: FirstRunManager? = null

        fun getInstance(context: Context): FirstRunManager {
            return instance ?: synchronized(this) {
                instance ?: FirstRunManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
