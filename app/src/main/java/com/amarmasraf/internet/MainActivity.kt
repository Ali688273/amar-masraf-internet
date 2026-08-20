package com.amarmasraf.internet

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }

        val title = TextView(this).apply {
            text = "📊 آمار مصرف واقعی اینترنت"
            textSize = 22f
            setTextColor(Color.parseColor("#1E293B"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        mainLayout.addView(title)

        if (!hasUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            val permNotice = TextView(this).apply {
                text = "لطفاً دسترسی به آمار مصرف (Usage Access) را در تنظیمات فعال کنید و سپس برنامه را دوباره باز کنید."
                textSize = 16f
                setTextColor(Color.parseColor("#EF4444"))
                gravity = Gravity.CENTER
            }
            mainLayout.addView(permNotice)
            setContentView(mainLayout)
            return
        }

        val statsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val daily = getUsage(statsManager, Calendar.DAY_OF_YEAR)
        val weekly = getUsage(statsManager, Calendar.WEEK_OF_YEAR)
        val monthly = getUsage(statsManager, Calendar.MONTH)

        mainLayout.addView(createCard("مصرف امروز", formatBytes(daily), "#3B82F6"))
        mainLayout.addView(createCard("مصرف این هفته", formatBytes(weekly), "#10B981"))
        mainLayout.addView(createCard("مصرف این ماه", formatBytes(monthly), "#8B5CF6"))

        val scrollView = ScrollView(this)
        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    private fun createCard(titleText: String, valueText: String, colorHex: String): CardView {
        val card = CardView(this).apply {
            radius = 24f
            cardElevation = 8f
            setCardBackgroundColor(Color.WHITE)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 35)
            layoutParams = params
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(45, 45, 45, 45)
        }

        val title = TextView(this).apply {
            text = titleText
            textSize = 15f
            setTextColor(Color.parseColor("#64748B"))
        }

        val value = TextView(this).apply {
            text = valueText
            textSize = 24f
            setTextColor(Color.parseColor(colorHex))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 10, 0, 0)
        }

        layout.addView(title)
        layout.addView(value)
        card.addView(layout)
        return card
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getUsage(networkStatsManager: NetworkStatsManager, timeFrame: Int): Long {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis

        when (timeFrame) {
            Calendar.DAY_OF_YEAR -> calendar.set(Calendar.HOUR_OF_DAY, 0)
            Calendar.WEEK_OF_YEAR -> calendar.add(Calendar.DAY_OF_YEAR, -7)
            Calendar.MONTH -> calendar.add(Calendar.MONTH, -1)
        }
        val startTime = calendar.timeInMillis

        var totalBytes = 0L
        try {
            val mobileBucket = networkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, "", startTime, endTime)
            totalBytes += mobileBucket.rxBytes + mobileBucket.txBytes

            val wifiBucket = networkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, "", startTime, endTime)
            totalBytes += wifiBucket.rxBytes + wifiBucket.txBytes
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return totalBytes
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return if (mb >= 1024) {
            String.format("%.2f گیگابایت", mb / 1024.0)
        } else {
            "$mb مگابایت"
        }
    }
}
