package com.semiovert.app.core.web

import android.content.Context
import androidx.annotation.OptIn
import androidx.webkit.ProcessGlobalConfig
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal object WebViewStartup {
    private val started = AtomicBoolean()
    private val completion = CompletableDeferred<Unit>()

    @OptIn(markerClass = [WebViewCompat.ExperimentalAsyncStartUp::class])
    fun prepare(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val startupMode = WebViewFeature.STARTUP_FEATURE_SET_UI_THREAD_STARTUP_MODE_V2
        if (WebViewFeature.isStartupFeatureSupported(context, startupMode)) {
            ProcessGlobalConfig.apply(
                ProcessGlobalConfig().setUiThreadStartupModeV2(context, ProcessGlobalConfig.UI_THREAD_STARTUP_MODE_ASYNC),
            )
        }
        val executor = Executors.newSingleThreadExecutor()
        WebViewCompat.startUpWebView(
            context.applicationContext,
            WebViewStartUpConfig.Builder(executor).build(),
            object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                override fun onResult(result: WebViewStartUpResult) {
                    completion.complete(Unit)
                    executor.shutdown()
                }

                override fun onError(error: WebViewStartupException) {
                    completion.completeExceptionally(error)
                    executor.shutdown()
                }
            },
        )
    }

    suspend fun await(context: Context) {
        prepare(context)
        completion.await()
    }
}
