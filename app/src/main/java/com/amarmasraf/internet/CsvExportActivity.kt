package com.amarmasraf.internet
import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.*

class CsvExportActivity:Activity(){
 private var pending=""
 override fun onCreate(b:Bundle?){super.onCreate(b);export()}
 private fun export(){
  val p=UsageFeatureUtils.billingPeriod(this);val sdf=SimpleDateFormat("yyyy-MM-dd",Locale.US);val sb=StringBuilder("date,mobile_bytes,wifi_bytes,total_bytes\n")
  val c=Calendar.getInstance().apply{timeInMillis=p.start}
  while(c.timeInMillis<p.end){val s=c.timeInMillis;c.add(Calendar.DAY_OF_YEAR,1);val e=minOf(c.timeInMillis,p.end);val m=UsageFeatureUtils.queryTotal(this,ConnectivityManager.TYPE_MOBILE,s,e);val w=UsageFeatureUtils.queryTotal(this,ConnectivityManager.TYPE_WIFI,s,e);sb.append(sdf.format(Date(s))).append(',').append(m).append(',').append(w).append(',').append(m+w).append('\n')}
  pending=sb.toString()
  startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="text/csv";putExtra(Intent.EXTRA_TITLE,"internet_usage.csv")},9002)
 }
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==9002&&c==RESULT_OK&&d?.data!=null){contentResolver.openOutputStream(d.data!!)?.use{it.write(pending.toByteArray(Charsets.UTF_8))};android.widget.Toast.makeText(this,"CSV ذخیره شد",android.widget.Toast.LENGTH_SHORT).show()};finish()}
}