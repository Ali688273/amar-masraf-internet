package com.amarmasraf.internet

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
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
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusBannerType
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.AdShowListener
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusErrorModel
import java.util.Calendar

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
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
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subTitleTv = TextView(this).apply {
            text = "مدیریت و پایش مصرف داده"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
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
                setTextColor(Color.parseColor("#EF4444"))
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

        // Tapsell Banner Container at bottom
        bannerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(bannerContainer)

        setContentView(rootLayout)

        startSpeedChecker()
        initTapsell()
    }

    private fun initTapsell() {
        TapsellPlus.initialize(this, TapsellKey, object : TapsellPlusInitListener() {
            override fun onInitializeSuccess(adNetworks: String) {
                Log.d("TapsellInit", "Initialized successfully")
                loadBannerAd()
            }

            override fun onInitializationFailed(adNetworks: String, error: AdNetworkError) {
                Log.e("TapsellInit", "Failed: ${error.errorMessage}")
            }
        })
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

                override fun onError(error: TapsellPlusErrorModel) {
                    Log.e("TapsellRequest", "Error: ${error.errorMessage}")
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

        // App List Title
        val listTitle = TextView(this).apply {
            text = "مصرف اینترنت برنامه‌ها"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(5, 20, 5, 10)
        }
        contentLayout.addView(listTitle)

        // Apps Usage List
        val appList = queryAppUsageList(statsManager, netType, start, end)
        if (appList.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "داده‌ای برای این بازه زمانی یافت نشد."
                setTextColor(Color.parseColor("#64748B"))
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

    private fun createSpeedCard(): CardView {
        val card = CardView(this).apply {
            radius = 24f
            setCardBackgroundColor(Color.parseColor("#1E293B"))
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
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 15)
        }

        val speedsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val dlBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(20, 15, 20, 15)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
        }
        val dlLabel = TextView(this).apply { text = "دانلود"; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")) }
        downloadSpeedTv = TextView(this).apply { text = "0.0 Mb/s"; textSize = 14f; setTextColor(Color.parseColor("#10B981")); typeface = Typeface.DEFAULT_BOLD }
        dlBox.addView(dlLabel)
        dlBox.addView(downloadSpeedTv)

        val ulBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(20, 15, 20, 15)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
        }
        val ulLabel = TextView(this).apply { text = "آپلود"; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")) }
        uploadSpeedTv = TextView(this).apply { text = "0.0 Mb/s"; textSize = 14f; setTextColor(Color.parseColor("#3B82F6")); typeface = Typeface.DEFAULT_BOLD }
        ulBox.addView(ulLabel)
        ulBox.addView(uploadSpeedTv)

        speedsLayout.addView(dlBox)
        speedsLayout.addView(ulBox)
        layout.addView(title)
        layout.addView(speedsLayout)
        card.addView(layout)
        return card
    }

    private fun createTotalUsageCard(bytes: Long): CardView {
        val card = CardView(this).apply {
            radius = 24f
            setCardBackgroundColor(Color.parseColor("#1E293B"))
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
            setTextColor(Color.parseColor("#94A3B8"))
        }

        val value = TextView(this).apply {
            text = formatBytes(bytes)
            textSize = 24f
            setTextColor(Color.parseColor("#818CF8"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 10, 0, 0)
        }

        layout.addView(title)
        layout.addView(value)
        card.addView(layout)
        return card
    }

    private fun createAppItemRow(app: AppInfo): CardView {
        val card = CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(Color.parseColor("#1E293B"))
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
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(16, 0, 16, 0)
            }
        }

        val usageView = TextView(this).apply {
            text = formatBytes(app.bytes)
            textSize = 13f
            setTextColor(Color.parseColor("#38BDF8"))
            typeface = Typeface.DEFAULT_BOLD
        }

        layout.addView(iconView)
        layout.addView(nameView)
        layout.addView(usageView)
        card.addView(layout)
        return card
    }

    private fun createTabButton(title: String, isSelected: Boolean, onClick: () -> Unit): CardView {
        return CardView(this).apply {
            radius = 16f
            setCardBackgroundColor(if (isSelected) Color.parseColor("#6366F1") else Color.parseColor("#1E293B"))
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(4, 0, 4, 0)
            layoutParams = params
            setOnClickListener { onClick() }

            val tv = TextView(context).apply {
                text = title
                textSize = 13f
                setTextColor(Color.WHITE)
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
            setCardBackgroundColor(if (isSelected) Color.parseColor("#3B82F6") else Color.parseColor("#0F172A"))
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(4, 0, 4, 0)
            layoutParams = params
            setOnClickListener { onClick() }

            val tv = TextView(context).apply {
                text = title
                textSize = 12f
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
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
            1 -> { // Week
                cal.add(Calendar.DAY_OF_YEAR, -7)
            }
            2 -> { // Month
                cal.add(Calendar.MONTH, -1)
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
                list.add(AppInfo(name, icon, bytes))
            } catch (e: Exception) {
                continue
            }
        }
        return list.sortedByDescending { it.bytes }
    }

    private fun startSpeedChecker() {
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()

        speedHandler.postDelayed(object : Runnable {
            override fun run() {
                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()
                val currentTime = System.currentTimeMillis()

                val timeDiff = (currentTime - lastTime) / 1000.0
                if (timeDiff > 0) {
                    val dlSpeed = ((currentRx - lastRx) * 8 / timeDiff) / (1024 * 1024)
                    val ulSpeed = ((currentTx - lastTx) * 8 / timeDiff) / (1024 * 1024)

                    downloadSpeedTv.text = String.format("%.1f Mb/s", if (dlSpeed < 0) 0.0 else dlSpeed)
                    uploadSpeedTv.text = String.format("%.1f Mb/s", if (ulSpeed < 0) 0.0 else ulSpeed)
                }

                lastRx = currentRx
                lastTx = currentTx
                lastTime = currentTime

                speedHandler.postDelayed(this, 2000)
            }
        }, 2000)
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
        val bytes: Long
    )
}
