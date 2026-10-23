package com.amarmasraf.internet

import android.app.AlertDialog
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusBannerType
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.AdShowListener
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusErrorModel
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val TapsellKey = "skdkrkqgpljebkgtnhnksnjsogjohskdglotogpbbkgdrqscslsbfshkgnbfdgrdglkffr"
    private val BannerZoneId = "66f7f24c084f7063d8091d37"

    private lateinit var downloadSpeedTv: TextView
    private lateinit var uploadSpeedTv: TextView
    private lateinit var contentLayout: LinearLayout
    private lateinit var bannerContainer: FrameLayout

    private var currentTab = 0 // 0: Mobile, 1: WiFi
    private var currentPeriod = 0 // 0: Today, 1: Week, 2: Month

    private var lastRx: Long = 0
    private var lastTx: Long = 0
    private var lastTime: Long = 0
    private val speedHandler = Handler(Looper.getMainLooper())
    private var speedRunnable: Runnable? = null
    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private val warningPercent get() = prefs.getInt("warning_percent", 80)
    private val monthlyLimitGb get() = prefs.getFloat("monthly_limit_gb", 10f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.color(this@MainActivity, "background"))
        }

        val scrollContainer = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 40, 30, 40)
        }

        // App Header
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 30)
        }

        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(this).apply {
            text = "آمار مصرف اینترنت"
            textSize = 22f
            setTextColor(ThemeManager.color(this@MainActivity, "text"))
            typeface = Typeface.DEFAULT_BOLD
        }

        val subTitleTv = TextView(this).apply {
            text = "مدیریت و پایش مصرف داده"
            textSize = 13f
            setTextColor(ThemeManager.color(this@MainActivity, "muted"))
            setPadding(0, 4, 0, 0)
        }

        titleLayout.addView(titleTv)
        titleLayout.addView(subTitleTv)
        headerLayout.addView(titleLayout)
        mainLayout.addView(headerLayout)

        if (!checkUsageStatsPermission()) {
            val permissionNotice = TextView(this).apply {
                text = "لطفاً دسترسی به آمار مصرف (Usage Access) را برای این برنامه فعال کنید."
                textSize = 15f
                setTextColor(ThemeManager.color(this@MainActivity, "danger"))
                gravity = Gravity.CENTER
                setPadding(0, 50, 0, 50)
            }
            mainLayout.addView(permissionNotice)
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            // Live Speed Card
            mainLayout.addView(createSpeedCard())

            // Dynamic Content Container
            contentLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            mainLayout.addView(contentLayout)

            refreshUI()
        }

        scrollContainer.addView(mainLayout)
        rootLayout.addView(scrollContainer)

        // Persistent bottom navigation for the app's main sections.
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(6, 8, 6, 8)
            setBackgroundColor(ThemeManager.color(this@MainActivity, "dark"))
        }
        navigation.addView(createNavigationButton("⌂\nخانه", "primary") {
            scrollContainer.smoothScrollTo(0, 0)
        })
        navigation.addView(createNavigationButton("▦\nبرنامه‌ها", "cardAlt") {
            startActivity(Intent(this@MainActivity, AppUsageActivity::class.java))
        })
        navigation.addView(createNavigationButton("▥\nنمودار", "cardAlt") {
            startActivity(Intent(this@MainActivity, ChartsActivity::class.java))
        })
        navigation.addView(createNavigationButton("⚙\nتنظیمات", "accent") {
            startActivity(Intent(this@MainActivity, AdvancedUsageActivity::class.java))
        })
        rootLayout.addView(navigation)

        // Tapsell Banner Container at bottom
        bannerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(bannerContainer)

        setContentView(rootLayout)

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        scheduleDailyCheck()
        startSpeedChecker()
        initTapsell()
    }

    private fun createNavigationButton(label: String, role: String, action: () -> Unit): CardView {
        return CardView(this).apply {
            radius = 14f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, role))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(3, 0, 3, 0) }
            setOnClickListener { action() }
            addView(TextView(context).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (role == "cardAlt") ThemeManager.color(this@MainActivity, "text") else Color.WHITE)
                setPadding(4, 9, 4, 9)
            })
        }
    }

    private fun initTapsell() {
        loadBannerAd()
    }

    private fun loadBannerAd() {
        TapsellPlus.requestStandardBannerAd(
            this,
            BannerZoneId,
            TapsellPlusBannerType.BANNER_320x50,
            object : AdRequestCallback() {
                override fun response(model: TapsellPlusAdModel) {
                    TapsellPlus.showStandardBannerAd(
                        this@MainActivity,
                        model.responseId,
                        bannerContainer,
                        object : AdShowListener() {
                            override fun onOpened(model: TapsellPlusAdModel) {}
                            override fun onError(error: TapsellPlusErrorModel) {
                                Log.e("TapsellShow", "Error: ${error.errorMessage}")
                            }
                        }
                    )
                }
            }
        )
    }

    private fun refreshUI() {
        contentLayout.removeAllViews()

        // Tabs for Mobile / WiFi
        val tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 20)
        }

        tabLayout.addView(createTabButton("سیم‌کارت", currentTab == 0) {
            currentTab = 0
            refreshUI()
        })
        tabLayout.addView(createTabButton("وای‌فای", currentTab == 1) {
            currentTab = 1
            refreshUI()
        })
        contentLayout.addView(tabLayout)

        // Period Chips (Today, Week, Month)
        val periodLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 25)
        }

        periodLayout.addView(createChipButton("امروز", currentPeriod == 0) {
            currentPeriod = 0
            refreshUI()
        })
        periodLayout.addView(createChipButton("این هفته", currentPeriod == 1) {
            currentPeriod = 1
            refreshUI()
        })
        periodLayout.addView(createChipButton("این ماه", currentPeriod == 2) {
            currentPeriod = 2
            refreshUI()
        })
        contentLayout.addView(periodLayout)

        val statsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val netType = if (currentTab == 0) ConnectivityManager.TYPE_MOBILE else ConnectivityManager.TYPE_WIFI
        val (start, end) = getTimeRange(currentPeriod)

        // Total Usage Card
        val totalBytes = queryNetworkTotal(statsManager, netType, start, end)
        contentLayout.addView(createTotalUsageCard(totalBytes))
        contentLayout.addView(createPackageOverviewCard())
        contentLayout.addView(createChartsButton())
        contentLayout.addView(createComparisonCard(statsManager, netType))
        contentLayout.addView(createSettingsCard())
        contentLayout.addView(createTrafficBreakdownCard(statsManager, netType, start, end))
        if (currentPeriod != 0) contentLayout.addView(createDailyHistoryCard(statsManager, netType, currentPeriod))

        val refreshButton = Button(this).apply {
            text = "↻ بروزرسانی آمار"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#334155"))
            setOnClickListener { refreshUI() }
        }
        contentLayout.addView(refreshButton)

        // App List Title
        val listTitle = TextView(this).apply {
            text = "مصرف اینترنت برنامه‌ها (دانلود / آپلود)"
            textSize = 16f
            setTextColor(ThemeManager.color(this@MainActivity, "text"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(5, 20, 5, 10)
        }
        contentLayout.addView(listTitle)

        // Apps Usage List
        val appList = queryAppUsageList(statsManager, netType, start, end)
        if (appList.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "داده‌ای برای این بازه زمانی یافت نشد."
                setTextColor(ThemeManager.color(this@MainActivity, "muted"))
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 40)
            }
            contentLayout.addView(emptyTv)
        } else {
            for (app in appList) {
                contentLayout.addView(createAppItemRow(app))
            }
        }
    }


    private fun createPackageOverviewCard(): CardView {
        val period = UsageFeatureUtils.billingPeriod(this)
        val now = System.currentTimeMillis()
        val used = UsageFeatureUtils.totalMobileWifi(this, UsageFeatureUtils.Period(period.start, now))
        val limitBytes = (monthlyLimitGb.coerceAtLeast(0.1f) * 1024.0 * 1024.0 * 1024.0).toLong()
        val percent = if (limitBytes > 0L) (used.toDouble() * 100.0 / limitBytes).toInt() else 0
        val remaining = (limitBytes - used).coerceAtLeast(0L)
        val elapsedDays = ((now - period.start).coerceAtLeast(1L) / 86_400_000.0).coerceAtLeast(1.0)
        val dailyAverage = used / elapsedDays
        val estimateDays = if (dailyAverage > 0L) remaining / dailyAverage else null
        val dailyBudgetBytes = prefs.getInt("daily_budget_mb", 500).coerceAtLeast(1).toLong() * 1024L * 1024L
        val todayUsed = UsageFeatureUtils.totalMobileWifi(this, UsageFeatureUtils.Period(getDayStart(0), now))
        val dailyPercent = (todayUsed.toDouble() * 100.0 / dailyBudgetBytes).toInt().coerceIn(0, 100)

        return CardView(this).apply {
            radius = 20f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, "cardAlt"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 20) }
            val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 18) }
            box.addView(TextView(context).apply {
                text = "📦 وضعیت بسته و پیش‌بینی مصرف"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.color(this@MainActivity, "text"))
            })
            box.addView(TextView(context).apply {
                text = "مصرف چرخه: ${formatBytes(used)} از ${formatBytes(limitBytes)} • ${percent.coerceAtLeast(0)}٪"
                textSize = 12f
                setTextColor(ThemeManager.color(this@MainActivity, "text"))
                setPadding(0, 10, 0, 5)
            })
            box.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percent.coerceIn(0, 100)
                progressTintList = ColorStateList.valueOf(ThemeManager.color(this@MainActivity, if (percent >= 90) "danger" else "primary"))
                progressBackgroundTintList = ColorStateList.valueOf(ThemeManager.color(this@MainActivity, "background"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 12).apply { setMargins(0, 0, 0, 8) }
            })
            box.addView(TextView(context).apply {
                text = "باقی‌مانده بسته: ${formatBytes(remaining)}"
                textSize = 12f
                setTextColor(ThemeManager.color(this@MainActivity, "muted"))
            })
            box.addView(TextView(context).apply {
                text = if (estimateDays == null) "پیش‌بینی پایان بسته: پس از ثبت مصرف بیشتر قابل محاسبه است"
                else if (estimateDays <= 0L) "پیش‌بینی: با روند فعلی سقف بسته امروز پر می‌شود"
                else "پیش‌بینی پایان بسته: حدود ${estimateDays.coerceAtMost(3650)} روز دیگر با میانگین مصرف فعلی"
                textSize = 12f
                setTextColor(ThemeManager.color(this@MainActivity, "accent"))
                setPadding(0, 6, 0, 10)
            })
            box.addView(TextView(context).apply {
                text = "بودجه امروز: ${formatBytes(todayUsed)} از ${formatBytes(dailyBudgetBytes)}"
                textSize = 12f
                setTextColor(ThemeManager.color(this@MainActivity, "text"))
            })
            box.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = dailyPercent
                progressTintList = ColorStateList.valueOf(ThemeManager.color(this@MainActivity, if (todayUsed >= dailyBudgetBytes) "danger" else "upload"))
                progressBackgroundTintList = ColorStateList.valueOf(ThemeManager.color(this@MainActivity, "background"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 10).apply { setMargins(0, 4, 0, 0) }
            })
            addView(box)
        }
    }

    override fun onDestroy() {
        speedRunnable?.let { speedHandler.removeCallbacks(it) }
        speedRunnable = null
        super.onDestroy()
    }

    private fun createChartsButton(): CardView {
        return CardView(this).apply {
            radius = 20f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, "primary"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 20) }
            setOnClickListener { startActivity(Intent(this@MainActivity, ChartsActivity::class.java)) }
            addView(TextView(context).apply {
                text = "📊 نمودار و مقایسه روزانه / ماهانه / سالانه"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(18, 20, 18, 20)
            })
        }
    }

    private fun createSpeedCard(): CardView {
        val card = CardView(this).apply {
            radius = 24f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, "card"))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 20)
            layoutParams = params
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 25, 25, 25)
        }

        val title = TextView(this).apply {
            text = "⚡ سرعت لحظه‌ای شبکه"
            textSize = 14f
            setTextColor(ThemeManager.color(this@MainActivity, "text"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 15)
        }

        val speedsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val dlBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.color(this@MainActivity, "background"))
            setPadding(20, 15, 20, 15)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
        }
        val dlLabel = TextView(this).apply { text = "دانلود"; textSize = 11f; setTextColor(ThemeManager.color(this@MainActivity, "muted")) }
        downloadSpeedTv = TextView(this).apply { text = "0.0 Mb/s"; textSize = 14f; setTextColor(ThemeManager.color(this@MainActivity, "download")); typeface = Typeface.DEFAULT_BOLD }
        dlBox.addView(dlLabel)
        dlBox.addView(downloadSpeedTv)

        val ulBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.color(this@MainActivity, "background"))
            setPadding(20, 15, 20, 15)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
        }
        val ulLabel = TextView(this).apply { text = "آپلود"; textSize = 11f; setTextColor(ThemeManager.color(this@MainActivity, "muted")) }
        uploadSpeedTv = TextView(this).apply { text = "0.0 Mb/s"; textSize = 14f; setTextColor(ThemeManager.color(this@MainActivity, "upload")); typeface = Typeface.DEFAULT_BOLD }
        ulBox.addView(ulLabel)
        ulBox.addView(uploadSpeedTv)

        speedsLayout.addView(dlBox)
        speedsLayout.addView(ulBox)
        layout.addView(title)
        layout.addView(TextView(this).apply { text = "برآورد بر اساس ترافیک دستگاه؛ آزمون مستقل سرعت اینترنت نیست"; textSize = 10f; setTextColor(ThemeManager.color(this@MainActivity, "muted")); setPadding(0, 0, 0, 10) })
        layout.addView(speedsLayout)
        card.addView(layout)
        return card
    }

    private fun createTotalUsageCard(bytes: Long): CardView {
        val card = CardView(this).apply {
            radius = 24f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, "card"))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 20)
            layoutParams = params
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "مجموع مصرف در این دوره"
            textSize = 13f
            setTextColor(ThemeManager.color(this@MainActivity, "muted"))
        }

        val value = TextView(this).apply {
            text = formatBytes(bytes)
            textSize = 24f
            setTextColor(ThemeManager.color(this@MainActivity, "accent"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 10, 0, 0)
        }

        layout.addView(title)
        layout.addView(value)
        card.addView(layout)
        return card
    }


    private fun createTrafficBreakdownCard(statsManager: NetworkStatsManager, netType: Int, start: Long, end: Long): CardView {
        val card = CardView(this).apply {
            radius = 20f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, "card"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 20)
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
        }
        val rx = queryNetworkDirection(statsManager, netType, start, end, true)
        val tx = queryNetworkDirection(statsManager, netType, start, end, false)
        layout.addView(makeTrafficBox("دانلود", formatBytes(rx), "#10B981"))
        layout.addView(makeTrafficBox("آپلود", formatBytes(tx), "#3B82F6"))
        card.addView(layout)
        return card
    }

    private fun makeTrafficBox(title: String, value: String, color: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val t = TextView(context).apply {
                text = title
                textSize = 12f
                setTextColor(ThemeManager.color(this@MainActivity, "muted"))
                gravity = Gravity.CENTER
            }
            val v = TextView(context).apply {
                text = value
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(color))
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 0)
            }
            addView(t)
            addView(v)
        }
    }

    private fun createDailyHistoryCard(statsManager: NetworkStatsManager, netType: Int, period: Int): CardView {
        val card = CardView(this).apply {
            radius = 20f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, "card"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 20)
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        layout.addView(TextView(this).apply {
            text = if (period == 1) "گزارش روزانه ۷ روز اخیر" else "مصرف روزانه در ماه جاری"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.color(this@MainActivity, "text"))
            setPadding(0, 0, 0, 12)
        })
        val days = if (period == 1) 7 else Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        for (day in minOf(days, 30) downTo 1) {
            val (s, e) = if (period == 2) dayOfMonthRange(day) else dayRange(day)
            val bytes = queryNetworkTotal(statsManager, netType, s, e)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 5, 0, 5)
            }
            row.addView(TextView(this).apply {
                text = if (period == 2) "روز $day" else if (day == 1) "امروز" else "${day - 1} روز پیش"
                textSize = 11f
                setTextColor(ThemeManager.color(this@MainActivity, "soft"))
                layoutParams = LinearLayout.LayoutParams(75, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = "●"
                textSize = 18f
                setTextColor(ThemeManager.color(this@MainActivity, "accent"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = formatBytes(bytes)
                textSize = 10f
                setTextColor(ThemeManager.color(this@MainActivity, "muted"))
            })
            layout.addView(row)
        }
        card.addView(layout)
        return card
    }

    private fun dayRange(daysAgo: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(daysAgo - 1))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return Pair(start, cal.timeInMillis)
    }

    private fun dayOfMonthRange(dayOfMonth: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return Pair(start, minOf(cal.timeInMillis, System.currentTimeMillis()))
    }

    private fun queryNetworkDirection(statsManager: NetworkStatsManager, netType: Int, start: Long, end: Long, download: Boolean): Long {
        var total = 0L
        try {
            val stats = statsManager.querySummary(netType, null, start, end)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                total += if (download) bucket.rxBytes else bucket.txBytes
            }
            stats.close()
        } catch (e: Exception) {
            Log.e("NetworkStats", "Direction query failed", e)
        }
        return total
    }

    private fun createComparisonCard(statsManager: NetworkStatsManager, netType: Int): CardView {
        val card = CardView(this).apply {
            radius = 20f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, "card"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 20) }
        }
        val today = queryNetworkTotal(statsManager, netType, getDayStart(0), System.currentTimeMillis())
        val yesterday = queryNetworkTotal(statsManager, netType, getDayStart(1), getDayStart(0))
        val change = if (yesterday > 0) ((today - yesterday).toDouble() / yesterday * 100.0) else 0.0
        val text = if (yesterday == 0L) "برای دیروز داده کافی نیست" else String.format(Locale.US, "%.0f%% %s نسبت به دیروز", kotlin.math.abs(change), if (change >= 0) "بیشتر" else "کمتر")
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 18) }
        layout.addView(TextView(this).apply { this.text = "مقایسه با دیروز"; textSize = 13f; setTextColor(ThemeManager.color(this@MainActivity, "muted")) })
        layout.addView(TextView(this).apply { this.text = text; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.color(this@MainActivity, "text")); setPadding(0, 8, 0, 0) })
        layout.addView(TextView(this).apply { this.text = "امروز: ${formatBytes(today)}   |   دیروز: ${formatBytes(yesterday)}"; textSize = 11f; setTextColor(ThemeManager.color(this@MainActivity, "accent")); setPadding(0, 6, 0, 0) })
        card.addView(layout)
        return card
    }

    private fun createSettingsCard(): CardView {
        val card = CardView(this).apply {
            radius = 20f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, "cardAlt"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 20) }
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 18) }
        layout.addView(TextView(this).apply { text = "⚙️ تنظیمات بسته اینترنت"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.color(this@MainActivity, "text")) })
        val themeButton = Button(this).apply {
            text = "🎨 تم برنامه: ${ThemeManager.name(ThemeManager.current(this@MainActivity))}"
            setTextColor(ThemeManager.color(this@MainActivity, "text"))
            setBackgroundColor(ThemeManager.color(this@MainActivity, "primary"))
            setOnClickListener { showThemePicker() }
        }
        layout.addView(themeButton)
        val limit = EditText(this).apply { hint = "حجم ماهانه (گیگابایت)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(monthlyLimitGb.toString()); setTextColor(ThemeManager.color(this@MainActivity, "text")); setHintTextColor(ThemeManager.color(this@MainActivity, "muted")) }
        val percent = EditText(this).apply { hint = "درصد هشدار (مثلاً 80)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(warningPercent.toString()); setTextColor(ThemeManager.color(this@MainActivity, "text")); setHintTextColor(ThemeManager.color(this@MainActivity, "muted")) }
        layout.addView(limit); layout.addView(percent)
        val notify = Switch(this).apply { text = "اعلان نزدیک‌شدن به سقف مصرف"; setTextColor(ThemeManager.color(this@MainActivity, "text")); isChecked = prefs.getBoolean("notifications", true) }
        layout.addView(notify)
        val save = Button(this).apply {
            text = "ذخیره تنظیمات"
            setTextColor(Color.WHITE)
            setBackgroundColor(ThemeManager.color(this@MainActivity, "primary"))
            setOnClickListener {
                val l = limit.text.toString().toFloatOrNull()?.coerceAtLeast(0.1f) ?: 10f
                val p = percent.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 80
                prefs.edit().putFloat("monthly_limit_gb", l).putInt("warning_percent", p).putBoolean("notifications", notify.isChecked).apply()
                scheduleDailyCheck()
                refreshUI()
            }
        }
        layout.addView(save)
        card.addView(layout)
        return card
    }

    private fun showThemePicker() {
        val ids = ThemeManager.ids()
        val labels = ids.map { "●  " + ThemeManager.name(it) }.toTypedArray()
        val current = ids.indexOf(ThemeManager.current(this))
        AlertDialog.Builder(this)
            .setTitle("انتخاب تم")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.edit().putString("theme", ids[which]).apply()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun getDayStart(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun queryAppDirection(statsManager: NetworkStatsManager, netType: Int, start: Long, end: Long, uid: Int, download: Boolean): Long {
        var total = 0L
        try {
            val stats = statsManager.queryDetailsForUid(netType, null, start, end, uid)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) { stats.getNextBucket(bucket); total += if (download) bucket.rxBytes else bucket.txBytes }
            stats.close()
        } catch (_: Exception) {}
        return total
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("usage_alerts", "هشدار مصرف اینترنت", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun scheduleDailyCheck() {
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, UsageAlertReceiver::class.java)
        val pending = PendingIntent.getBroadcast(this, 7001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (!prefs.getBoolean("notifications", true)) {
            alarm.cancel(pending)
            return
        }
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1) }
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
    }

    private fun createAppItemRow(app: AppInfo): CardView {
        val card = CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(ThemeManager.color(this@MainActivity, "card"))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 12)
            layoutParams = params
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 20, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(app.icon ?: packageManager.defaultActivityIcon)
            layoutParams = LinearLayout.LayoutParams(64, 64)
        }

        val nameView = TextView(this).apply {
            text = app.name
            textSize = 14f
            setTextColor(ThemeManager.color(this@MainActivity, "text"))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(16, 0, 16, 0)
            }
        }

        val usageView = TextView(this).apply {
            text = formatBytes(app.bytes)
            textSize = 12f
            setTextColor(ThemeManager.color(this@MainActivity, "accent"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }
        val detailView = TextView(this).apply {
            text = "↓ ${formatBytes(app.rxBytes)}   ↑ ${formatBytes(app.txBytes)}"
            textSize = 9f
            setTextColor(ThemeManager.color(this@MainActivity, "muted"))
            gravity = Gravity.END
        }
        val usageLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; addView(usageView); addView(detailView) }

        layout.addView(iconView)
        layout.addView(nameView)
        layout.addView(usageLayout)
        card.addView(layout)
        return card
    }

    private fun createTabButton(title: String, isSelected: Boolean, onClick: () -> Unit): CardView {
        return CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(if (isSelected) ThemeManager.color(this@MainActivity, "primary") else ThemeManager.color(this@MainActivity, "card"))
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(4, 0, 4, 0)
            layoutParams = params
            setOnClickListener { onClick() }

            val tv = TextView(context).apply {
                text = title
                textSize = 13f
                setTextColor(if (isSelected) Color.WHITE else ThemeManager.color(this@MainActivity, "text"))
                gravity = Gravity.CENTER
                setPadding(15, 18, 15, 18)
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(tv)
        }
    }

    private fun createChipButton(title: String, isSelected: Boolean, onClick: () -> Unit): CardView {
        return CardView(this).apply {
            radius = 20f
            setCardBackgroundColor(if (isSelected) ThemeManager.color(this@MainActivity, "upload") else ThemeManager.color(this@MainActivity, "background"))
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(4, 0, 4, 0)
            layoutParams = params
            setOnClickListener { onClick() }

            val tv = TextView(context).apply {
                text = title
                textSize = 12f
                setTextColor(if (isSelected) Color.WHITE else ThemeManager.color(this@MainActivity, "muted"))
                gravity = Gravity.CENTER
                setPadding(10, 12, 10, 12)
            }
            addView(tv)
        }
    }

    private fun getTimeRange(period: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val endTime = cal.timeInMillis
        when (period) {
            0 -> { // Today
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            1 -> { // Current calendar week, starting Monday
                cal.setFirstDayOfWeek(Calendar.MONDAY)
                val daysSinceMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            2 -> { // Current calendar month, from the first day until now
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
        }
        return Pair(cal.timeInMillis, endTime)
    }

    private fun queryNetworkTotal(statsManager: NetworkStatsManager, netType: Int, start: Long, end: Long): Long {
        var total = 0L
        try {
            val stats = statsManager.querySummary(netType, null, start, end)
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

    private fun queryAppUsageList(statsManager: NetworkStatsManager, netType: Int, start: Long, end: Long): List<AppInfo> {
        val map = HashMap<String, Long>()
        val pm = packageManager
        try {
            val stats = statsManager.querySummary(netType, null, start, end)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val bytes = bucket.rxBytes + bucket.txBytes
                if (bytes < 5120) continue // Skip very low traffic

                val pkgs = pm.getPackagesForUid(uid)
                if (pkgs != null && pkgs.isNotEmpty()) {
                    val pkg = pkgs[0]
                    map[pkg] = (map[pkg] ?: 0L) + bytes
                }
            }
            stats.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val list = mutableListOf<AppInfo>()
        for ((pkg, bytes) in map) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val name = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val appUid = pm.getApplicationInfo(pkg, 0).uid
                val rx = queryAppDirection(statsManager, netType, start, end, appUid, true)
                val tx = queryAppDirection(statsManager, netType, start, end, appUid, false)
                list.add(AppInfo(name, icon, bytes, rx, tx))
            } catch (e: Exception) {
                continue
            }
        }
        return list.sortedByDescending { it.bytes }
    }

    private fun startSpeedChecker() {
        lastRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        lastTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        lastTime = System.currentTimeMillis()
        speedRunnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                val currentRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
                val currentTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
                val currentTime = System.currentTimeMillis()
                val timeDiff = (currentTime - lastTime) / 1000.0
                if (timeDiff > 0.0) {
                    val dlSpeed = ((currentRx - lastRx).coerceAtLeast(0L) * 8.0 / timeDiff) / (1024.0 * 1024.0)
                    val ulSpeed = ((currentTx - lastTx).coerceAtLeast(0L) * 8.0 / timeDiff) / (1024.0 * 1024.0)
                    downloadSpeedTv.text = String.format(Locale.US, "%.2f Mbps", dlSpeed)
                    uploadSpeedTv.text = String.format(Locale.US, "%.2f Mbps", ulSpeed)
                }
                lastRx = currentRx
                lastTx = currentTx
                lastTime = currentTime
                speedRunnable?.let { speedHandler.postDelayed(it, 2000) }
            }
        }
        speedRunnable?.let { speedHandler.postDelayed(it, 2000) }
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f گیگابایت", gb)
            mb >= 1.0 -> String.format("%.1f مگابایت", mb)
            kb >= 1.0 -> String.format("%.0f کیلوبایت", kb)
            else -> "$bytes بایت"
        }
    }

    data class AppInfo(
        val name: String,
        val icon: Drawable?,
        val bytes: Long,
        val rxBytes: Long,
        val txBytes: Long
    )
}
