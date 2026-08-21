package com.amarmasraf.internet

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.util.Calendar

data class AppUsageInfo(
    val appName: String,
    val icon: Drawable?,
    val usageBytes: Long
)

enum class NetworkType { SIM1, SIM2, WIFI }
enum class TimePeriod { TODAY, WEEK, MONTH }

class MainActivity : AppCompatActivity() {

    private lateinit var speedDownloadTv: TextView
    private lateinit var speedUploadTv: TextView
    private lateinit var dynamicContainer: LinearLayout
    
    private var selectedNetwork = NetworkType.SIM1
    private var selectedPeriod = TimePeriod.TODAY

    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bgColor = Color.parseColor("#0F172A")
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 40, 30, 40)
        }

        // ۱. هدر
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 20)
        }

        val headerTextLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(this).apply {
            text = "آمار مصرف اینترنت"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitleTv = TextView(this).apply {
            text = "پایش دقیق و تفکیکی ترافیک شبکه"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 4, 0, 0)
        }

        headerTextLayout.addView(titleTv)
        headerTextLayout.addView(subtitleTv)

        val headerIcon = CardView(this).apply {
            radius = 24f
            setCardBackgroundColor(Color.parseColor("#6366F1"))
            val iconTv = TextView(context).apply {
                text = "📊"
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(20, 20, 20, 20)
            }
            addView(iconTv)
        }

        headerLayout.addView(headerTextLayout)
        headerLayout.addView(headerIcon)
        mainLayout.addView(headerLayout)

        if (!hasUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            val permNotice = TextView(this).apply {
                text = "لطفاً دسترسی به آمار مصرف (Usage Access) را در تنظیمات فعال کرده و برنامه را مجدداً باز کنید."
                textSize = 14f
                setTextColor(Color.parseColor("#EF4444"))
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            }
            mainLayout.addView(permNotice)
        } else {
            // کارت سرعت زنده
            mainLayout.addView(createSpeedCard())

            // کانتینر محتوای پویا
            dynamicContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            mainLayout.addView(dynamicContainer)
            refreshDashboard()
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(mainLayout)
        }

        rootLayout.addView(scrollView)
        setContentView(rootLayout)

        startSpeedMonitor()
    }

    private fun refreshDashboard() {
        dynamicContainer.removeAllViews()
        val cardBgColor = Color.parseColor("#1E293B")
        val statsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

        // انتخاب نوع شبکه (سیم ۱ / سیم ۲ / وای فای)
        val networkTabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 15)
        }

        networkTabs.addView(createTabCard("سیم‌کارت ۱", selectedNetwork == NetworkType.SIM1) {
            selectedNetwork = NetworkType.SIM1
            refreshDashboard()
        })
        networkTabs.addView(createTabCard("سیم‌کارت ۲", selectedNetwork == NetworkType.SIM2) {
            selectedNetwork = NetworkType.SIM2
            refreshDashboard()
        })
        networkTabs.addView(createTabCard("وای‌فای (Wi-Fi)", selectedNetwork == NetworkType.WIFI) {
            selectedNetwork = NetworkType.WIFI
            refreshDashboard()
        })
        dynamicContainer.addView(networkTabs)

        // انتخاب بازه زمانی (امروز / این هفته / این ماه)
        val periodTabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 25)
        }

        periodTabs.addView(createChipCard("امروز", selectedPeriod == TimePeriod.TODAY) {
            selectedPeriod = TimePeriod.TODAY
            refreshDashboard()
        })
        periodTabs.addView(createChipCard("این هفته", selectedPeriod == TimePeriod.WEEK) {
            selectedPeriod = TimePeriod.WEEK
            refreshDashboard()
        })
        periodTabs.addView(createChipCard("این ماه", selectedPeriod == TimePeriod.MONTH) {
            selectedPeriod = TimePeriod.MONTH
            refreshDashboard()
        })
        dynamicContainer.addView(periodTabs)

        // محاسبه مصرف
        val (startTime, endTime) = getTimeRange(selectedPeriod)
        val netTypeInt = if (selectedNetwork == NetworkType.WIFI) ConnectivityManager.TYPE_WIFI else ConnectivityManager.TYPE_MOBILE
        val subscriberId = getSubscriberIdForNetwork(selectedNetwork)

        val totalBytes = getNetworkBytes(statsManager, netTypeInt, subscriberId, startTime, endTime)
        val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)

        // نمودار مصرف
        val chartCard = CardView(this).apply {
            radius = 28f
            setCardBackgroundColor(cardBgColor)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 30)
            layoutParams = params
        }

        val chartLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val networkTitle = when(selectedNetwork) {
            NetworkType.SIM1 -> "مصرف سیم‌کارت ۱"
            NetworkType.SIM2 -> "مصرف سیم‌کارت ۲"
            NetworkType.WIFI -> "مصرف وای‌فای"
        }
        val periodTitle = when(selectedPeriod) {
            TimePeriod.TODAY -> "(امروز)"
            TimePeriod.WEEK -> "(این هفته)"
            TimePeriod.MONTH -> "(این ماه)"
        }

        val chartTitle = TextView(this).apply {
            text = "$networkTitle $periodTitle"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.RIGHT
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val circleView = CircularProgressView(this, totalGB)
        circleView.layoutParams = LinearLayout.LayoutParams(380, 380).apply {
            setMargins(0, 20, 0, 20)
        }

        chartLayout.addView(chartTitle)
        chartLayout.addView(circleView)
        chartCard.addView(chartLayout)
        dynamicContainer.addView(chartCard)

        // لیست برنامه‌ها
        val appListTitle = TextView(this).apply {
            text = "📱 مصرف برنامه‌ها در این بازه"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(10, 10, 10, 20)
        }
        dynamicContainer.addView(appListTitle)

        val appsList = getAppUsageList(statsManager, netTypeInt, subscriberId, startTime, endTime)
        if (appsList.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "هیچ مصرفی برای این بازه ثبت نشده است."
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                setPadding(0, 30, 0, 30)
            }
            dynamicContainer.addView(emptyTv)
        } else {
            for (app in appsList) {
                dynamicContainer.addView(createAppRow(app, cardBgColor))
            }
        }
    }

    private fun createTabCard(title: String, isSelected: Boolean, onClick: () -> Unit): CardView {
        return CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(if (isSelected) Color.parseColor("#6366F1") else Color.parseColor("#1E293B"))
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(6, 0, 6, 0)
            layoutParams = params
            setOnClickListener { onClick() }

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 18, 12, 18)
                gravity = Gravity.CENTER
            }

            val tView = TextView(context).apply {
                text = title
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            layout.addView(tView)
            addView(layout)
        }
    }

    private fun createChipCard(title: String, isSelected: Boolean, onClick: () -> Unit): CardView {
        return CardView(this).apply {
            radius = 20f
            setCardBackgroundColor(if (isSelected) Color.parseColor("#3B82F6") else Color.parseColor("#0F172A"))
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(6, 0, 6, 0)
            layoutParams = params
            setOnClickListener { onClick() }

            val tView = TextView(context).apply {
                text = title
                textSize = 11f
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                setPadding(8, 12, 8, 12)
            }
            addView(tView)
        }
    }

    private fun createSpeedCard(): CardView {
        val cardBgColor = Color.parseColor("#1E293B")
        val speedCard = CardView(this).apply {
            radius = 24f
            setCardBackgroundColor(cardBgColor)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 20)
            layoutParams = params
        }

        val speedLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 25, 25, 25)
        }

        val speedTitle = TextView(this).apply {
            text = "⚡ سرعت زنده شبکه"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 15)
        }

        val speedDetailsLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val downCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(15, 15, 15, 15)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8, 0) }
        }
        val downLabel = TextView(this).apply { text = "⬇ دانلود"; textSize = 10f; setTextColor(Color.parseColor("#94A3B8")) }
        speedDownloadTv = TextView(this).apply { text = "0.0 Mb/s"; textSize = 13f; setTextColor(Color.parseColor("#10B981")); typeface = Typeface.DEFAULT_BOLD }
        downCard.addView(downLabel); downCard.addView(speedDownloadTv)

        val upCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(15, 15, 15, 15)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 0, 0, 0) }
        }
        val upLabel = TextView(this).apply { text = "⬆ آپلود"; textSize = 10f; setTextColor(Color.parseColor("#94A3B8")) }
        speedUploadTv = TextView(this).apply { text = "0.0 Mb/s"; textSize = 13f; setTextColor(Color.parseColor("#3B82F6")); typeface = Typeface.DEFAULT_BOLD }
        upCard.addView(upLabel); upCard.addView(speedUploadTv)

        speedDetailsLayout.addView(downCard)
        speedDetailsLayout.addView(upCard)
        speedLayout.addView(speedTitle)
        speedLayout.addView(speedDetailsLayout)
        speedCard.addView(speedLayout)
        return speedCard
    }

    private fun createAppRow(app: AppUsageInfo, cardBgColor: Int): CardView {
        val card = CardView(this).apply {
            radius = 18f
            setCardBackgroundColor(cardBgColor)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 14)
            layoutParams = params
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 20, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(app.icon ?: packageManager.defaultActivityIcon)
            layoutParams = LinearLayout.LayoutParams(70, 70)
        }

        val nameView = TextView(this).apply {
            text = app.appName
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(16, 0, 16, 0) }
        }

        val usageView = TextView(this).apply {
            text = formatBytes(app.usageBytes)
            textSize = 12f
            setTextColor(Color.parseColor("#818CF8"))
            typeface = Typeface.DEFAULT_BOLD
        }

        row.addView(iconView)
        row.addView(nameView)
        row.addView(usageView)
        card.addView(row)
        return card
    }

    private fun getTimeRange(period: TimePeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis

        when (period) {
            TimePeriod.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.WEEK -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
            }
            TimePeriod.MONTH -> {
                calendar.add(Calendar.MONTH, -1)
            }
        }
        return Pair(calendar.timeInMillis, endTime)
    }

    private fun getSubscriberIdForNetwork(network: NetworkType): String? {
        if (network == NetworkType.WIFI) return null
        return try {
            val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val list = sm.activeSubscriptionInfoList
            if (list != null) {
                val index = if (network == NetworkType.SIM1) 0 else 1
                if (list.size > index) {
                    list[index].subscriptionId.toString()
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getNetworkBytes(statsManager: NetworkStatsManager, networkType: Int, subscriberId: String?, startTime: Long, endTime: Long): Long {
        var total = 0L
        try {
            val stats = statsManager.querySummary(networkType, subscriberId, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                total += bucket.rxBytes + bucket.txBytes
            }
            stats.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return total
    }

    private fun getAppUsageList(statsManager: NetworkStatsManager, networkType: Int, subscriberId: String?, startTime: Long, endTime: Long): List<AppUsageInfo> {
        val usageMap = HashMap<String, Long>()
        val pm = packageManager

        try {
            val stats = statsManager.querySummary(networkType, subscriberId, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val bytes = bucket.rxBytes + bucket.txBytes
                if (bytes < 100 * 1024) continue // نادیده گرفتن مصرف زیر ۱۰۰ کیلوبایت

                val packages = pm.getPackagesForUid(uid)
                if (packages != null && packages.isNotEmpty()) {
                    val pkgName = packages[0]
                    usageMap[pkgName] = (usageMap[pkgName] ?: 0L) + bytes
                }
            }
            stats.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val resultList = mutableListOf<AppUsageInfo>()
        for ((pkgName, bytes) in usageMap) {
            try {
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                resultList.add(AppUsageInfo(appName, icon, bytes))
            } catch (e: Exception) {
                continue
            }
        }

        return resultList.sortedByDescending { it.usageBytes }
    }

    private fun startSpeedMonitor() {
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()

        handler.postDelayed(object : Runnable {
            override fun run() {
                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()
                val currentTime = System.currentTimeMillis()

                val timeDiff = (currentTime - lastTime) / 1000.0
                if (timeDiff > 0) {
                    val rxSpeed = ((currentRx - lastRxBytes) * 8 / timeDiff) / (1024 * 1024)
                    val txSpeed = ((currentTx - lastTxBytes) * 8 / timeDiff) / (1024 * 1024)

                    speedDownloadTv.text = String.format("%.1f Mb/s", if (rxSpeed < 0) 0.0 else rxSpeed)
                    speedUploadTv.text = String.format("%.1f Mb/s", if (txSpeed < 0) 0.0 else txSpeed)
                }

                lastRxBytes = currentRx
                lastTxBytes = currentTx
                lastTime = currentTime

                handler.postDelayed(this, 2000)
            }
        }, 2000)
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

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return if (mb >= 1024) {
            String.format("%.2f گیگابایت", mb / 1024.0)
        } else {
            "$mb مگابایت"
        }
    }

    class CircularProgressView(context: Context, private val gbValue: Double) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            style = Paint.Style.STROKE
            strokeWidth = 22f
        }

        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6366F1")
            style = Paint.Style.STROKE
            strokeWidth = 24f
            strokeCap = Paint.Cap.ROUND
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 50f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val radius = (Math.min(w, h) / 2) - 25f
            val rect = RectF(w / 2 - radius, h / 2 - radius, w / 2 + radius, h / 2 + radius)

            canvas.drawArc(rect, 135f, 270f, false, bgPaint)
            val sweep = Math.min((gbValue / 10.0) * 270f, 270.0).toFloat()
            canvas.drawArc(rect, 135f, if (sweep < 5f && gbValue > 0) 10f else sweep, false, progressPaint)

            canvas.drawText(if (gbValue < 0.1) String.format("%.2f", gbValue) else String.format("%.1f", gbValue), w / 2, h / 2 + 8f, textPaint)
            canvas.drawText("گیگابایت", w / 2, h / 2 + 48f, subTextPaint)
        }
    }
}
