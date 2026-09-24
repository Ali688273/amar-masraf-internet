package com.amarmasraf.internet

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInterface
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
            setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
        }
        root.addView(TextView(this).apply {
            text="⚙️ امکانات پیشرفته مصرف"; textSize=22f; setTextColor(android.graphics.Color.WHITE); setPadding(0,0,0,15)
        })

        val daily=EditText(this).apply { hint="بودجه روزانه (مگابایت)"; setText(prefs.getInt("daily_budget_mb",500).toString()); inputType=2 }
        root.addView(daily)
        val billing=EditText(this).apply { hint="روز شروع چرخه قبض، ۱ تا ۲۸"; setText(prefs.getInt("billing_start_day",1).toString()); inputType=2 }
        root.addView(billing)
        val anomaly=EditText(this).apply { hint="ضریب هشدار مصرف غیرعادی"; setText(prefs.getFloat("anomaly_multiplier",2f).toString()); inputType=8194 }
        root.addView(anomaly)

        root.addView(Button(this).apply {
            text="ذخیره تنظیمات"
            setOnClickListener {
                prefs.edit()
                    .putInt("daily_budget_mb",daily.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 500)
                    .putInt("billing_start_day",billing.text.toString().toIntOrNull()?.coerceIn(1,28) ?: 1)
                    .putFloat("anomaly_multiplier",anomaly.text.toString().toFloatOrNull()?.coerceAtLeast(1.2f) ?: 2f).apply()
                Toast.makeText(this@AdvancedUsageActivity,"تنظیمات ذخیره شد",Toast.LENGTH_SHORT).show()
                build()
            }
        })

        root.addView(card("📅 بودجه امروز", todayBudget()))
        root.addView(card("📊 چرخه قبض", billingStatus()))
        root.addView(card("⚠️ مصرف غیرعادی", anomalyStatus()))
        root.addView(card("📶 اطلاعات شبکه", networkStatus()))
        root.addView(card("📡 Hotspot / Tethering", hotspotStatus()))

        root.addView(Button(this).apply {
            text="🔎 جستجو و مرتب‌سازی برنامه‌ها"
            setOnClickListener { startActivity(Intent(this@AdvancedUsageActivity,AppUsageActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text="📄 خروجی CSV"
            setOnClickListener { startActivity(Intent(this@AdvancedUsageActivity,CsvExportActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text="🔔 فعال‌سازی نمایش مصرف در نوار اعلان"
            setOnClickListener {
                if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),8001)
                androidx.core.content.ContextCompat.startForegroundService(this@AdvancedUsageActivity,Intent(this@AdvancedUsageActivity,UsageMonitorService::class.java))
                Toast.makeText(this@AdvancedUsageActivity,"نمایش مصرف در نوار اعلان فعال شد",Toast.LENGTH_SHORT).show()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun card(title:String,body:String)=CardView(this).apply{
        radius=18f; setCardBackgroundColor(android.graphics.Color.parseColor("#1E293B"))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,8,0,4)}
        val l=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(18,15,18,15)}
        l.addView(TextView(context).apply{text=title;textSize=15f;setTextColor(android.graphics.Color.WHITE)})
        l.addView(TextView(context).apply{text=body;textSize=13f;setTextColor(android.graphics.Color.LTGRAY);setPadding(0,7,0,0)})
        addView(l)
    }

    private fun todayBudget():String{
        val budget=prefs.getInt("daily_budget_mb",500).toLong()*1024*1024
        val used=UsageFeatureUtils.totalMobileWifi(this,UsageFeatureUtils.today())
        return UsageFeatureUtils.formatBytes(used)+" از "+UsageFeatureUtils.formatBytes(budget)+"\nباقی‌مانده: "+UsageFeatureUtils.formatBytes((budget-used).coerceAtLeast(0))
    }
    private fun billingStatus():String{
        val p=UsageFeatureUtils.billingPeriod(this)
        val used=UsageFeatureUtils.totalMobileWifi(this,p)
        val limit=(prefs.getFloat("monthly_limit_gb",10f)*1024*1024*1024).toLong()
        return "شروع چرخه: "+java.text.SimpleDateFormat("yyyy/MM/dd",java.util.Locale.US).format(java.util.Date(p.start))+"\nمصرف: "+UsageFeatureUtils.formatBytes(used)+" از "+UsageFeatureUtils.formatBytes(limit)
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
