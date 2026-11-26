package com.semiovert.app.feature.account

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.semiovert.app.core.designsystem.RemoteState
import com.semiovert.app.core.designsystem.accentTextButtonColors
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID

private const val ORIGIN = "https://www.semi-overt.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun RegistrationChallenge(siteKey: String, onToken: (String) -> Unit, onClose: () -> Unit) {
    val callback by rememberUpdatedState(onToken)
    val supported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember(attempt) { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).testTag("registration-challenge")) {
                Text("完成安全验证", style = MaterialTheme.typography.titleMedium)
                if (supported && !failed) key(attempt) {
                    AndroidView(modifier = Modifier.fillMaxWidth().height(132.dp), factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            WebViewCompat.addWebMessageListener(this, "SemiChallenge", setOf(ORIGIN)) { _, message, _, mainFrame, _ ->
                                if (!mainFrame || completed) return@addWebMessageListener
                                val data = message.data.orEmpty()
                                if (data == "error") failed = true
                                else if (data.length in 1..2048) {
                                    completed = true
                                    callback(data)
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                                    request.isForMainFrame || !allowed(request.url.toString())

                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                    if (allowed(request.url.toString()) || request.url.toString() == "$ORIGIN/") null
                                    else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }
                            loadDataWithBaseURL("$ORIGIN/", widgetHtml(siteKey), "text/html", "UTF-8", null)
                        }
                    }, onRelease = { browser ->
                        WebViewCompat.removeWebMessageListener(browser, "SemiChallenge")
                        browser.stopLoading()
                        browser.destroy()
                    }, update = {})
                } else {
                    RemoteState(if (supported) "验证失败" else "请更新 Android System WebView",
                        action = if (supported) "重试" else null, onAction = { attempt++ })
                }
                TextButton(onClick = onClose, colors = accentTextButtonColors()) { Text("取消") }
            }
        }
    }
}

private fun allowed(url: String) = url == "about:blank" || url == "about:srcdoc" ||
    android.net.Uri.parse(url).let { it.scheme == "https" && it.host == "challenges.cloudflare.com" && it.userInfo == null }

private fun widgetHtml(key: String): String {
    val nonce = UUID.randomUUID().toString()
    return """
        <!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-$nonce' https://challenges.cloudflare.com; style-src 'unsafe-inline'; frame-src https://challenges.cloudflare.com; connect-src https://challenges.cloudflare.com">
        </head><body style="margin:12px 0">
        <div id="challenge"></div>
        <script nonce="$nonce">
        const timeout = setTimeout(() => SemiChallenge.postMessage('error'), 20000);
        function showChallenge() {
          clearTimeout(timeout);
          turnstile.render('#challenge', {
            sitekey: ${JSONObject.quote(key)}, size: 'flexible', language: 'zh-cn',
            callback: token => SemiChallenge.postMessage(token),
            'error-callback': () => SemiChallenge.postMessage('error'),
            'expired-callback': () => SemiChallenge.postMessage('error')
          });
        }
        </script><script nonce="$nonce" src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=showChallenge&render=explicit" async defer></script>
        </body></html>
    """.trimIndent()
}
