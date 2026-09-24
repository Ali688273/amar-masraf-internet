package com.amarmasraf.internet
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class AppUsageActivity:AppCompatActivity(){
 data class Item(val name:String,val pkg:String,val bytes:Long)
 private lateinit var list:LinearLayout; private lateinit var search:EditText
 private var sort=0; private var all=emptyList<Item>()
 override fun onCreate(b:Bundle?){super.onCreate(b);build()}
 private fun build(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,22,18,30);setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))}
  root.addView(TextView(this).apply{text="📱 مصرف برنامه‌ها";textSize=22f;setTextColor(android.graphics.Color.WHITE);setPadding(0,0,0,15)})
  search=EditText(this).apply{hint="جستجوی نام یا package";setTextColor(android.graphics.Color.WHITE);setHintTextColor(android.graphics.Color.GRAY)}
  root.addView(search)
  val sorts=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  listOf("بیشترین مصرف","نام","کمترین مصرف").forEachIndexed{i,t->sorts.addView(Button(this).apply{text=t;setOnClickListener{sort=i;render()}})}
  root.addView(sorts);list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(list)
  search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){render()};override fun afterTextChanged(e:Editable?) {}})
  all=load();render();setContentView(ScrollView(this).apply{addView(root)})
 }
 private fun load():List<Item>{
  val m=getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
  val p=UsageFeatureUtils.billingPeriod(this);val map=HashMap<String,Long>()
  for(type in intArrayOf(ConnectivityManager.TYPE_MOBILE,ConnectivityManager.TYPE_WIFI))try{
   val s=m.querySummary(type,null,p.start,p.end);val b=NetworkStats.Bucket()
   while(s.hasNextBucket()){s.getNextBucket(b);val pkgs=packageManager.getPackagesForUid(b.uid);if(!pkgs.isNullOrEmpty())map[pkgs[0]]=(map[pkgs[0]]?:0L)+b.rxBytes+b.txBytes};s.close()
  }catch(_:Exception){}
  return map.mapNotNull{(pkg,bytes)->try{val a=packageManager.getApplicationInfo(pkg,0);Item(packageManager.getApplicationLabel(a).toString(),pkg,bytes)}catch(_:Exception){null}}
 }
 private fun render(){
  if(!::list.isInitialized)return
  val q=search.text.toString().trim()
  val items=all.filter{q.isEmpty()||it.name.contains(q,true)||it.pkg.contains(q,true)}.let{when(sort){1->it.sortedBy{it.name.lowercase()};2->it.sortedBy{it.bytes};else->it.sortedByDescending{it.bytes}}}
  list.removeAllViews()
  items.forEach{item->list.addView(CardView(this).apply{radius=16f;setCardBackgroundColor(android.graphics.Color.parseColor("#1E293B"));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,5,0,5)};addView(TextView(context).apply{text=item.name+"\n"+item.pkg+"\n"+UsageFeatureUtils.formatBytes(item.bytes);textSize=13f;setTextColor(android.graphics.Color.WHITE);setPadding(18,15,18,15)})})}
 }
}