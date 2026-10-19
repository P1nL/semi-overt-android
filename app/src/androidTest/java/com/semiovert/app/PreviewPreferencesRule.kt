package com.semiovert.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Reset only this offline demo's preferences, before each test Activity launches. */
class PreviewPreferencesRule: ExternalResource() {
    override fun before() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("offline_preview",Context.MODE_PRIVATE).edit().clear().commit()
    }
}
