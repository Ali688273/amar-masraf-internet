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
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusBannerType
import ir.tapsell.plus.listener.AdRequestCallback
import java.util.Calendar

data class AppUsageInfo(
    val appName: String,
    val icon: Drawable?,
    val usageBytes: Long
)

class MainActivity : AppCompatActivity() {

    private lateinit var speedDownloadTv: TextView
    private lateinit var speedUploadTv: TextView
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bgColor = Color.parseColor("#0F172A")
        val cardBgColor = Color.parseColor("#1E293B")
        val accentColor = Color.parseColor("#6366F1")

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 40, 30, 40)
        }

        // ۱. هدر بالای صفحه
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 30)
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
            text = "پایش هوشمند و تفکیکی ترافیک شبکه"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 4, 0, 0)
        }

        headerTextLayout.addView(titleTv)
        headerTextLayout.addView(subtitleTv)

        val headerIcon = CardView(this).apply {
            radius = 24f
            setCardBackgroundColor(accentColor)
            val iconTv = TextView(context).apply {
                text = "📈"
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
                text = "لطفاً دسترسی به آمار مصرف را در تنظیمات فعال کرده و برنامه را مجدداً باز کنید."
                textSize = 14f
                setTextColor(Color.parseColor("#EF4444"))
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            }
            mainLayout.addView(permNotice)
        } else {
            val statsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

            // ۲. تب‌های انتخاب شبکه
            val tabsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 25)
            }

            val sim1Card = createTabCard("سیم‌کارت ۱", "همراه اول", true)
            val sim2Card = createTabCard("سیم‌کارت ۲", "ایرانسل", false)
            val wifiCard = createTabCard("وای‌فای (Wi-Fi)", "شبکه خانگی", false)

            tabsLayout.addView(sim1Card)
            tabsLayout.addView(sim2Card)
            tabsLayout.addView(wifiCard)
            mainLayout.addView(tabsLayout)

            // ۳. کارت اصلی نمودار دایره‌ای مصرف
            val monthlyBytes = getTotalUsageForPeriod(statsManager, Calendar.MONTH)
            val monthlyGB = monthlyBytes / (1024.0 * 1024.0 * 1024.0)

            val chartCard = CardView(this).apply {
                radius = 28f
                setCardBackgroundColor(cardBgColor)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 30)
                layoutParams = params
            }

            val chartLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(35, 35, 35, 35)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val chartTitle = TextView(this).apply {
                text = "مصرف سیم‌کارت ۱ (این ماه)"
                textSize = 14f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.RIGHT
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val circleView = CircularProgressView(this, monthlyGB)
            val circleParams = LinearLayout.LayoutParams(400, 400).apply {
                setMargins(0, 30, 0, 30)
            }
            circleView.layoutParams = circleParams

            val footerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#0F172A"))
                setPadding(25, 15, 25, 15)
            }

            val pkgText = TextView(this).apply {
                text = "بسته فعال: نامحدود / آزاد"
                textSize = 12f
                setTextColor(Color.parseColor("#CBD5E1"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val usagePercentText = TextView(this).apply {
                text = String.format("%.1f GB مصرف شده", monthlyGB)
                textSize = 12f
                setTextColor(accentColor)
                typeface = Typeface.DEFAULT_BOLD
            }

            footerLayout.addView(pkgText)
            footerLayout.addView(usagePercentText)

            chartLayout.addView(chartTitle)
            chartLayout.addView(circleView)
            chartLayout.addView(footerLayout)
            chartCard.addView(chartLayout)
            mainLayout.addView(chartCard)

            // ۴. کارت آمار سرعت زنده ترافیک
            val speedCard = CardView(this).apply {
                radius = 24f
                setCardBackgroundColor(cardBgColor)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 30)
                layoutParams = params
            }

            val speedLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(30, 30, 30, 30)
            }

            val speedTitle = TextView(this).apply {
                text = "⚡ آمار سرعت و ترافیک زنده"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 20)
            }

            val speedDetailsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val downCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#0F172A"))
                setPadding(20, 20, 20, 20)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 10, 0)
                }
            }

            val downLabel = TextView(this).apply {
                text = "⬇ سرعت دانلود"
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
            }
            speedDownloadTv = TextView(this).apply {
                text = "0.0 مگابیت/ثانیه"
                textSize = 14f
                setTextColor(Color.parseColor("#10B981"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 6, 0, 0)
            }
            downCard.addView(downLabel)
            downCard.addView(speedDownloadTv)

            val upCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#0F172A"))
                setPadding(20, 20, 20, 20)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(10, 0, 0, 0)
                }
            }

            val upLabel = TextView(this).apply {
                text = "⬆ سرعت آپلود"
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
            }
            speedUploadTv = TextView(this).apply {
                text = "0.0 مگابیت/ثانیه"
                textSize = 14f
                setTextColor(Color.parseColor("#3B82F6"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 6, 0, 0)
            }
            upCard.addView(upLabel)
            upCard.addView(speedUploadTv)

            speedDetailsLayout.addView(downCard)
            speedDetailsLayout.addView(upCard)
            speedLayout.addView(speedTitle)
            speedLayout.addView(speedDetailsLayout)
            speedCard.addView(speedLayout)
            mainLayout.addView(speedCard)

            // ۵. تفکیک برنامه‌ها
            val appListTitle = TextView(this).apply {
                text = "📱 آمار مصرف به تفکیک برنامه‌ها"
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(10, 10, 10, 20)
            }
            mainLayout.addView(appListTitle)

            val appsList = getAppUsageList(statsManager)
            for (app in appsList) {
                mainLayout.addView(createAppRow(app, cardBgColor))
            }
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(mainLayout)
        }

        // ۶. جایگاه نمایش بنر تبلیغاتی تپسل در پایین صفحه
        val adContainer = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(10, 15, 10, 15)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rootLayout.addView(scrollView)
        rootLayout.addView(adContainer)
        setContentView(rootLayout)

        startSpeedMonitor()
        initAndShowTapsellAd(adContainer.id)
    }

    private fun createTabCard(title: String, subtitle: String, isSelected: Boolean): CardView {
        val card = CardView(this).apply {
            radius = 18f
            setCardBackgroundColor(
                if (isSelected) Color.parseColor("#6366F1") else Color.parseColor("#1E293B")
            )
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(6, 0, 6, 0)
            layoutParams = params
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 20, 16, 20)
            gravity = Gravity.CENTER
        }

        val tView = TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subView = TextView(this).apply {
            text = subtitle
            textSize = 10f
            setTextColor(if (isSelected) Color.parseColor("#E0E7FF") else Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 0)
        }

        layout.addView(tView)
        layout.addView(subView)
        card.addView(layout)
        return card
    }

    private fun createAppRow(app: AppUsageInfo, cardBgColor: Int): CardView {
        val card = CardView(this).apply {
            radius = 18f
            setCardBackgroundColor(cardBgColor)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 16)
            layoutParams = params
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(25, 20, 25, 20)
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(app.icon)
            layoutParams = LinearLayout.LayoutParams(80, 80)
        }

        val nameView = TextView(this).apply {
            text = app.appName
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(20, 0, 20, 0)
            }
        }

        val usageView = TextView(this).apply {
            text = formatBytes(app.usageBytes)
            textSize = 13f
            setTextColor(Color.parseColor("#818CF8"))
            typeface = Typeface.DEFAULT_BOLD
        }

        row.addView(iconView)
        row.addView(nameView)
        row.addView(usageView)
        card.addView(row)
        return card
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

                    speedDownloadTv.text = String.format("%.1f مگابیت/ثانیه", if (rxSpeed < 0) 0.0 else rxSpeed)
                    speedUploadTv.text = String.format("%.1f مگابیت/ثانیه", if (txSpeed < 0) 0.0 else txSpeed)
                }

                lastRxBytes = currentRx
                lastTxBytes = currentTx
                lastTime = currentTime

                handler.postDelayed(this, 2000)
            }
        }, 2000)
    }

    private fun getTotalUsageForPeriod(statsManager: NetworkStatsManager, timeFrame: Int): Long {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis

        when (timeFrame) {
            Calendar.DAY_OF_YEAR -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
            }
            Calendar.WEEK_OF_YEAR -> calendar.add(Calendar.DAY_OF_YEAR, -7)
            Calendar.MONTH -> calendar.add(Calendar.MONTH, -1)
        }
        val startTime = calendar.timeInMillis

        return getNetworkBytes(statsManager, ConnectivityManager.TYPE_MOBILE, startTime, endTime) +
               getNetworkBytes(statsManager, ConnectivityManager.TYPE_WIFI, startTime, endTime)
    }

    private fun getNetworkBytes(statsManager: NetworkStatsManager, networkType: Int, startTime: Long, endTime: Long): Long {
        var total = 0L
        try {
            val stats = statsManager.querySummary(networkType, null, startTime, endTime)
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

    private fun getAppUsageList(statsManager: NetworkStatsManager): List<AppUsageInfo> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val usageMap = HashMap<Int, Long>()
        val pm = packageManager

        fun collectAppStats(networkType: Int) {
            try {
                val stats = statsManager.querySummary(networkType, null, startTime, endTime)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    val bytes = bucket.rxBytes + bucket.txBytes
                    usageMap[uid] = (usageMap[uid] ?: 0L) + bytes
                }
                stats.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        collectAppStats(ConnectivityManager.TYPE_MOBILE)
        collectAppStats(ConnectivityManager.TYPE_WIFI)

        val resultList = mutableListOf<AppUsageInfo>()
        for ((uid, bytes) in usageMap) {
            if (bytes < 1024 * 1024) continue

            val packages = pm.getPackagesForUid(uid) ?: continue
            for (pkg in packages) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    resultList.add(AppUsageInfo(appName, icon, bytes))
                    break
                } catch (e: Exception) {
                    continue
                }
            }
        }

        return resultList.sortedByDescending { it.usageBytes }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
       
