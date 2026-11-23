package com.semiovert.app

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

class BusinessTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        val app = targetContext.applicationContext as SemiOvertApplication
        app.offlineFixture = arguments.getString("liveBusiness") != "true"
        app.fixtureEndpoint = arguments.getString("fixtureEndpoint")
        super.onCreate(arguments)
    }
}
