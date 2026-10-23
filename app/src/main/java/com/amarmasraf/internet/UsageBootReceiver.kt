package com.amarmasraf.internet

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.util.Calendar

class UsageBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        if (prefs.getBoolean("live_usage_enabled", false)) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, UsageMonitorService::class.java)
                )
            } catch (_: Exception) {
                // Device or OS may restrict foreground service startup after boot.
            }
        }

        if (!prefs.getBoolean("notifications", true)) return

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alertIntent = Intent(context, UsageAlertReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            7001,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val firstCheck = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstCheck.timeInMillis,
            AlarmManager.INTERVAL_HOUR,
            pending
        )
    }
}
