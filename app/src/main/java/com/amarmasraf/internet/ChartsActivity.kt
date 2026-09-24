package com.amarmasraf.internet

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.text.SimpleDateFormat
import java.util.*

class ChartsActivity:AppCompatActivity(){
 private var mode=0
 private lateinit var root:LinearLayout
 override fun onCreate(b:Bundle?){super.onCreate(b);build()}
 private fun build(){
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,25,18,30);setBackgroundColor(Color.parseColor("#0F172A"))}
  root.addView(TextView(this).apply{text="📊 نمودار و تاریخچه مصرف";textSize=22f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setPadding(0,0,0,15)})
  val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  actions.addView(Button(this).apply{text="⚙ امکانات";setOnClickListener{startActivity(Intent(this@ChartsActivity,AdvancedUsageActivity::class.java))}})
  actions.addView(Button(this).apply{text="📄 CSV";setOnClickListener{startActivity(Intent(this@ChartsActivity,CsvExportActivity::class.java))}})
  root.addView(actions)
  val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  listOf("روزانه","ماهانه","سالانه").forEachIndexed{i,t->tabs.addView(tab(t,i==mode){mode=i;build()})}
  root.addView(tabs)
  root.addView(TextView(this).apply{text=when(mode){0->"۹۰ روز اخیر";1->"۲۴ ماه اخیر";else->"۱۰ سال اخیر"};textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setPadding(0,20,0,10)})
  val manager=getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
  if(mode==0)root.addView(createHourly(manager))
  root.addView(createChart(manager,ConnectivityManager.TYPE_MOBILE,"سیم‌کارت","#38BDF8"))
  root.addView(createChart(manager,ConnectivityManager.TYPE_WIFI,"وای‌فای","#10B981"))
  root.addView(summary(manager))
  setContentView(ScrollView(this).apply{addView(root)})
 }
 private fun tab(title:String,selected:Boolean,action:()->Unit)=CardView(this).apply{
  radius=18f;setCardBackgroundColor(if(selected)Color.parseColor("#4F46E5")else Color.parseColor("#1E293B"))
  layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(3,0,3,0)};setOnClickListener{action()}
  addView(TextView(context).apply{text=title;textSize=12f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(8,14,8,14)})
 }
 private fun createHourly(m:NetworkStatsManager):CardView{
  val card=CardView(this).apply{radius=20f;setCardBackgroundColor(Color.parseColor("#1E293B"));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,8,0,18)}}
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,18)}
  box.addView(TextView(this).apply{text="⏱ مصرف ساعتی امروز";textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)})
  val values=(0..23).map{hour->hourRange(hour).let{query(m,ConnectivityManager.TYPE_MOBILE,it.first,it.second)+query(m,ConnectivityManager.TYPE_WIFI,it.first,it.second)}}
  val max=values.maxOrNull()?.coerceAtLeast(1L)?:1L
  values.forEachIndexed{h,v->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
   row.addView(TextView(this).apply{text=String.format(Locale.US,"%02d:00",h);setTextColor(Color.LTGRAY);textSize=9f;layoutParams=LinearLayout.LayoutParams(48,-2)})
   row.addView(TextView(this).apply{text=" ".repeat((v.toDouble()/max*24).toInt().coerceAtLeast(if(v>0)1 else 0));setTextColor(Color.TRANSPARENT);setBackgroundColor(Color.parseColor("#F59E0B"));layoutParams=LinearLayout.LayoutParams(0,15,1f).apply{setMargins(4,3,4,3)}})
   row.addView(TextView(this).apply{text=format(v);setTextColor(Color.LTGRAY);textSize=8f})
   box.addView(row)
  }
  card.addView(box);return card
 }
 private fun createChart(m:NetworkStatsManager,type:Int,title:String,color:String):CardView{
  val card=CardView(this).apply{radius=20f;setCardBackgroundColor(Color.parseColor("#1E293B"));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,18)}}
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,18)}
  box.addView(TextView(this).apply{text=title;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)})
  val ranges=when(mode){0->(89 downTo 0).map{dayRange(-it)};1->(23 downTo 0).map{monthRange(-it)};else->(9 downTo 0).map{yearRange(-it)}}
  val data=ranges.map{query(m,type,it.first,it.second)};val max=data.maxOrNull()?.coerceAtLeast(1L)?:1L
  ranges.indices.forEach{i->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,3,0,3)}
   val offset=when(mode){0->i-89;1->i-23;else->i-9}
   row.addView(TextView(this).apply{text=when(mode){0->if(offset==0)"امروز" else (-offset).toString()+" روز پیش";1->SimpleDateFormat("MM/yyyy",Locale.US).format(monthDate(offset));else->SimpleDateFormat("yyyy",Locale.US).format(yearDate(offset))};textSize=8f;setTextColor(Color.parseColor("#94A3B8"));layoutParams=LinearLayout.LayoutParams(62,-2)})
   row.addView(TextView(this).apply{text=" ".repeat((data[i].toDouble()/max*22).toInt().coerceAtLeast(if(data[i]>0)1 else 0));setTextColor(Color.TRANSPARENT);setBackgroundColor(Color.parseColor(color));layoutParams=LinearLayout.LayoutParams(0,14,1f).apply{setMargins(4,0,4,0)}})
   row.addView(TextView(this).apply{text=format(data[i]);textSize=8f;setTextColor(Color.LTGRAY)})
   box.addView(row)
  }
  card.addView(box);return card
 }
 private fun summary(m:NetworkStatsManager)=CardView(this).apply{
  radius=20f;setCardBackgroundColor(Color.parseColor("#1E293B"));layoutParams=LinearLayout.LayoutParams(-1,-2)
  val p=when(mode){0->dayRange(0);1->monthRange(0);else->yearRange(0)}
  val a=query(m,ConnectivityManager.TYPE_MOBILE,p.first,p.second);val w=query(m,ConnectivityManager.TYPE_WIFI,p.first,p.second)
  addView(TextView(context).apply{text="مقایسه بازه فعلی\nسیم‌کارت: "+format(a)+"\nWi‑Fi: "+format(w)+"\nمجموع: "+format(a+w);textSize=14f;setTextColor(Color.WHITE);setPadding(18,18,18,18)})
 }
 private fun query(m:NetworkStatsManager,t:Int,s:Long,e:Long):Long=try{val x=m.querySummary(t,null,s,e);val b=NetworkStats.Bucket();var z=0L;while(x.hasNextBucket()){x.getNextBucket(b);z+=b.rxBytes+b.txBytes};x.close();z}catch(_:Exception){0L}
 private fun dayRange(o:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.add(Calendar.DAY_OF_YEAR,o);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return Pair(c.timeInMillis,c.timeInMillis+86400000L)}
 private fun hourRange(h:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val s=c.timeInMillis;return Pair(s,s+3600000L)}
 private fun monthRange(o:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.add(Calendar.MONTH,o);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val s=c.timeInMillis;c.add(Calendar.MONTH,1);return Pair(s,c.timeInMillis)}
 private fun yearRange(o:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.add(Calendar.YEAR,o);c.set(Calendar.MONTH,0);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val s=c.timeInMillis;c.add(Calendar.YEAR,1);return Pair(s,c.timeInMillis)}
 private fun monthDate(o:Int):Date{val c=Calendar.getInstance();c.add(Calendar.MONTH,o);return c.time}
 private fun yearDate(o:Int):Date{val c=Calendar.getInstance();c.add(Calendar.YEAR,o);return c.time}
 private fun format(b:Long):String{val gb=b/1024.0/1024.0/1024.0;return if(gb>=1)String.format(Locale.US,"%.2f GB",gb)else String.format(Locale.US,"%.0f MB",b/1024.0/1024.0)}
}