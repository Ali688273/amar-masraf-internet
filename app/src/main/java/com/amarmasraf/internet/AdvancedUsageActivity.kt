package com.amarmasraf.internet

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.util.Collections

class AdvancedUsageActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); build() }

    private fun build() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20,25,20,30)
            setBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "background"))
        }
        root.addView(TextView(this).apply {
            text=LanguageCalendar.text(this, "⚙️ امکانات پیشرفته مصرف", "⚙️ Advanced usage options"); textSize=22f; setTextColor(ThemeManager.color(this@AdvancedUsageActivity, "text")); setPadding(0,0,0,15)
        })

        if (!UsageFeatureUtils.hasUsageAccess(this)) {
            root.addView(card("دسترسی لازم است", "برای محاسبه درست گزارش‌ها، دسترسی آمار مصرف را در تنظیمات اندروید فعال کنید.").apply {
                setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            })
        }

        root.addView(Button(this).apply {
            text = LanguageCalendar.text(this, "🎨 انتخاب تم و رنگ کارت‌ها", "🎨 Choose theme and card colors")
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "primary"))
            setOnClickListener { showThemePicker() }
        })

        root.addView(Button(this).apply {
            text = LanguageCalendar.text(this@AdvancedUsageActivity, "🌐 زبان برنامه: " + if (LanguageCalendar.isEnglish(this@AdvancedUsageActivity)) "English" else "فارسی", "🌐 App language: " + if (LanguageCalendar.isEnglish(this@AdvancedUsageActivity)) "English" else "Persian")
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "primary"))
            setOnClickListener { showLanguagePicker() }
        })
        root.addView(Button(this).apply {
            val solar = LanguageCalendar.isSolar(this@AdvancedUsageActivity)
            text = LanguageCalendar.text(this@AdvancedUsageActivity, "📅 تقویم: " + if (solar) "خورشیدی" else "میلادی", "📅 Calendar: " + if (solar) "Solar Hijri" else "Gregorian")
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "primary"))
            setOnClickListener { showCalendarPicker() }
        })

        val monthlyLimit=EditText(this).apply { hint="حجم بسته در چرخه قبض (گیگابایت)"; setText(prefs.getFloat("monthly_limit_gb",10f).toString()); inputType=8194; setTextColor(ThemeManager.color(this@AdvancedUsageActivity, "text")); setHintTextColor(ThemeManager.color(this@AdvancedUsageActivity, "muted")) }
        root.addView(monthlyLimit)
        val warning=EditText(this).apply { hint="درصد هشدار سقف بسته (۱ تا ۱۰۰)"; setText(prefs.getInt("warning_percent",80).toString()); inputType=2; setTextColor(ThemeManager.color(this@AdvancedUsageActivity, "text")); setHintTextColor(ThemeManager.color(this@AdvancedUsageActivity, "muted")) }
        root.addView(warning)
        val notifications=Switch(this).apply { text=LanguageCalendar.text(this, "فعال بودن هشدارهای مصرف", "Enable usage alerts"); setTextColor(ThemeManager.color(this@AdvancedUsageActivity, "text")); isChecked=prefs.getBoolean("notifications",true) }
        root.addView(notifications)

        val daily=EditText(this).apply { hint="بودجه روزانه (مگابایت)"; setText(prefs.getInt("daily_budget_mb",500).toString()); inputType=2; setTextColor(ThemeManager.color(this@AdvancedUsageActivity, "text")); setHintTextColor(ThemeManager.color(this@AdvancedUsageActivity, "muted")) }
        root.addView(daily)
        val billing=EditText(this).apply { hint="روز شروع چرخه قبض، ۱ تا ۲۸"; setText(prefs.getInt("billing_start_day",1).toString()); inputType=2; setTextColor(ThemeManager.color(this@AdvancedUsageActivity, "text")); setHintTextColor(ThemeManager.color(this@AdvancedUsageActivity, "muted")) }
        root.addView(billing)
        val anomaly=EditText(this).apply { hint="ضریب هشدار مصرف غیرعادی"; setText(prefs.getFloat("anomaly_multiplier",2f).toString()); inputType=8194; setTextColor(ThemeManager.color(this@AdvancedUsageActivity, "text")); setHintTextColor(ThemeManager.color(this@AdvancedUsageActivity, "muted")) }
        root.addView(anomaly)

        root.addView(Button(this).apply {
            text=LanguageCalendar.text(this, "ذخیره تنظیمات", "Save settings")
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "primary"))
            setOnClickListener {
                prefs.edit()
                    .putFloat("monthly_limit_gb", monthlyLimit.text.toString().toFloatOrNull()?.coerceAtLeast(0.1f) ?: 10f)
                    .putInt("warning_percent", warning.text.toString().toIntOrNull()?.coerceIn(1,100) ?: 80)
                    .putBoolean("notifications", notifications.isChecked)
                    .putInt("daily_budget_mb",daily.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 500)
                    .putInt("billing_start_day",billing.text.toString().toIntOrNull()?.coerceIn(1,28) ?: 1)
                    .putFloat("anomaly_multiplier",anomaly.text.toString().toFloatOrNull()?.coerceAtLeast(1.2f) ?: 2f).apply()
                Toast.makeText(this@AdvancedUsageActivity,"تنظیمات ذخیره شد",Toast.LENGTH_SHORT).show()
                if (notifications.isChecked) scheduleUsageAlerts() else cancelUsageAlerts()
                build()
            }
        })

        root.addView(card("📅 بودجه امروز", todayBudget()))
        root.addView(card("📊 چرخه قبض", billingStatus()))
        root.addView(card("⚠️ مصرف غیرعادی", anomalyStatus()))
        root.addView(card("📶 اطلاعات شبکه", networkStatus()))
        root.addView(card("📡 Hotspot / Tethering", hotspotStatus()))

        root.addView(Button(this).apply {
            text=LanguageCalendar.text(this, "🔎 جستجو و مرتب‌سازی برنامه‌ها", "🔎 Search and sort apps")
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "primary"))
            setOnClickListener { startActivity(Intent(this@AdvancedUsageActivity,AppUsageActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text=LanguageCalendar.text(this, "📄 خروجی CSV", "📄 Export CSV")
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "primary"))
            setOnClickListener { startActivity(Intent(this@AdvancedUsageActivity,CsvExportActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text=LanguageCalendar.text(this, "🔔 فعال‌سازی نمایش مصرف در نوار اعلان", "🔔 Show usage in notification")
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "upload"))
            setOnClickListener {
                if (!UsageFeatureUtils.hasUsageAccess(this@AdvancedUsageActivity)) {
                    Toast.makeText(this@AdvancedUsageActivity, "ابتدا دسترسی آمار مصرف را فعال کنید", Toast.LENGTH_LONG).show()
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    return@setOnClickListener
                }
                prefs.edit().putBoolean("live_usage_enabled", true).apply()
                if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),8001)
                androidx.core.content.ContextCompat.startForegroundService(this@AdvancedUsageActivity,Intent(this@AdvancedUsageActivity,UsageMonitorService::class.java))
                Toast.makeText(this@AdvancedUsageActivity,"نمایش مصرف در نوار اعلان فعال شد",Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text=LanguageCalendar.text(this, "⏹ خاموش‌کردن نمایش مصرف در نوار اعلان", "⏹ Hide usage notification")
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "primary"))
            setOnClickListener {
                prefs.edit().putBoolean("live_usage_enabled", false).apply()
                stopService(Intent(this@AdvancedUsageActivity, UsageMonitorService::class.java))
                Toast.makeText(this@AdvancedUsageActivity,"نمایش مصرف در نوار اعلان خاموش شد",Toast.LENGTH_SHORT).show()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun scheduleUsageAlerts() {
        val alarm = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, UsageAlertReceiver::class.java)
        val pending = android.app.PendingIntent.getBroadcast(this, 7001, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val next = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MINUTE, 5)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        alarm.setInexactRepeating(android.app.AlarmManager.RTC_WAKEUP, next.timeInMillis, android.app.AlarmManager.INTERVAL_HOUR, pending)
    }

    private fun cancelUsageAlerts() {
        val alarm = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, UsageAlertReceiver::class.java)
        val pending = android.app.PendingIntent.getBroadcast(this, 7001, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pending)
    }

    private fun showLanguagePicker() {
        val english = LanguageCalendar.isEnglish(this)
        val options = arrayOf("فارسی", "English")
        android.app.AlertDialog.Builder(this)
            .setTitle(LanguageCalendar.text(this, "انتخاب زبان برنامه", "Choose app language"))
            .setSingleChoiceItems(options, if (english) 1 else 0) { dialog, which ->
                prefs.edit().putString("language", if (which == 1) "en" else "fa").apply()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(LanguageCalendar.text(this, "انصراف", "Cancel"), null)
            .show()
    }

    private fun showCalendarPicker() {
        val solar = LanguageCalendar.isSolar(this)
        val options = if (LanguageCalendar.isEnglish(this)) arrayOf("Solar Hijri calendar", "Gregorian calendar") else arrayOf("تقویم خورشیدی", "تقویم میلادی")
        android.app.AlertDialog.Builder(this)
            .setTitle(LanguageCalendar.text(this, "انتخاب نوع تقویم", "Choose calendar"))
            .setSingleChoiceItems(options, if (solar) 0 else 1) { dialog, which ->
                prefs.edit().putString("calendar", if (which == 0) "solar" else "gregorian").apply()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(LanguageCalendar.text(this, "انصراف", "Cancel"), null)
            .show()
    }

    private fun showThemePicker() {
        val ids = ThemeManager.ids()
        val labels = ids.map { "●  " + ThemeManager.name(it) }.toTypedArray()
        val selected = ids.indexOf(ThemeManager.current(this)).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("انتخاب تم رنگی")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                prefs.edit().putString("theme", ids[which]).apply()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun card(title:String,body:String)=CardView(this).apply{
        radius=18f; setCardBackgroundColor(ThemeManager.color(this@AdvancedUsageActivity, "cardAlt"))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,8,0,4)}
        val l=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(18,15,18,15)}
        l.addView(TextView(context).apply{text=title;textSize=15f;setTextColor(ThemeManager.color(this@AdvancedUsageActivity, "text"));typeface=android.graphics.Typeface.DEFAULT_BOLD})
        l.addView(TextView(context).apply{text=body;textSize=13f;setTextColor(ThemeManager.color(this@AdvancedUsageActivity, "muted"));setPadding(0,7,0,0)})
        addView(l)
    }

    private fun todayBudget():String{
        val budget=prefs.getInt("daily_budget_mb",500).toLong()*1024*1024
        val used=UsageFeatureUtils.totalMobileWifi(this,UsageFeatureUtils.today())
        return UsageFeatureUtils.formatBytes(used)+" از "+UsageFeatureUtils.formatBytes(budget)+"\nباقی‌مانده: "+UsageFeatureUtils.formatBytes((budget-used).coerceAtLeast(0))
    }
    private fun billingStatus():String{
        val p=UsageFeatureUtils.billingPeriod(this)
        val used=UsageFeatureUtils.queryTotal(this,ConnectivityManager.TYPE_MOBILE,p.start,p.end)
        val limit=(prefs.getFloat("monthly_limit_gb",10f)*1024*1024*1024).toLong()
        return "شروع چرخه: "+LanguageCalendar.date(this, p.start)+"\nمصرف سیم‌کارت: "+UsageFeatureUtils.formatBytes(used)+" از "+UsageFeatureUtils.formatBytes(limit)
    }
    private fun anomalyStatus():String{
        val today=UsageFeatureUtils.totalMobileWifi(this,UsageFeatureUtils.today())
        var sum=0L
        for(i in 1..7){
            val c=java.util.Calendar.getInstance().apply{add(java.util.Calendar.DAY_OF_YEAR,-i);set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}
            val e=(c.clone() as java.util.Calendar).apply{add(java.util.Calendar.DAY_OF_YEAR,1)}
            sum+=UsageFeatureUtils.totalMobileWifi(this,UsageFeatureUtils.Period(c.timeInMillis,e.timeInMillis))
        }
        val avg=sum/7.0; val mult=prefs.getFloat("anomaly_multiplier",2f)
        return if(avg>0 && today>avg*mult) "هشدار: مصرف امروز بیش از "+mult+" برابر میانگین ۷ روز اخیر است." else "مصرف امروز در محدوده معمول قرار دارد."
    }
    private fun networkStatus():String{
        val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n=cm.activeNetwork ?: return "اتصال فعال یافت نشد."
        val c=cm.getNetworkCapabilities(n) ?: return "وضعیت شبکه نامشخص است."
        val type=when{c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)->"Wi‑Fi";c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)->"سیم‌کارت";c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)->"Ethernet";else->"سایر"}
        return "نوع اتصال: "+type+"\nاینترنت: "+if(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))"در دسترس" else "در دسترس نیست"
    }
    private fun hotspotStatus():String{
        var rx=0L;var tx=0L;var found=false
        try{
            for(n in Collections.list(NetworkInterface.getNetworkInterfaces())){
                val name=n.name.lowercase()
                if(name.startsWith("ap")||name.contains("rndis")||name.contains("tether")||name.contains("usb")){
                    val r=android.net.TrafficStats.getRxBytes(name);val t=android.net.TrafficStats.getTxBytes(name)
                    if(r!=android.net.TrafficStats.UNSUPPORTED.toLong()&&t!=android.net.TrafficStats.UNSUPPORTED.toLong()){rx+=r;tx+=t;found=true}
                }
            }
        }catch(_:Exception){}
        return if(found)"ترافیک رابط‌های Hotspot/Tethering از زمان روشن شدن دستگاه: دریافت "+UsageFeatureUtils.formatBytes(rx)+"، ارسال "+UsageFeatureUtils.formatBytes(tx)
        else "API عمومی اندروید آمار مستقل Hotspot را برای این دستگاه ارائه نکرد؛ عدد ساختگی نمایش داده نمی‌شود."
    }
}
