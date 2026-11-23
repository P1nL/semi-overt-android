package com.semiovert.app

import android.app.Application
import com.semiovert.app.core.data.LiveBusiness
import com.semiovert.app.core.data.PRODUCTION_API
import com.semiovert.app.core.designsystem.installAppMotionPolicy
import com.semiovert.app.core.web.WebViewStartup

class SemiOvertApplication: Application() {
    internal var offlineFixture = false
    internal var fixtureEndpoint: String? = null
    internal val business by lazy {
        if (BuildConfig.OFFLINE_PREVIEW || (BuildConfig.DEBUG && offlineFixture)) null
        else LiveBusiness(this, fixtureEndpoint.takeIf { BuildConfig.DEBUG } ?: PRODUCTION_API,
            if (BuildConfig.DEBUG && fixtureEndpoint != null) "" else BuildConfig.TURNSTILE_SITE_KEY)
    }

    override fun onCreate() {
        super.onCreate()
        installAppMotionPolicy(this)
        WebViewStartup.prepare(this)
    }
}
