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
 data class Item(val name:String,val pkg:String,val bytes:Long,val rx:Long,val tx:Long)
 private lateinit var list:LinearLayout; private lateinit var search:EditText
 private var sort=0; private var networkMode=2; private var periodMode=0; private var searchQuery=""; private var all=emptyList<Item>()
 override fun onCreate(b:Bundle?){super.onCreate(b);build()}
 private fun build(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,22,18,30);setBackgroundColor(ThemeManager.color(this@AppUsageActivity, "background"))}
  root.addView(TextView(this).apply{text="📱 مصرف برنامه‌ها";textSize=22f;setTextColor(ThemeManager.color(this@AppUsageActivity, "text"));setPadding(0,0,0,15)})
  search=EditText(this).apply{hint="جستجوی نام یا package";setTextColor(ThemeManager.color(this@AppUsageActivity, "text"));setHintTextColor(ThemeManager.color(this@AppUsageActivity, "muted"));setText(searchQuery)}
  root.addView(search)
  val networks=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  listOf("سیم‌کارت","وای‌فای","همه شبکه‌ها").forEachIndexed{i,t->networks.addView(Button(this).apply{text=t;setTextColor(if(networkMode==i)android.graphics.Color.WHITE else ThemeManager.color(this@AppUsageActivity,"text"));setBackgroundColor(ThemeManager.color(this@AppUsageActivity,if(networkMode==i)"primary" else "cardAlt"));setOnClickListener{networkMode=i;build()}})}
  root.addView(networks)
  val periods=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  listOf("چرخه قبض","امروز","این هفته","این ماه").forEachIndexed{i,t->periods.addView(Button(this).apply{text=t;textSize=10f;setTextColor(if(periodMode==i)android.graphics.Color.WHITE else ThemeManager.color(this@AppUsageActivity,"text"));setBackgroundColor(ThemeManager.color(this@AppUsageActivity,if(periodMode==i)"primary" else "cardAlt"));setOnClickListener{periodMode=i;build()}})}
  root.addView(periods)
  val sorts=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  listOf("بیشترین مصرف","نام","کمترین مصرف").forEachIndexed{i,t->sorts.addView(Button(this).apply{text=t;textSize=10f;setTextColor(if(sort==i)android.graphics.Color.WHITE else ThemeManager.color(this@AppUsageActivity,"text"));setBackgroundColor(ThemeManager.color(this@AppUsageActivity,if(sort==i)"primary" else "cardAlt"));setOnClickListener{sort=i;build()}})}
  root.addView(sorts);list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(list)
  search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){searchQuery=s?.toString().orEmpty();render()};override fun afterTextChanged(e:Editable?) {}})
  all=load();render();setContentView(ScrollView(this).apply{addView(root)})
 }
 private fun load():List<Item>{
  val m=getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
  val map=HashMap<String,Long>();val rxMap=HashMap<String,Long>();val txMap=HashMap<String,Long>()
  val types=when(networkMode){0->intArrayOf(ConnectivityManager.TYPE_MOBILE);1->intArrayOf(ConnectivityManager.TYPE_WIFI);else->intArrayOf(ConnectivityManager.TYPE_MOBILE,ConnectivityManager.TYPE_WIFI)}
  val p=selectedPeriod()
  for(type in types)try{
   val s=m.querySummary(type,null,p.start,p.end);val b=NetworkStats.Bucket()
   while(s.hasNextBucket()){s.getNextBucket(b);val pkgs=packageManager.getPackagesForUid(b.uid);if(!pkgs.isNullOrEmpty()){val pkg=pkgs[0];map[pkg]=(map[pkg]?:0L)+b.rxBytes+b.txBytes;rxMap[pkg]=(rxMap[pkg]?:0L)+b.rxBytes;txMap[pkg]=(txMap[pkg]?:0L)+b.txBytes}};s.close()
  }catch(_:Exception){}
  return map.mapNotNull{(pkg,bytes)->try{val a=packageManager.getApplicationInfo(pkg,0);Item(packageManager.getApplicationLabel(a).toString(),pkg,bytes,rxMap[pkg]?:0L,txMap[pkg]?:0L)}catch(_:Exception){null}}
 }
 private fun selectedPeriod():UsageFeatureUtils.Period{
  val now=System.currentTimeMillis()
  return when(periodMode){
   1->UsageFeatureUtils.today()
   2->{val c=java.util.Calendar.getInstance();val days=(c.get(java.util.Calendar.DAY_OF_WEEK)+5)%7;c.add(java.util.Calendar.DAY_OF_YEAR,-days);c.set(java.util.Calendar.HOUR_OF_DAY,0);c.set(java.util.Calendar.MINUTE,0);c.set(java.util.Calendar.SECOND,0);c.set(java.util.Calendar.MILLISECOND,0);UsageFeatureUtils.Period(c.timeInMillis,now)}
   3->{val c=java.util.Calendar.getInstance();c.set(java.util.Calendar.DAY_OF_MONTH,1);c.set(java.util.Calendar.HOUR_OF_DAY,0);c.set(java.util.Calendar.MINUTE,0);c.set(java.util.Calendar.SECOND,0);c.set(java.util.Calendar.MILLISECOND,0);UsageFeatureUtils.Period(c.timeInMillis,now)}
   else->UsageFeatureUtils.billingPeriod(this)
  }
 }
 private fun render(){
  if(!::list.isInitialized)return
  val q=search.text.toString().trim()
  val items=all.filter{q.isEmpty()||it.name.contains(q,true)||it.pkg.contains(q,true)}.let{when(sort){1->it.sortedBy{it.name.lowercase()};2->it.sortedBy{it.bytes};else->it.sortedByDescending{it.bytes}}}
  list.removeAllViews()
  if(items.isEmpty()){
   list.addView(TextView(this).apply{text=if(q.isEmpty())"برای این بازه داده‌ای برای برنامه‌ها ثبت نشده است." else "نتیجه‌ای برای «$q» پیدا نشد.";textSize=14f;gravity=android.view.Gravity.CENTER;setTextColor(ThemeManager.color(this@AppUsageActivity,"muted"));setPadding(20,35,20,35)})
   return
  }
  items.forEachIndexed{index,item->list.addView(CardView(this).apply{radius=16f;setCardBackgroundColor(ThemeManager.color(this@AppUsageActivity, if(index%2==0) "card" else "cardAlt"));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,5,0,5)};addView(TextView(context).apply{text=item.name+"\n"+item.pkg+"\nمجموع: "+UsageFeatureUtils.formatBytes(item.bytes)+"\n↓ دانلود: "+UsageFeatureUtils.formatBytes(item.rx)+"   ↑ آپلود: "+UsageFeatureUtils.formatBytes(item.tx);textSize=13f;setTextColor(ThemeManager.color(this@AppUsageActivity, "text"));setPadding(18,15,18,15)})})}
 }
}