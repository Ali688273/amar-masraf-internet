package com.amarmasraf.internet

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import java.util.Calendar
import java.util.Locale

object UsageFeatureUtils {
    data class Period(val start: Long, val end: Long)

    fun queryTotal(context: Context, type: Int, start: Long, end: Long): Long {
        return try {
            val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val stats = manager.querySummary(type, null, start, end)
            val bucket = NetworkStats.Bucket()
            var total = 0L
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                total += bucket.rxBytes + bucket.txBytes
            }
            stats.close()
            total
        } catch (_: Exception) { 0L }
    }

    fun billingPeriod(context: Context): Period {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val startDay = prefs.getInt("billing_start_day", 1).coerceIn(1, 28)
        val now = Calendar.getInstance()
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, startDay)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (after(now)) add(Calendar.MONTH, -1)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return Period(start.timeInMillis, minOf(end.timeInMillis, System.currentTimeMillis()))
    }

    fun today(): Period {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return Period(c.timeInMillis, System.currentTimeMillis())
    }

    fun formatBytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L)
        val mb = b / 1024.0 / 1024.0
        val gb = b / 1024.0 / 1024.0 / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        }
    }

    fun totalMobileWifi(context: Context, p: Period): Long =
        queryTotal(context, ConnectivityManager.TYPE_MOBILE, p.start, p.end) +
        queryTotal(context, ConnectivityManager.TYPE_WIFI, p.start, p.end)
}
