package com.amarmasraf.internet
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
class UsageBootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent?){if(i?.action==Intent.ACTION_BOOT_COMPLETED)try{ContextCompat.startForegroundService(c,Intent(c,UsageMonitorService::class.java))}catch(_:Exception){}}}