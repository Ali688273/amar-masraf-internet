package com.amarmasraf.internet
import android.app.*
import android.content.Intent
import android.net.ConnectivityManager
import android.os.IBinder
import androidx.core.app.NotificationCompat

class UsageMonitorService:Service(){
 override fun onCreate(){super.onCreate();createChannel();startForeground(7101,notification())}
 override fun onStartCommand(i:Intent?,flags:Int,id:Int):Int{startForeground(7101,notification());return START_STICKY}
 private fun createChannel(){if(android.os.Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("live_usage","مصرف لحظه‌ای اینترنت",NotificationManager.IMPORTANCE_LOW))}
 private fun notification():Notification{
  val p=UsageFeatureUtils.billingPeriod(this);val m=UsageFeatureUtils.queryTotal(this,ConnectivityManager.TYPE_MOBILE,p.start,System.currentTimeMillis());val w=UsageFeatureUtils.queryTotal(this,ConnectivityManager.TYPE_WIFI,p.start,System.currentTimeMillis())
  val pi=PendingIntent.getActivity(this,7102,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return NotificationCompat.Builder(this,"live_usage").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("آمار مصرف اینترنت").setContentText("سیم‌کارت "+UsageFeatureUtils.formatBytes(m)+" • Wi‑Fi "+UsageFeatureUtils.formatBytes(w)).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build()
 }
 override fun onBind(i:Intent?):IBinder?=null
}