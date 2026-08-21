package com.amarmasraf.internet

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusBannerType
import ir.tapsell.plus.listener.AdRequestCallback

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val statusTv = TextView(this).apply {
            text = "در حال اتصال به تپسل..."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
        }
        layout.addView(statusTv)

        val adContainer = LinearLayout(this).apply {
            id = android.view.View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        layout.addView(adContainer)

        setContentView(layout)

        val tapsellKey = "aanbcpksderreqsnknjnookaelpsgibjbrjbcfsicndoqmkimibmncsnfqrrbkccaiqnjb"
        val zoneId = "6a868ecdaf056d371d5ba541"

        TapsellPlus.initialize(this, tapsellKey, object : TapsellPlusInitListener {
            override fun onInitializeSuccess(adNetworks: AdNetworks) {
                statusTv.text = "تپسل فعال شد. در حال دریافت تبلیغ..."
                
                TapsellPlus.requestStandardBannerAd(
                    this@MainActivity,
                    zoneId,
                    TapsellPlusBannerType.BANNER_320x50,
                    object : AdRequestCallback() {
                        override fun response(tapsellPlusAdModel: TapsellPlusAdModel) {
                            statusTv.text = "تبلیغ دریافت شد!"
                            TapsellPlus.showStandardBannerAd(
                                this@MainActivity,
                                tapsellPlusAdModel.responseId,
                                findViewById(adContainer.id)
                            )
                        }
                        override fun error(message: String) {
                            statusTv.text = "خطا در دریافت تبلیغ: $message"
                        }
                    }
                )
            }

            override fun onInitializeFailed(adNetworkError: AdNetworkError) {
                statusTv.text = "خطا در اتصال اولیه به تپسل"
            }
        })
    }
}
