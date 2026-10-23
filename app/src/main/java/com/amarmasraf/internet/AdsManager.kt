package com.amarmasraf.internet

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryAdListener
import com.adivery.sdk.AdiveryBannerAdView
import com.adivery.sdk.AdiveryNativeAdView
import com.adivery.sdk.BannerSize
import ir.tapsell.plus.AdHolder
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.AdShowListener
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusBannerType
import ir.tapsell.plus.TapsellPlusVideoAdHolder
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusErrorModel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized ad loading. A missing ad/network must never block app functionality.
 * Full-screen ads are rate limited; rewarded ads are only shown after an explicit user action.
 */
object AdsManager {
    private const val TAG = "AmarMasrafAds"

    private const val TAPSELL_NATIVE_BANNER = "6a868ecdaf056d371d5ba541"
    private const val TAPSELL_REWARDED = "6adb201bbe0ef33af833007d"
    private const val TAPSELL_INTERSTITIAL = "6adb20409252922579632a98"
    private const val TAPSELL_PREVIEW_VIDEO = "6adb20649252922579632a99"
    private const val TAPSELL_NATIVE_VIDEO = "6adb209a9252922579632a9a"
    private const val TAPSELL_STANDARD_BANNER = "6adb20c3fc418616211cb77c"
    private const val TAPSELL_INSTANT_BANNER = "6adb20eafc418616211cb77d"

    private const val ADIVERY_INTERSTITIAL = "278a1ffe-0547-4e8f-9dea-0f15bc015b89"
    private const val ADIVERY_REWARDED = "1df93e2b-e555-4800-bf2f-ba8347f4f081"
    private const val ADIVERY_BANNER = "0d1e991c-c67a-40ee-8ffa-ea1bddbdf425"
    private const val ADIVERY_APP_OPEN = "48920df4-8e72-4542-b4ce-dba3f0a47406"
    private const val ADIVERY_PREVIEW_VIDEO = "c449c4f7-c571-42af-a580-645bef76163e"
    private const val ADIVERY_NATIVE = "3a700fe5-3de4-44cf-9d6b-4d7348224983"

    @Volatile private var tapsellReady = false
    @Volatile private var adiveryPreloaded = false
    @Volatile private var lastFullScreenShownAt = 0L
    @Volatile private var tapsellInterstitialResponseId: String? = null
    @Volatile private var tapsellRewardedResponseId: String? = null
    private val initializing = AtomicBoolean(false)
    private const val FULL_SCREEN_COOLDOWN_MS = 5 * 60 * 1000L
    private const val APP_OPEN_COOLDOWN_MS = 30 * 60 * 1000L

    fun initialize(context: Context) {
        val app = context.applicationContext
        if (!initializing.compareAndSet(false, true)) return

        try {
            Adivery.setLoggingEnabled(false)
            Adivery.configure(app, BuildConfig.ADIVERY_APP_KEY)
            Adivery.prepareInterstitialAd(app, ADIVERY_INTERSTITIAL)
            Adivery.prepareRewardedAd(app, ADIVERY_REWARDED)
            Adivery.prepareAppOpenAd(app, ADIVERY_APP_OPEN)
        } catch (e: Exception) {
            Log.w(TAG, "Adivery initialization/preload failed", e)
        }

        try {
            TapsellPlus.initialize(app, BuildConfig.TAPSELL_APP_KEY, object : TapsellPlusInitListener() {
                override fun onInitializeSuccess(adNetworks: AdNetworks) {
                    tapsellReady = true
                    Log.d(TAG, "Tapsell initialized: ${adNetworks.name}")
                }

                override fun onInitializeFailed(adNetworks: AdNetworks, error: AdNetworkError) {
                    tapsellReady = false
                    Log.w(TAG, "Tapsell initialization failed: ${error.errorMessage}")
                }
            })
        } catch (e: Exception) {
            tapsellReady = false
            Log.w(TAG, "Tapsell initialization call failed", e)
        }
    }

    fun attachPersistentBanner(activity: Activity, container: FrameLayout) {
        container.removeAllViews()
        container.minimumHeight = (50 * activity.resources.displayMetrics.density).toInt()
        container.visibility = android.view.View.VISIBLE
        if (tapsellReady) {
            requestTapsellBanner(activity, container)
        } else {
            showAdiveryBanner(activity, container)
        }
    }

    private fun requestTapsellBanner(activity: Activity, container: FrameLayout) {
        requestTapsellBannerForZone(activity, container, T_STANDARD_BANNER, tryInstantBanner = true)
    }

    private fun requestTapsellBannerForZone(
        activity: Activity,
        container: FrameLayout,
        zoneId: String,
        tryInstantBanner: Boolean
    ) {
        try {
            TapsellPlus.requestStandardBannerAd(activity, zoneId, TapsellPlusBannerType.BANNER_320x50,
                object : AdRequestCallback() {
                    override fun response(model: TapsellPlusAdModel) {
                        if (activity.isFinishing || activity.isDestroyed) return
                        try {
                            TapsellPlus.showStandardBannerAd(activity, model.responseId, container,
                                object : AdShowListener() {
                                    override fun onOpened(model: TapsellPlusAdModel) = Unit
                                    override fun onError(error: TapsellPlusErrorModel) {
                                        Log.w(TAG, "Tapsell banner display failed")
                                        if (tryInstantBanner) requestTapsellBannerForZone(activity, container, T_INSTANT_BANNER, false)
                                        else showAdiveryBanner(activity, container)
                                    }
                                })
                        } catch (e: Exception) {
                            Log.w(TAG, "Tapsell banner display threw", e)
                            if (tryInstantBanner) requestTapsellBannerForZone(activity, container, T_INSTANT_BANNER, false)
                            else showAdiveryBanner(activity, container)
                        }
                    }
                    override fun error(message: String) {
                        Log.w(TAG, "Tapsell banner request failed: $message")
                        if (activity.isFinishing || activity.isDestroyed) return
                        if (tryInstantBanner) requestTapsellBannerForZone(activity, container, T_INSTANT_BANNER, false)
                        else showAdiveryBanner(activity, container)
                    }
                })
        } catch (e: Exception) {
            Log.w(TAG, "Tapsell banner request threw", e)
            if (tryInstantBanner) requestTapsellBannerForZone(activity, container, T_INSTANT_BANNER, false)
            else showAdiveryBanner(activity, container)
        }
    }

    private fun showAdiveryBanner(activity: Activity, container: FrameLayout) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            container.removeAllViews()
            val banner = AdiveryBannerAdView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPlacementId(ADIVERY_BANNER)
                setBannerSize(BannerSize.BANNER)
                setBannerAdListener(object : AdiveryAdListener() {
                    override fun onAdLoaded() { Log.d(TAG, "Adivery fallback banner loaded") }
                    override fun onAdShown() = Unit
                    override fun onAdClicked() = Unit
                    override fun onError(reason: String) { Log.w(TAG, "Adivery banner failed: $reason") }
                })
            }
            container.addView(banner)
            banner.post { if (!activity.isFinishing && !activity.isDestroyed) banner.loadAd() }
        } catch (e: Exception) {
            Log.w(TAG, "Adivery banner fallback threw", e)
        }
    }

    fun attachNativeAd(activity: Activity, container: FrameLayout) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = activity.getSharedPreferences("ad_settings", Context.MODE_PRIVATE)
        val launchCount = prefs.getInt("native_ad_launch_count", 0) + 1
        prefs.edit().putInt("native_ad_launch_count", launchCount).apply()

        // Rotate native video in occasionally; keep native banner as the usual, quieter format.
        if (tapsellReady && launchCount % 3 == 0) {
            try {
                TapsellPlus.requestNativeVideo(activity, T_NATIVE_VIDEO, object : AdRequestCallback() {
                    override fun response(model: TapsellPlusAdModel) {
                        if (activity.isFinishing || activity.isDestroyed) return
                        try {
                            val holder = TapsellPlusVideoAdHolder.Builder()
                                .setContentViewTemplate(R.layout.native_vid_template)
                                .setAppInstallationViewTemplate(ir.tapsell.sdk.R.layout.tapsell_app_installation_video_ad_template)
                                .setAdContainer(container)
                                .build()
                            TapsellPlus.showNativeVideo(activity, model.responseId, holder, object : AdShowListener() {
                                override fun onOpened(model: TapsellPlusAdModel) = Unit
                                override fun onError(error: TapsellPlusErrorModel) {
                                    Log.w(TAG, "Tapsell native video failed; using native banner")
                                    attachTapsellNativeBanner(activity, container)
                                }
                            })
                        } catch (e: Exception) {
                            Log.w(TAG, "Tapsell native video display threw", e)
                            attachTapsellNativeBanner(activity, container)
                        }
                    }
                    override fun error(message: String) {
                        Log.w(TAG, "Tapsell native video request failed: $message")
                        if (!activity.isFinishing && !activity.isDestroyed) attachTapsellNativeBanner(activity, container)
                    }
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Tapsell native video request threw", e)
            }
        }
        attachTapsellNativeBanner(activity, container)
    }

    private fun attachTapsellNativeBanner(activity: Activity, container: FrameLayout) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (tapsellReady) {
            try {
                val holder = TapsellPlus.createAdHolder(activity, container, ir.tapsell.plus.R.layout.native_banner)
                TapsellPlus.requestNativeAd(activity, T_NATIVE_BANNER, object : AdRequestCallback() {
                    override fun response(model: TapsellPlusAdModel) {
                        if (activity.isFinishing || activity.isDestroyed) return
                        try {
                            TapsellPlus.showNativeAd(activity, model.responseId, holder, object : AdShowListener() {
                                override fun onOpened(model: TapsellPlusAdModel) = Unit
                                override fun onError(error: TapsellPlusErrorModel) {
                                    Log.w(TAG, "Tapsell native ad failed; trying Adivery")
                                    showAdiveryNative(activity, container)
                                }
                            })
                        } catch (e: Exception) {
                            Log.w(TAG, "Tapsell native ad display threw", e)
                            showAdiveryNative(activity, container)
                        }
                    }
                    override fun error(message: String) {
                        Log.w(TAG, "Tapsell native ad request failed: $message")
                        if (!activity.isFinishing && !activity.isDestroyed) showAdiveryNative(activity, container)
                    }
                })
                return
            } catch (e: Exception) {
                Log.w(TAG, "Tapsell native ad request threw", e)
            }
        }
        showAdiveryNative(activity, container)
    }

    private fun showAdiveryNative(activity: Activity, container: FrameLayout) {
        if (activity.isFinishing || activity.isDestroyed || BuildConfig.ADIVERY_APP_KEY.isBlank()) return
        try {
            container.removeAllViews()
            val native = AdiveryNativeAdView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPlacementId(A_NATIVE)
                setNativeAdLayout(R.layout.view_native_ad)
                setListener(object : AdiveryAdListener() {
                    override fun onAdLoaded() { Log.d(TAG, "Adivery native ad loaded") }
                    override fun onAdShown() = Unit
                    override fun onAdClicked() = Unit
                    override fun onError(reason: String) { Log.w(TAG, "Adivery native ad failed: $reason") }
                })
            }
            container.addView(native)
            native.post { if (!activity.isFinishing && !activity.isDestroyed) native.loadAd() }
        } catch (e: Exception) {
            Log.w(TAG, "Adivery native ad setup failed", e)
        }
    }

    fun preloadTapsellInterstitial(activity: Activity) {
        if (!tapsellReady || activity.isFinishing || activity.isDestroyed) return
        try {
            TapsellPlus.requestInterstitialAd(activity, TAPSELL_INTERSTITIAL, object : AdRequestCallback() {
                override fun response(model: TapsellPlusAdModel) {
                    tapsellInterstitialResponseId = model.responseId
                }
                override fun error(message: String) {
                    Log.w(TAG, "Tapsell interstitial request failed: $message")
                    tapsellInterstitialResponseId = null
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Tapsell interstitial preload threw", e)
        }
    }

    /** Rewarded ads are prepared here, but only shown by an explicit feature/action that grants a real reward. */
    fun showRewardedAfterUserAction(activity: Activity, onReward: () -> Unit) {
        if (tapsellReady) {
            try {
                TapsellPlus.requestRewardedVideoAd(activity, TAPSELL_REWARDED, object : AdRequestCallback() {
                    override fun response(model: TapsellPlusAdModel) {
                        tapsellRewardedResponseId = model.responseId
                        TapsellPlus.showRewardedVideoAd(activity, model.responseId, object : AdShowListener() {
                            override fun onOpened(model: TapsellPlusAdModel) = Unit
                            override fun onClosed(model: TapsellPlusAdModel) = Unit
                            override fun onRewarded(model: TapsellPlusAdModel) { onReward() }
                            override fun onError(error: TapsellPlusErrorModel) {
                                Log.w(TAG, "Tapsell rewarded show failed: $error")
                            }
                        })
                    }
                    override fun error(message: String) {
                        Log.w(TAG, "Tapsell rewarded request failed: $message")
                        prepareAdiveryRewarded(activity)
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "Tapsell rewarded request threw", e)
                prepareAdiveryRewarded(activity)
            }
        } else {
            prepareAdiveryRewarded(activity)
        }
    }

    private fun prepareAdiveryRewarded(activity: Activity) {
        try {
            Adivery.prepareRewardedAd(activity, ADIVERY_REWARDED)
        } catch (e: Exception) {
            Log.w(TAG, "Adivery rewarded preparation failed", e)
        }
    }

    fun onMainResume(activity: Activity) {
        if (!adiveryPreloaded && BuildConfig.ADIVERY_APP_KEY.isNotBlank()) {
            try {
                Adivery.prepareInterstitialAd(activity, A_INTERSTITIAL)
                Adivery.prepareRewardedAd(activity, A_REWARDED)
                Adivery.prepareAppOpenAd(activity, A_APP_OPEN)
                adiveryPreloaded = true
            } catch (e: Exception) {
                Log.w(TAG, "Adivery ad preload failed", e)
            }
        }
        val prefs = activity.getSharedPreferences("ad_settings", Context.MODE_PRIVATE)
        val lastPause = prefs.getLong("last_main_pause", 0L)
        val now = System.currentTimeMillis()
        if (lastPause > 0L && now - lastPause >= APP_OPEN_COOLDOWN_MS) {
            try {
                if (Adivery.isLoaded(ADIVERY_APP_OPEN)) {
                    Adivery.showAppOpenAd(activity, ADIVERY_APP_OPEN)
                } else {
                    Adivery.prepareAppOpenAd(activity, ADIVERY_APP_OPEN)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Adivery app-open ad failed", e)
            }
        }
        prefs.edit().remove("last_main_pause").apply()
        preloadTapsellInterstitial(activity)
    }

    fun onMainPause(context: Context) {
        context.getSharedPreferences("ad_settings", Context.MODE_PRIVATE)
            .edit().putLong("last_main_pause", System.currentTimeMillis()).apply()
    }
}
