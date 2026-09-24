package com.amarmasraf.internet
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChartsActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private var mode = 0
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); build() }
    private fun build() {
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(22,30,22,30); setBackgroundColor(Color.parseColor("#0F172A")) }
        root.addView(TextView(this).apply { text="📊 نمودار مصرف اینترنت"; textSize=22f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0,0,0,20) })
        val tabs=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("روزانه","ماهانه","سالانه").forEachIndexed { i,t -> tabs.addView(tab(t,i==mode){mode=i;build()}) }
        root.addView(tabs)
        root.addView(TextView(this).apply { text=when(mode){0->"مقایسه ۳۰ روز اخیر";1->"مقایسه ۱۲ ماه اخیر";else->"مقایسه ۵ سال اخیر"}; textSize=15f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0,25,0,12) })
        val manager=getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        root.addView(createChart(manager,ConnectivityManager.TYPE_MOBILE,"سیم‌کارت","#38BDF8"))
        root.addView(createChart(manager,ConnectivityManager.TYPE_WIFI,"وای‌فای","#10B981"))
        root.addView(createComparisonSummary(manager))
        setContentView(ScrollView(this).apply{addView(root)})
    }
    private fun tab(title:String,selected:Boolean,action:()->Unit)=CardView(this).apply{
        radius=18f; setCardBackgroundColor(if(selected)Color.parseColor("#4F46E5")else Color.parseColor("#1E293B"))
        layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{setMargins(4,0,4,0)}
        setOnClickListener{action()}
        addView(TextView(context).apply{text=title;textSize=12f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(8,15,8,15)})
    }
    private fun createChart(manager:NetworkStatsManager,type:Int,title:String,color:String):CardView{
        val card=CardView(this).apply{radius=20f;setCardBackgroundColor(Color.parseColor("#1E293B"));layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(0,0,0,18)}}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,18)}
        box.addView(TextView(this).apply{text=title;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)})
        val ranges=when(mode){0->(29 downTo 0).map{dayRange(-it)};1->(11 downTo 0).map{monthRange(-it)};else->(4 downTo 0).map{yearRange(-it)}}
        val data=ranges.map{query(manager,type,it.first,it.second)}; val max=data.maxOrNull()?.coerceAtLeast(1L)?:1L
        ranges.indices.forEach{i->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,5,0,5)}
            row.addView(TextView(this).apply{text=when(mode){0->dayLabel(i);1->monthLabel(i);else->yearLabel(i)};textSize=9f;setTextColor(Color.parseColor("#94A3B8"));layoutParams=LinearLayout.LayoutParams(65,LinearLayout.LayoutParams.WRAP_CONTENT)})
            row.addView(TextView(this).apply{text=" ".repeat((data[i].toDouble()/max*22).toInt().coerceAtLeast(if(data[i]>0)1 else 0));setTextColor(Color.TRANSPARENT);setBackgroundColor(Color.parseColor(color));layoutParams=LinearLayout.LayoutParams(0,18,1f).apply{setMargins(5,0,5,0)}})
            row.addView(TextView(this).apply{text=format(data[i]);textSize=9f;setTextColor(Color.parseColor("#CBD5E1"))}); box.addView(row)
        }
        card.addView(box);return card
    }
    private fun createComparisonSummary(manager:NetworkStatsManager):CardView{
        val card=CardView(this).apply{radius=20f;setCardBackgroundColor(Color.parseColor("#1E293B"));layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(0,0,0,20)}}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,18)}
        box.addView(TextView(this).apply{text="مقایسه سیم‌کارت و وای‌فای";textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)})
        val range=when(mode){0->dayRange(0);1->monthRange(0);else->yearRange(0)}
        val mobile=query(manager,ConnectivityManager.TYPE_MOBILE,range.first,range.second);val wifi=query(manager,ConnectivityManager.TYPE_WIFI,range.first,range.second)
        box.addView(TextView(this).apply{text="سیم‌کارت: "+format(mobile)+"
وای‌فای: "+format(wifi)+"
مجموع: "+format(mobile+wifi);textSize=14f;setTextColor(Color.WHITE);setPadding(0,12,0,0)})
        card.addView(box);return card
    }
    private fun query(manager:NetworkStatsManager,type:Int,start:Long,end:Long):Long{
        return try{val s=manager.querySummary(type,null,start,end);val b=NetworkStats.Bucket();var total=0L;while(s.hasNextBucket()){s.getNextBucket(b);total+=b.rxBytes+b.txBytes};s.close();total}catch(_:Exception){0L}
    }
    private fun dayRange(offset:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.add(Calendar.DAY_OF_YEAR,offset);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return Pair(c.timeInMillis,c.timeInMillis+86400000L)}
    private fun monthRange(offset:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.add(Calendar.MONTH,offset);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val s=c.timeInMillis;c.add(Calendar.MONTH,1);return Pair(s,c.timeInMillis)}
    private fun yearRange(offset:Int):Pair<Long,Long>{val c=Calendar.getInstance();c.add(Calendar.YEAR,offset);c.set(Calendar.MONTH,0);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val s=c.timeInMillis;c.add(Calendar.YEAR,1);return Pair(s,c.timeInMillis)}
    private fun dayLabel(index:Int)=if(index==29)"امروز" else (29-index).toString()+" روز پیش"
    private fun monthLabel(index:Int):String{val c=Calendar.getInstance();c.add(Calendar.MONTH,index-11);return SimpleDateFormat("MMM",Locale("fa")).format(c.time)}
    private fun yearLabel(index:Int):String{val c=Calendar.getInstance();c.add(Calendar.YEAR,index-4);return SimpleDateFormat("yyyy",Locale.US).format(c.time)}
    private fun format(b:Long):String{val gb=b/1024.0/1024.0/1024.0;return if(gb>=1)String.format(Locale.US,"%.2f GB",gb)else String.format(Locale.US,"%.0f MB",b/1024.0/1024.0)}
}