package com.amarmasraf.internet
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import android.net.ConnectivityManager
class UsageWidgetProvider:AppWidgetProvider(){
 override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){ids.forEach{update(c,m,it)}}
 private fun update(c:Context,m:AppWidgetManager,id:Int){val p=UsageFeatureUtils.billingPeriod(c);val a=UsageFeatureUtils.queryTotal(c,ConnectivityManager.TYPE_MOBILE,p.start,p.end);val w=UsageFeatureUtils.queryTotal(c,ConnectivityManager.TYPE_WIFI,p.start,p.end);val v=RemoteViews(c.packageName,R.layout.widget_usage);v.setTextViewText(R.id.widget_value,"سیم‌کارت "+UsageFeatureUtils.formatBytes(a)+"\nWi‑Fi "+UsageFeatureUtils.formatBytes(w));m.updateAppWidget(id,v)}
}