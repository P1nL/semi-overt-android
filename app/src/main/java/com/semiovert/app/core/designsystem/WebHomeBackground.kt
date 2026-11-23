package com.semiovert.app.core.designsystem

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewStartupException
import com.semiovert.app.core.web.WebViewStartup
import java.io.ByteArrayInputStream
import org.json.JSONObject

/** The web renderer is decorative; native controls retain all input handling. */
@Composable
internal fun WebHomeBackground(modifier: Modifier=Modifier,active: Boolean=true,
    preparation: IntroPreparation?=null) {
    val context=LocalContext.current
    var engineReady by remember {mutableStateOf(false)}
    LaunchedEffect(context) {
        preparation?.backgroundReady=false
        try {
            WebViewStartup.await(context)
            engineReady=true
        } catch(error: WebViewStartupException) {
            Log.e("WebHomeBackground","WebView startup failed",error)
        }
    }
    if(!engineReady)return
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    val motion=LocalMotionEnabled.current
    val currentPreparation by rememberUpdatedState(preparation)
    AndroidView(
        factory={context ->
            HomeBackgroundWebView(context,if(preparation!=null) {{currentPreparation?.backgroundReady=true}} else null)
        },
        modifier=modifier.fillMaxSize().testTag("home-web-background"),
        onReset=null,
        onRelease={it.release()},
        update={view -> view.setEnvironment(active&&resumed,motion)},
    )
}

@SuppressLint("SetJavaScriptEnabled")
internal class HomeBackgroundWebView(context: Context,private val onFirstFrameReady: (() -> Unit)?=null): WebView(context) {
    private var environment=JSONObject()
    private var lastSent: String?=null
    private var pageReady=false
    private var released=false

    init {
        val loader=WebViewAssetLoader.Builder()
            .addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(context)).build()
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable=false
        isFocusableInTouchMode=false
        isClickable=false
        isLongClickable=false
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        overScrollMode=View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled=false
        isHorizontalScrollBarEnabled=false
        settings.javaScriptEnabled=true
        settings.allowFileAccess=false
        settings.allowContentAccess=false
        settings.cacheMode=WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.useWideViewPort=true
        webViewClient=object: WebViewClient() {
            override fun shouldInterceptRequest(view: WebView,request: WebResourceRequest): WebResourceResponse =
                loader.shouldInterceptRequest(request.url)?:WebResourceResponse(
                    "text/plain","UTF-8",404,"Not Found",emptyMap(),ByteArrayInputStream(ByteArray(0)))

            override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest)=true

            override fun onPageFinished(view: WebView,url: String) {
                if(released)return
                pageReady=true
                lastSent=null
                sendEnvironment()
                awaitFirstFrame()
            }
        }
        webChromeClient=object: WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if(message.messageLevel()==ConsoleMessage.MessageLevel.ERROR)
                    Log.e("WebHomeBackground","${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                return true
            }
        }
        val ink=SoPalette.AutumnLeaf.toArgb() and 0xFFFFFF
        loadUrl("https://appassets.androidplatform.net/assets/web-background/index.html?ink=$ink")
    }

    fun setEnvironment(active: Boolean,motionEnabled: Boolean) {
        if(released)return
        environment=JSONObject().put("active",active).put("motionEnabled",motionEnabled)
        sendEnvironment()
    }

    private fun sendEnvironment() {
        if(!pageReady||released)return
        val next=environment.toString()
        if(next==lastSent)return
        lastSent=next
        evaluateJavascript("window.SemiWebBackground?.configure($next)",null)
    }

    private fun awaitFirstFrame() {
        val callback=onFirstFrameReady?:return
        evaluateJavascript("(() => {const state=window.SemiWebBackground?.getState();return !!state?.ready&&!state.error})()") {result ->
            if(released||result!="true")return@evaluateJavascript
            postVisualStateCallback(0L,object: VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if(!released)callback()
                }
            })
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent)=false
    override fun onGenericMotionEvent(event: MotionEvent)=false

    fun release() {
        if(released)return
        released=true
        pageReady=false
        stopLoading()
        onPause()
        destroy()
    }
}
