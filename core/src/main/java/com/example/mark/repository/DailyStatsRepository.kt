package com.example.mark.repository

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * Stores daily health statistics like step counts for comparison.
 */
class DailyStatsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("daily_stats", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun saveSteps(count: Int) {
        val today = dateFormat.format(Date())
        prefs.edit().putInt("steps_$today", count).apply()
    }

    fun getStepsForDate(date: Date): Int {
        val dateStr = dateFormat.format(date)
        return prefs.getInt("steps_$dateStr", -1)
    }

    fun getYesterdaySteps(): Int {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return getStepsForDate(cal.time)
    }

    companion object {
        @Volatile private var instance: DailyStatsRepository? = null
        fun getInstance(context: Context): DailyStatsRepository =
            instance ?: synchronized(this) {
                instance ?: DailyStatsRepository(context.applicationContext).also { instance = it }
            }
    }
}
