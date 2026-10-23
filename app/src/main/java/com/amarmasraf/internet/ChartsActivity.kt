package com.amarmasraf.internet

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ChartsActivity:AppCompatActivity(){
 private var mode=0
 private lateinit var root:LinearLayout
 private var permissionMissing=false
 override fun onCreate(b:Bundle?){super.onCreate(b);if(UsageFeatureUtils.hasUsageAccess(this))build()else showPermissionScreen()}
 override fun onResume(){super.onResume();if(permissionMissing&&UsageFeatureUtils.hasUsageAccess(this)){permissionMissing=false;build()}}
 private fun showPermissionScreen(){
  permissionMissing=true
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(24,30,24,30);setBackgroundColor(ThemeManager.color(this@ChartsActivity,"background"))}
  root.addView(TextView(this).apply{text="برای محاسبه نمودارها، دسترسی «آمار مصرف» را در تنظیمات اندروید فعال کنید.";textSize=16f;gravity=Gravity.CENTER;setTextColor(ThemeManager.color(this@ChartsActivity,"text"))})
  root.addView(Button(this).apply{text="باز کردن تنظیمات دسترسی";setOnClickListener{startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))}})
  setContentView(root)
 }
 private data class Snapshot(
  val hourly:List<Long>,
  val mobile:List<Long>,
  val wifi:List<Long>,
  val currentMobile:Long,
  val currentWifi:Long,
  val previousTotal:Long
 )
 private var loadJob:kotlinx.coroutines.Job?=null
 private fun build(){
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,25,18,30);setBackgroundColor(ThemeManager.color(this@ChartsActivity, "background"))}
  root.addView(TextView(this).apply{text="📊 نمودار و تاریخچه مصرف";textSize=22f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.color(this@ChartsActivity, "text"));setPadding(0,0,0,15)})
  val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  actions.addView(Button(this).apply{text="⚙ امکانات";setOnClickListener{startActivity(Intent(this@ChartsActivity,AdvancedUsageActivity::class.java))}})
  actions.addView(Button(this).apply{text="📄 CSV";setOnClickListener{startActivity(Intent(this@ChartsActivity,CsvExportActivity::class.java))}})
  root.addView(actions)
  val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  listOf("روزانه","ماهانه","سالانه").forEachIndexed{i,t->tabs.addView(tab(t,i==mode){mode=i;build()})}
  root.addView(tabs)
  root.addView(TextView(this).apply{text=when(mode){0->"۹۰ روز اخیر";1->"۲۴ ماه اخیر";else->"۱۰ سال اخیر"};textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.color(this@ChartsActivity, "text"));setPadding(0,20,0,10)})
  val loading=TextView(this).apply{text="در حال محاسبه آمار…";textSize=14f;gravity=Gravity.CENTER;setTextColor(ThemeManager.color(this@ChartsActivity,"muted"));setPadding(0,35,0,35)}
  root.addView(loading)
  val targetRoot=root
  val selectedMode=mode
  setContentView(ScrollView(this).apply{addView(targetRoot)})
  loadJob?.cancel()
  loadJob=lifecycleScope.launch {
   val snapshot=withContext(Dispatchers.IO){loadSnapshot(selectedMode)}
   if(isFinishing || isDestroyed || mode!=selectedMode || root!==targetRoot)return@launch
   targetRoot.removeView(loading)
   if(selectedMode==0)targetRoot.addView(createHourly(snapshot.hourly))
   targetRoot.addView(createChart("سیم‌کارت",snapshot.mobile))
   targetRoot.addView(createChart("وای‌فای",snapshot.wifi))
   targetRoot.addView(summary(snapshot))
  }
 }
 private fun loadSnapshot(selectedMode:Int):Snapshot{
  val manager=getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
  val hourly=if(selectedMode==0)(0..23).map{h->hourRange(h).let{query(manager,ConnectivityManager.TYPE_MOBILE,it.first,it.second)+query(manager,ConnectivityManager.TYPE_WIFI,it.first,it.second)}}else emptyList()
  val ranges=when(selectedMode){
   0->(89 downTo 0).map{dayRange(-it)}
   1->(23 downTo 0).map{monthRange(-it)}
   else->(9 downTo 0).map{yearRange(-it)}
  }
  val mobile=ranges.map{query(manager,ConnectivityManager.TYPE_MOBILE,it.first,it.second)}
  val wifi=ranges.map{query(manager,ConnectivityManager.TYPE_WIFI,it.first,it.second)}
  val current=when(selectedMode){0->dayRange(0);1->monthRange(0);else->yearRange(0)}
  val previous=when(selectedMode){0->dayRange(-1);1->monthRange(-1);else->yearRange(-1)}
  val now=System.currentTimeMillis()
  val currentMobile=query(manager,ConnectivityManager.TYPE_MOBILE,current.first,minOf(current.second,now))
  val currentWifi=query(manager,ConnectivityManager.TYPE_WIFI,current.first,minOf(current.second,now))
  val previousTotal=query(manager,ConnectivityManager.TYPE_MOBILE,previous.first,previous.second)+query(manager,ConnectivityManager.TYPE_WIFI,previous.first,previous.second)
  return Snapshot(hourly,mobile,wifi,currentMobile,currentWifi,previousTotal)
 }
 private fun tab(title:String,selected:Boolean,action:()->Unit)=CardView(this).apply{
  radius=18f;setCardBackgroundColor(if(selected)ThemeManager.color(this@ChartsActivity, "primary")else ThemeManager.color(this@ChartsActivity, "card"))
  layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(3,0,3,0)};setOnClickListener{action()}
  addView(TextView(context).apply{text=title;textSize=12f;setTextColor(if(selected) Color.WHITE else ThemeManager.color(this@ChartsActivity, "text"));gravity=Gravity.CENTER;setPadding(8,14,8,14)})
 }
 private fun createHourly(values:List<Long>):CardView{
  val card=CardView(this).apply{radius=20f;setCardBackgroundColor(ThemeManager.color(this@ChartsActivity, "cardAlt"));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,8,0,18)}}
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,18)}
  box.addView(TextView(this).apply{text="⏱ مصرف ساعتی امروز";textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.color(this@ChartsActivity, "text"))})
  val maxValue=values.maxOrNull()?.coerceAtLeast(1L)?:1L
  values.forEachIndexed{h,v->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
   row.addView(TextView(this).apply{text=String.format(Locale.US,"%02d:00",h);setTextColor(ThemeManager.color(this@ChartsActivity, "muted"));textSize=9f;layoutParams=LinearLayout.LayoutParams(48,-2)})
   row.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply{max=1000;progress=((v.toDouble()/maxValue)*1000).toInt().coerceIn(0,1000);progressTintList=ColorStateList.valueOf(ThemeManager.color(this@ChartsActivity, "accent"));progressBackgroundTintList=ColorStateList.valueOf(ThemeManager.color(this@ChartsActivity, "background"));layoutParams=LinearLayout.LayoutParams(0,15,1f).apply{setMargins(4,3,4,3)}})
   row.addView(TextView(this).apply{text=format(v);setTextColor(ThemeManager.color(this@ChartsActivity, "muted"));textSize=8f})
   box.addView(row)
  }
  card.addView(box);return card
 }
 private fun createChart(title:String,data:List<Long>):CardView{
  val card=CardView(this).apply{radius=20f;setCardBackgroundColor(ThemeManager.color(this@ChartsActivity, "card"));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,18)}}
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,18)}
  box.addView(TextView(this).apply{text=title;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.color(this@ChartsActivity, "text"))})
  val maxValue=data.maxOrNull()?.coerceAtLeast(1L)?:1L
  data.indices.forEach{i->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,3,0,3)}
   val offset=i-(data.size-1)
   row.addView(TextView(this).apply{text=when(mode){0->if(offset==0)"امروز" else (-offset).toString()+" روز پیش";1->SimpleDateFormat("MM/yyyy",Locale.US).format(monthDate(offset));else->SimpleDateFormat("yyyy",Locale.US).format(yearDate(offset))};textSize=8f;setTextColor(ThemeManager.color(this@ChartsActivity, "muted"));layoutParams=LinearLayout.LayoutParams(62,-2)})
   row.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply{max=1000;progress=((data[i].toDouble()/maxValue)*1000).toInt().coerceIn(0,1000);progressTintList=ColorStateList.valueOf(ThemeManager.color(this@ChartsActivity, if (title == "سیم‌کارت") "download" else "upload"));progressBackgroundTintList=ColorStateList.valueOf(ThemeManager.color(this@ChartsActivity, "background"));layoutParams=LinearLayout.LayoutParams(0,14,1f).apply{setMargins(4,0,4,0)}})
   row.addView(TextView(this).apply{text=format(data[i]);textSize=8f;setTextColor(ThemeManager.color(this@ChartsActivity, "muted"))})
   box.addView(row)
  }
  card.addView(box);return card
 }
 private fun summary(snapshot:Snapshot)=CardView(this).apply{
  radius=20f;setCardBackgroundColor(ThemeManager.color(this@ChartsActivity, "cardAlt"));layoutParams=LinearLayout.LayoutParams(-1,-2)
  val current=snapshot.currentMobile+snapshot.currentWifi
  val prior=snapshot.previousTotal
  val change=if(prior>0)((current-prior).toDouble()/prior*100.0) else 0.0
  val comparison=if(prior<=0)"برای بازه قبلی داده کافی نیست" else String.format(Locale.US,"تغییر نسبت به بازه قبل: %.0f%% %s",kotlin.math.abs(change),if(change>=0)"بیشتر" else "کمتر")
  addView(TextView(context).apply{text="مقایسه بازه فعلی با بازه قبل\nسیم‌کارت: "+format(snapshot.currentMobile)+"\nWi‑Fi: "+format(snapshot.currentWifi)+"\nمجموع فعلی: "+format(current)+"\nمجموع بازه قبل: "+format(prior)+"\n"+comparison;textSize=14f;setTextColor(ThemeManager.color(this@ChartsActivity, "text"));setPadding(18,18,18,18)})
 }
 private fun query(m:NetworkStatsManager,t:Int,s:Long,e:Long):Long=try{val x=m.querySummary(t,null,s,e);val b=NetworkStats.Bucket();var z=0L;while(x.hasNextBucket()){x.getNextBucket(b);z+=b.rxBytes+b.txBytes};x.close();z}catch(_:Exception){0L}
 private fun dayRange(o:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.add(Calendar.DAY_OF_YEAR,o);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val s=c.timeInMillis;c.add(Calendar.DAY_OF_YEAR,1);return Pair(s,c.timeInMillis)}
 private fun hourRange(h:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val s=c.timeInMillis;return Pair(s,s+3600000L)}
 private fun monthRange(o:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.add(Calendar.MONTH,o);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val s=c.timeInMillis;c.add(Calendar.MONTH,1);return Pair(s,c.timeInMillis)}
 private fun yearRange(o:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.add(Calendar.YEAR,o);c.set(Calendar.MONTH,0);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val s=c.timeInMillis;c.add(Calendar.YEAR,1);return Pair(s,c.timeInMillis)}
 private fun monthDate(o:Int):Date{val c=Calendar.getInstance();c.add(Calendar.MONTH,o);return c.time}
 private fun yearDate(o:Int):Date{val c=Calendar.getInstance();c.add(Calendar.YEAR,o);return c.time}
 private fun format(b:Long):String{val gb=b/1024.0/1024.0/1024.0;return if(gb>=1)String.format(Locale.US,"%.2f GB",gb)else String.format(Locale.US,"%.0f MB",b/1024.0/1024.0)}
}