package com.amarmasraf.internet

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class AppUsageActivity : AppCompatActivity() {
    data class Item(val name: String, val pkg: String, val bytes: Long, val rx: Long, val tx: Long)

    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var sort = 0
    private var networkMode = 2
    private var periodMode = 0
    private var searchQuery = ""
    private var all = emptyList<Item>()
    private var permissionMissing = false
    private var appliedLanguage: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (UsageFeatureUtils.hasUsageAccess(this)) build() else showPermissionScreen()
    }

    override fun onResume() {
        super.onResume()
        val selected = getSharedPreferences("settings", Context.MODE_PRIVATE).getString("language", "system")
        if (appliedLanguage != null && appliedLanguage != selected) {
            appliedLanguage = selected
            build()
            return
        }
        if (permissionMissing && UsageFeatureUtils.hasUsageAccess(this)) {
            permissionMissing = false
            build()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showPermissionScreen() {
        permissionMissing = true
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(30), dp(24), dp(30))
            setBackgroundColor(ThemeManager.color(this@AppUsageActivity, "background"))
        }
        page.addView(TextView(this).apply {
            text = LanguageCalendar.text(this, "برای نمایش مصرف هر برنامه، دسترسی «آمار مصرف» را در تنظیمات اندروید فعال کنید.", "Enable Usage Access in Android settings to view per-app usage.")
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.color(this@AppUsageActivity, "text"))
        })
        page.addView(Button(this).apply {
            text = LanguageCalendar.text(this, "باز کردن تنظیمات دسترسی", "Open access settings")
            setOnClickListener { startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        })
        setContentView(page)
    }

    private fun build() {
        appliedLanguage = getSharedPreferences("settings", Context.MODE_PRIVATE).getString("language", "system")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            setBackgroundColor(ThemeManager.color(this@AppUsageActivity, "background"))
        }

        root.addView(TextView(this).apply {
            text = LanguageCalendar.text(this, "📱 مصرف برنامه‌ها", "📱 App usage")
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.color(this@AppUsageActivity, "text"))
            setPadding(dp(2), 0, dp(2), dp(5))
        })
        root.addView(TextView(this).apply {
            text = LanguageCalendar.text(this, "مصرف اینترنت هر برنامه را با فیلترهای مرتب بررسی کنید", "Review each app’s data usage with organized filters")
            textSize = 12f
            setTextColor(ThemeManager.color(this@AppUsageActivity, "muted"))
            setPadding(dp(2), 0, dp(2), dp(12))
        })

        search = EditText(this).apply {
            hint = LanguageCalendar.text(this, "جستجوی نام برنامه یا package", "Search app name or package")
            textSize = 14f
            singleLine = true
            setTextColor(ThemeManager.color(this@AppUsageActivity, "text"))
            setHintTextColor(ThemeManager.color(this@AppUsageActivity, "muted"))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground(ThemeManager.color(this@AppUsageActivity, "card"), ThemeManager.color(this@AppUsageActivity, "muted"), 12, 1)
            setText(searchQuery)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(12) })

        root.addView(makeFilterSection(
            LanguageCalendar.text(this, "🌐 نوع شبکه", "🌐 Network"),
            listOf(LanguageCalendar.text(this, "📶 سیم‌کارت", "📶 Mobile"), LanguageCalendar.text(this, "📡 وای‌فای", "📡 Wi-Fi"), LanguageCalendar.text(this, "🌐 همه شبکه‌ها", "🌐 All")),
            networkMode, 3
        ) { networkMode = it; build() })

        root.addView(makeFilterSection(
            LanguageCalendar.text(this, "🗓 بازه زمانی", "🗓 Time period"),
            listOf(LanguageCalendar.text(this, "چرخه قبض", "Billing cycle"), LanguageCalendar.text(this, "امروز", "Today"), LanguageCalendar.text(this, "این هفته", "This week"), LanguageCalendar.text(this, "این ماه", "This month")),
            periodMode, 2
        ) { periodMode = it; build() })

        root.addView(makeFilterSection(
            LanguageCalendar.text(this, "↕ مرتب‌سازی برنامه‌ها", "↕ Sort apps"),
            listOf(LanguageCalendar.text(this, "بیشترین مصرف", "Most used"), LanguageCalendar.text(this, "بر اساس نام", "By name"), LanguageCalendar.text(this, "کمترین مصرف", "Least used")),
            sort, 3
        ) { sort = it; build() })

        root.addView(TextView(this).apply {
            text = LanguageCalendar.text(this, "فهرست برنامه‌ها", "Apps list")
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.color(this@AppUsageActivity, "text"))
            setPadding(dp(2), dp(14), dp(2), dp(8))
        })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                render()
            }
            override fun afterTextChanged(e: Editable?) {}
        })

        all = load()
        render()
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun makeFilterSection(title: String, options: List<String>, selected: Int, columns: Int, onSelect: (Int) -> Unit): CardView {
        val card = CardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = dp(2).toFloat()
            useCompatPadding = true
            setCardBackgroundColor(ThemeManager.color(this@AppUsageActivity, "card"))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        inner.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.color(this@AppUsageActivity, "text"))
            setPadding(dp(2), 0, dp(2), dp(8))
        })
        var row: LinearLayout? = null
        options.forEachIndexed { index, label ->
            if (index % columns == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                inner.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    if (index > 0) topMargin = dp(7)
                })
            }
            val chosen = index == selected
            val button = TextView(this).apply {
                text = (if (chosen) "✓  " else "") + label
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(dp(5), dp(11), dp(5), dp(11))
                setTextColor(if (chosen) Color.WHITE else ThemeManager.color(this@AppUsageActivity, "text"))
                background = roundedBackground(
                    if (chosen) withAlpha(ThemeManager.color(this@AppUsageActivity, "primary"), 0.78f)
                    else ThemeManager.color(this@AppUsageActivity, "cardAlt"),
                    if (chosen) ThemeManager.color(this@AppUsageActivity, "primary") else ThemeManager.color(this@AppUsageActivity, "muted"),
                    11, if (chosen) 2 else 1
                )
                elevation = if (chosen) dp(3).toFloat() else 0f
                isClickable = true
                isFocusable = true
                setOnClickListener { if (!chosen) onSelect(index) }
            }
            val lp = LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                if (index % columns != 0) marginStart = dp(6)
                if (index % columns != columns - 1 && index != options.lastIndex) marginEnd = dp(1)
            }
            row?.addView(button, lp)
        }
        // Keep the final row aligned when it has fewer options than the configured column count.
        if (options.size % columns != 0) {
            val lastRow = row
            repeat(columns - options.size % columns) {
                lastRow?.addView(View(this), LinearLayout.LayoutParams(0, dp(43), 1f).apply { marginStart = dp(6) })
            }
        }
        card.addView(inner)
        return card
    }

    private fun withAlpha(color: Int, fraction: Float): Int =
        Color.argb((Color.alpha(color) * fraction).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun roundedBackground(fill: Int, stroke: Int, radiusDp: Int, strokeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(strokeDp), stroke)
        }

    private fun load(): List<Item> {
        val manager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val totals = HashMap<String, Long>()
        val rxTotals = HashMap<String, Long>()
        val txTotals = HashMap<String, Long>()
        val types = when (networkMode) {
            0 -> intArrayOf(ConnectivityManager.TYPE_MOBILE)
            1 -> intArrayOf(ConnectivityManager.TYPE_WIFI)
            else -> intArrayOf(ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIFI)
        }
        val period = selectedPeriod()
        for (type in types) try {
            val stats = manager.querySummary(type, null, period.start, period.end)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val packages = packageManager.getPackagesForUid(bucket.uid)
                if (!packages.isNullOrEmpty()) {
                    val pkg = packages[0]
                    totals[pkg] = (totals[pkg] ?: 0L) + bucket.rxBytes + bucket.txBytes
                    rxTotals[pkg] = (rxTotals[pkg] ?: 0L) + bucket.rxBytes
                    txTotals[pkg] = (txTotals[pkg] ?: 0L) + bucket.txBytes
                }
            }
            stats.close()
        } catch (_: Exception) {}
        return totals.mapNotNull { (pkg, bytes) ->
            try {
                val app = packageManager.getApplicationInfo(pkg, 0)
                Item(packageManager.getApplicationLabel(app).toString(), pkg, bytes, rxTotals[pkg] ?: 0L, txTotals[pkg] ?: 0L)
            } catch (_: Exception) { null }
        }
    }

    private fun selectedPeriod(): UsageFeatureUtils.Period {
        val now = System.currentTimeMillis()
        return when (periodMode) {
            1 -> UsageFeatureUtils.today()
            2 -> {
                val calendar = java.util.Calendar.getInstance()
                val daysFromMonday = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysFromMonday)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                UsageFeatureUtils.Period(calendar.timeInMillis, now)
            }
            3 -> {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                UsageFeatureUtils.Period(calendar.timeInMillis, now)
            }
            else -> UsageFeatureUtils.billingPeriod(this)
        }
    }

    private fun render() {
        if (!::list.isInitialized) return
        val query = search.text.toString().trim()
        val items = all.filter { query.isEmpty() || it.name.contains(query, true) || it.pkg.contains(query, true) }
            .let {
                when (sort) {
                    1 -> it.sortedBy { item -> item.name.lowercase() }
                    2 -> it.sortedBy { item -> item.bytes }
                    else -> it.sortedByDescending { item -> item.bytes }
                }
            }
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (query.isEmpty()) LanguageCalendar.text(this@AppUsageActivity, "برای این بازه داده‌ای برای برنامه‌ها ثبت نشده است.", "No app usage data was recorded for this period.")
                else LanguageCalendar.text(this@AppUsageActivity, "نتیجه‌ای برای «$query» پیدا نشد.", "No results found for “$query”.")
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ThemeManager.color(this@AppUsageActivity, "muted"))
                setPadding(dp(20), dp(35), dp(20), dp(35))
            })
            return
        }

        items.forEachIndexed { index, item ->
            val card = CardView(this).apply {
                radius = dp(15).toFloat()
                cardElevation = dp(2).toFloat()
                useCompatPadding = true
                setCardBackgroundColor(ThemeManager.color(this@AppUsageActivity, if (index % 2 == 0) "card" else "cardAlt"))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            }
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(13), dp(15), dp(13))
            }
            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(TextView(this).apply {
                text = item.name
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.color(this@AppUsageActivity, "text"))
                maxLines = 2
            }, LinearLayout.LayoutParams(0, -2, 1f))
            titleRow.addView(TextView(this).apply {
                text = "#${index + 1}"
                textSize = 11f
                setTextColor(ThemeManager.color(this@AppUsageActivity, "muted"))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = roundedBackground(ThemeManager.color(this@AppUsageActivity, "cardAlt"), ThemeManager.color(this@AppUsageActivity, "muted"), 8, 1)
            })
            content.addView(titleRow)
            content.addView(TextView(this).apply {
                text = item.pkg
                textSize = 11f
                setTextColor(ThemeManager.color(this@AppUsageActivity, "muted"))
                setPadding(0, dp(3), 0, dp(10))
            })
            content.addView(View(this).apply {
                setBackgroundColor(withAlpha(ThemeManager.color(this@AppUsageActivity, "muted"), 0.35f))
            }, LinearLayout.LayoutParams(-1, dp(1)))
            content.addView(TextView(this).apply {
                text = LanguageCalendar.text(this, "مجموع مصرف اینترنت", "Total data used")
                textSize = 12f
                setTextColor(ThemeManager.color(this@AppUsageActivity, "muted"))
                setPadding(0, dp(9), 0, dp(2))
            })
            content.addView(TextView(this).apply {
                text = UsageFeatureUtils.formatBytes(item.bytes)
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.color(this@AppUsageActivity, "primary"))
                setPadding(0, 0, 0, dp(8))
            })
            val traffic = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(7), 0, 0)
            }
            traffic.addView(trafficInfo(LanguageCalendar.text(this, "↓ دانلود", "↓ Download"), item.rx), LinearLayout.LayoutParams(0, -2, 1f))
            traffic.addView(View(this).apply {
                setBackgroundColor(withAlpha(ThemeManager.color(this@AppUsageActivity, "muted"), 0.35f))
            }, LinearLayout.LayoutParams(dp(1), dp(39)))
            traffic.addView(trafficInfo(LanguageCalendar.text(this, "↑ آپلود", "↑ Upload"), item.tx), LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(traffic)
            card.addView(content)
            list.addView(card)
        }
    }

    private fun trafficInfo(label: String, bytes: Long): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(this@AppUsageActivity).apply {
                text = label
                textSize = 11f
                setTextColor(ThemeManager.color(this@AppUsageActivity, "muted"))
                gravity = Gravity.CENTER
            })
            addView(TextView(this@AppUsageActivity).apply {
                text = UsageFeatureUtils.formatBytes(bytes)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.color(this@AppUsageActivity, "text"))
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, 0)
            })
        }
}
