package com.amarmasraf.internet

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
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
        
        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl("file:///android_asset/index.html")

        TapsellPlus.initialize(this, "aanbcpksderreqsnknjnookaelpsgibjbrjbcfsicndoqmkimibmncsnfqrrbkccaiqnjb", object : TapsellPlusInitListener {
            override fun onInitializeSuccess(adNetworks: AdNetworks) {
                TapsellPlus.requestStandardBannerAd(
                    this@MainActivity,
                    "6a868ecdaf056d371d5ba541",
                    TapsellPlusBannerType.BANNER_320x50,
                    object : AdRequestCallback() {
                        override fun response(tapsellPlusAdModel: TapsellPlusAdModel) {
                            TapsellPlus.showStandardBannerAd(
                                this@MainActivity,
                                tapsellPlusAdModel.responseId,
                                findViewById(android.R.id.content)
                            )
                        }
                        override fun error(message: String) {}
                    }
                )
            }
            override fun onInitializeFailed(adNetworkError: AdNetworkError) {}
        })
    }
}
