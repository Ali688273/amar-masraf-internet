package com.amarmasraf.internet

import android.app.NotificationManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.app.NotificationCompat
import java.util.Calendar

class UsageAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications", true)) return
        val limitGb = prefs.getFloat("monthly_limit_gb", 10f)
        val warning = prefs.getInt("warning_percent", 80)
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val (start, end) = monthRange()
        val total = query(manager, ConnectivityManager.TYPE_MOBILE, start, end) + query(manager, ConnectivityManager.TYPE_WIFI, start, end)
        val percent = total.toDouble() / (limitGb * 1024.0 * 1024.0 * 1024.0) * 100.0
        if (percent >= warning) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = "مصرف این ماه ${format(total)} است؛ حدود ${percent.toInt()}٪ از سقف ${limitGb.toInt()} گیگابایت."
            val notification = NotificationCompat.Builder(context, "usage_alerts")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("هشدار مصرف اینترنت")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true).build()
            nm.notify(7002, notification)
        }
    }
    private fun monthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance(); val end = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return Pair(cal.timeInMillis, end)
    }
    private fun query(manager: NetworkStatsManager, type: Int, start: Long, end: Long): Long {
        return try {
            val stats = manager.querySummary(type, null, start, end); val bucket = NetworkStats.Bucket(); var total = 0L
            while (stats.hasNextBucket()) { stats.getNextBucket(bucket); total += bucket.rxBytes + bucket.txBytes }; stats.close(); total
        } catch (_: Exception) { 0L }
    }
    private fun format(bytes: Long): String {
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        return if (gb >= 1) String.format("%.2f GB", gb) else String.format("%.0f MB", bytes / 1024.0 / 1024.0)
    }
}