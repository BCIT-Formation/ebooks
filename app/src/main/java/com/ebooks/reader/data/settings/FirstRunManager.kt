package com.ebooks.reader.data.settings

import android.content.Context
import android.content.SharedPreferences

class FirstRunManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("first_run", Context.MODE_PRIVATE)

    fun isFirstRun(): Boolean = !prefs.getBoolean(KEY_FIRST_RUN_COMPLETED, false)

    fun markFirstRunComplete() {
        prefs.edit().putBoolean(KEY_FIRST_RUN_COMPLETED, true).apply()
    }

    /**
     * Whether the bundled feed checklist still has to be offered. Tracked apart
     * from [isFirstRun] because the user picks their feeds interactively, so it
     * is only settled once they confirm or skip the picker.
     */
    fun isFeedSetupPending(): Boolean = !prefs.getBoolean(KEY_FEED_SETUP_DONE, false)

    fun markFeedSetupComplete() {
        prefs.edit().putBoolean(KEY_FEED_SETUP_DONE, true).apply()
    }

    companion object {
        private const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"
        private const val KEY_FEED_SETUP_DONE = "feed_setup_done"

        @Volatile
        private var instance: FirstRunManager? = null

        fun getInstance(context: Context): FirstRunManager {
            return instance ?: synchronized(this) {
                instance ?: FirstRunManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
