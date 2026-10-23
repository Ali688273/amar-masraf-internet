package com.amarmasraf.internet
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import java.util.*

class CsvExportActivity:AppCompatActivity(){
 private var pending=""
 private var permissionMissing=false
 override fun onCreate(b:Bundle?){super.onCreate(b);if(UsageFeatureUtils.hasUsageAccess(this))export()else showPermissionScreen()}
 override fun onResume(){super.onResume();if(permissionMissing&&UsageFeatureUtils.hasUsageAccess(this)){permissionMissing=false;export()}}
 private fun showPermissionScreen(){
  permissionMissing=true
  val page=android.widget.LinearLayout(this).apply{orientation=android.widget.LinearLayout.VERTICAL;gravity=android.view.Gravity.CENTER;setPadding(24,30,24,30)}
  page.addView(android.widget.TextView(this).apply{text=LanguageCalendar.text(this, "برای خروجی دقیق CSV، ابتدا دسترسی «آمار مصرف» را فعال کنید.", "Enable Usage Access before exporting an accurate CSV report.");textSize=16f;gravity=android.view.Gravity.CENTER})
  page.addView(android.widget.Button(this).apply{text=LanguageCalendar.text(this, "باز کردن تنظیمات دسترسی", "Open access settings");setOnClickListener{startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))}})
  setContentView(page)
 }
 private fun export(){
  val page=android.widget.LinearLayout(this).apply{orientation=android.widget.LinearLayout.VERTICAL;gravity=android.view.Gravity.CENTER;setPadding(24,30,24,30)}
  page.addView(android.widget.TextView(this).apply{text=LanguageCalendar.text(this, "در حال آماده‌سازی گزارش CSV…", "Preparing CSV report…");textSize=16f;gravity=android.view.Gravity.CENTER})
  setContentView(page)
  lifecycleScope.launch{
   pending=withContext(Dispatchers.IO){buildCsv()}
   if(isFinishing||isDestroyed)return@launch
   startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/csv";putExtra(Intent.EXTRA_TITLE,"internet_usage.csv")},9002)
  }
 }
 private fun buildCsv():String{
  val p=UsageFeatureUtils.billingPeriod(this)
  val sb=StringBuilder("date,mobile_bytes,wifi_bytes,total_bytes\n")
  val c=Calendar.getInstance().apply{timeInMillis=p.start}
  while(c.timeInMillis<p.end){
   val s=c.timeInMillis
   c.add(Calendar.DAY_OF_YEAR,1)
   val e=minOf(c.timeInMillis,p.end)
   val m=UsageFeatureUtils.queryTotal(this,ConnectivityManager.TYPE_MOBILE,s,e)
   val w=UsageFeatureUtils.queryTotal(this,ConnectivityManager.TYPE_WIFI,s,e)
   sb.append(LanguageCalendar.date(this, s, "-")).append(',').append(m).append(',').append(w).append(',').append(m+w).append('\n')
  }
  return sb.toString()
 }
 override fun onActivityResult(r:Int,c:Int,d:Intent?){
  super.onActivityResult(r,c,d)
  if(r==9002&&c==RESULT_OK&&d?.data!=null){
   try{
    val stream=contentResolver.openOutputStream(d.data!!)
    if(stream==null) throw java.io.IOException("Unable to open destination")
    stream.use{it.write(pending.toByteArray(Charsets.UTF_8))}
    android.widget.Toast.makeText(this,"CSV ذخیره شد",android.widget.Toast.LENGTH_SHORT).show()
   }catch(e:Exception){
    android.widget.Toast.makeText(this,"ذخیره CSV ناموفق بود: "+(e.localizedMessage?: "خطای فایل"),android.widget.Toast.LENGTH_LONG).show()
   }
  }
  finish()
 }
}