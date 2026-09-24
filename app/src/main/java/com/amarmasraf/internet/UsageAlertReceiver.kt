package com.amarmasraf.internet

import android.app.NotificationManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.app.NotificationCompat
import java.util.Calendar

class UsageAlertReceiver:BroadcastReceiver(){
 override fun onReceive(c:Context,i:Intent?){
  val p=c.getSharedPreferences("settings",Context.MODE_PRIVATE)
  if(!p.getBoolean("notifications",true))return
  val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  val month=UsageFeatureUtils.billingPeriod(c)
  val total=UsageFeatureUtils.totalMobileWifi(c,month)
  val limit=p.getFloat("monthly_limit_gb",10f).toDouble()*1024*1024*1024
  val warning=p.getInt("warning_percent",80)
  val percent=if(limit>0)total/limit*100 else 0.0
  if(percent>=warning)notify(c,nm,7002,"هشدار سقف مصرف","مصرف چرخه قبض "+format(total)+" است؛ "+percent.toInt()+"٪ از سقف تعیین‌شده.")
  val budget=p.getInt("daily_budget_mb",500).toLong()*1024*1024
  val today=UsageFeatureUtils.totalMobileWifi(c,UsageFeatureUtils.today())
  if(today>=budget)notify(c,nm,7003,"هشدار بودجه روزانه","مصرف امروز "+format(today)+" از بودجه "+format(budget)+" عبور کرده است.")
  val avg=sevenDayAverage(c)
  val mult=p.getFloat("anomaly_multiplier",2f)
  if(avg>0&&today>avg*mult)notify(c,nm,7004,"مصرف غیرعادی","مصرف امروز بیش از "+mult+" برابر میانگین ۷ روز اخیر است.")
 }
 private fun sevenDayAverage(c:Context):Double{
  var sum=0L
  for(i in 1..7){
   val s=Calendar.getInstance().apply{add(Calendar.DAY_OF_YEAR,-i);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
   val e=(s.clone() as Calendar).apply{add(Calendar.DAY_OF_YEAR,1)}
   sum+=UsageFeatureUtils.totalMobileWifi(c,UsageFeatureUtils.Period(s.timeInMillis,e.timeInMillis))
  }
  return sum/7.0
 }
 private fun notify(c:Context,nm:NotificationManager,id:Int,title:String,text:String){
  nm.notify(id,NotificationCompat.Builder(c,"usage_alerts").setSmallIcon(android.R.drawable.stat_notify_sync_noanim).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build())
 }
 private fun format(b:Long):String{val gb=b/1024.0/1024.0/1024.0;return if(gb>=1)String.format("%.2f GB",gb)else String.format("%.0f MB",b/1024.0/1024.0)}
}