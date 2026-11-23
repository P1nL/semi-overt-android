package com.semiovert.app.feature.editor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Base64
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import com.semiovert.app.core.designsystem.AppMotionFrameRate
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import com.semiovert.app.core.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.UUID

internal class EditorHostCallbacks(
    val ready: () -> Unit,
    val loading: () -> Unit,
    val failed: () -> Unit,
    val close: () -> Unit,
    val export: (String, String) -> Unit,
    val chooseImage: (ValueCallback<Array<Uri>>) -> Unit,
)

/** One document/renderer owned by the navigation host, not by an individual editor entrance. */
internal class ArticleEditorSession(
    private val context: Context,
    private val storageKey: String,
    private val business: LiveBusiness? = null,
    private val owner: String? = null,
) {
    private var web: WebView? = null
    private var callbacks: EditorHostCallbacks? = null
    private var destroyed = false
    private var documentReady = false
    private var failed = false
    private var activeStorageKey = storageKey
    private val cloud = business != null && owner != null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loaded = false
    private var preparing = false
    private var remoteHint: String? = null
    private var serverDraft: CloudDraft? = null
    private var blocked = false
    private var syncing = false
    private var mediaRequests = 0
    private var palette = EditorPalette.Light
    private val main = Handler(Looper.getMainLooper())
    var drafts by mutableStateOf<List<EditorDraft>>(emptyList())
        private set
    var draftsLoading by mutableStateOf(false)
        private set
    var draftsFailed by mutableStateOf(false)
        private set
    var draftsMessage by mutableStateOf<String?>(null)
        private set
    var deletingDraftKey by mutableStateOf<String?>(null)
        private set

    @Keep
    private inner class Bridge {
        @JavascriptInterface fun ready(key: String) = post {
            if (key != activeStorageKey) return@post
            loaded = true
            if (draftsLoading) readDrafts()
            if (!cloud) this@ArticleEditorSession.ready()
            else if (callbacks != null) prepareCloud()
        }
        @JavascriptInterface fun close() = post {
            if (!syncing && mediaRequests == 0) callbacks?.close?.invoke()
        }
        @JavascriptInterface fun cloudSave(key: String, action: String, data: String) = post {
            if (key == activeStorageKey && cloud && callbacks != null && data.length <= 150_000) {
                if (action == "reload") prepareCloud(reload = true)
                else syncCloud(action, data)
            }
        }
        @JavascriptInterface fun cloudImage(key: String, request: String, type: String, data: String) = post {
            if (key == activeStorageKey && cloud && callbacks != null) uploadImage(request, type, data)
        }
        @JavascriptInterface fun motion() = post {
            if(callbacks!=null)AppMotionFrameRate.activity()
        }
        @JavascriptInterface fun exportDraft(name: String, content: String) =
            post { callbacks?.export?.invoke(name, content) }
        @JavascriptInterface fun hideKeyboard() = post {
            if (callbacks != null) web?.let { browser ->
                val controller = ViewCompat.getWindowInsetsController(browser)
                if (controller != null) controller.hide(WindowInsetsCompat.Type.ime())
                else context.getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(browser.windowToken, 0)
            }
        }

        private fun post(action: () -> Unit) {
            main.post { if (!destroyed) action() }
        }
    }

    private fun ready() {
        documentReady = true
        callbacks?.ready?.invoke()
    }

    fun attach(palette: EditorPalette, host: EditorHostCallbacks): WebView {
        check(!destroyed)
        this.palette = palette
        callbacks = host
        val existing = web
        if (existing != null) {
            existing.onResume()
            existing.applyEditorTheme(palette)
            existing.evaluateJavascript("window.SemiNativeEditor?.setKeyboardInset(0)", null)
            if (failed) host.failed()
            else if (documentReady) existing.post { if (callbacks === host) host.ready() }
            else if (cloud && loaded) prepareCloud()
            return existing
        }
        return createWebView(palette)
    }

    /** Read the same local document used by the editor, including drafts saved before this UI existed. */
    fun loadDrafts(palette: EditorPalette) {
        if (destroyed) return
        this.palette = palette
        draftsLoading = true
        draftsFailed = false
        draftsMessage = null
        val browser = web ?: createWebView(palette)
        browser.onResume()
        if (failed) browser.reload()
        else if (loaded || documentReady) readDrafts()
    }

    private fun readDrafts() {
        val base = JSONObject.quote(storageKey)
        web?.evaluateJavascript("""
            (() => {
              const base = $base;
              return JSON.stringify(Object.keys(localStorage)
                .filter(key => key === base || key.startsWith(base + ':'))
                .map(key => ({...JSON.parse(localStorage.getItem(key)), key})));
            })()
        """.trimIndent()) { encoded ->
            if (destroyed) return@evaluateJavascript
            val local = runCatching {
                val items = JSONArray(JSONTokener(encoded).nextValue() as String)
                items.objects().mapNotNull { item -> EditorDraft.fromJson(item, item.getString("key")) }
            }.getOrElse {
                draftsFailed = true; draftsLoading = false
                return@evaluateJavascript
            }
            if (!cloud) {
                drafts = local.sortedByDescending { it.updatedAt }
                draftsLoading = false
            } else scope.launch {
                try {
                    val linked = local.filter { it.remoteId != null }.associateBy { it.remoteId }
                    val remote = checkNotNull(business).drafts(checkNotNull(owner)).map { item ->
                        val cached = linked[item.id]
                        val summary = EditorDraft.fromCloud(item, cached?.key ?: "$storageKey:cloud:${item.id}")
                        if (cached?.localChanges == true) cached.copy(status = item.status, reason = item.reason)
                        else summary
                    }
                    val ids = remote.mapNotNull { it.remoteId }.toSet()
                    drafts = (remote + local.filter { it.remoteId == null || (it.localChanges && it.remoteId !in ids) })
                        .sortedByDescending { it.updatedAt }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    drafts = local.sortedByDescending { it.updatedAt }
                    draftsMessage = error.userMessage()
                    draftsFailed = local.isEmpty()
                } finally { draftsLoading = false }
            }
            if (callbacks == null) web?.onPause()
        }
    }

    fun selectDraft(draft: EditorDraft?): String {
        val key = draft?.key?.takeIf { it.isNotBlank() } ?: if (cloud) "$storageKey:new:${UUID.randomUUID()}" else storageKey
        if (key == activeStorageKey && !cloud) return key
        activeStorageKey = key
        remoteHint = draft?.remoteId
        serverDraft = null
        blocked = false
        loaded = false
        documentReady = false
        failed = false
        // The previous document was saved when the editor was parked.
        web?.loadUrl(documentUrl())
        return key
    }

    fun deleteDraft(draft: EditorDraft) {
        val browser = web ?: return
        if (deletingDraftKey != null || !draft.deletable) return
        deletingDraftKey = draft.key
        fun removeLocal() {
            browser.evaluateJavascript("(() => { localStorage.removeItem(${JSONObject.quote(draft.key)}); return true; })()") { result ->
                if (destroyed) return@evaluateJavascript
                deletingDraftKey = null
                if (result == "true") {
                    drafts = drafts.filterNot { it.key == draft.key }
                    if (activeStorageKey == draft.key) {
                        activeStorageKey = storageKey
                        documentReady = false
                        loaded = false
                        browser.loadUrl(documentUrl())
                    }
                } else Toast.makeText(context, "删除失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
        if (!cloud || draft.remoteId == null) removeLocal()
        else scope.launch {
            try {
                checkNotNull(business).deleteDraft(checkNotNull(owner), draft.remoteId)
                removeLocal()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                deletingDraftKey = null
                Toast.makeText(context, error.userMessage(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun documentUrl() = EDITOR_URL + "?draftKey=" + Uri.encode(activeStorageKey) +
        "&theme=" + palette.name + if (cloud) "&cloud=1" else ""

    private fun prepareCloud(reload: Boolean = false) {
        val browser = web ?: return
        if (preparing || syncing || !loaded) return
        preparing = true
        val key = activeStorageKey
        browser.evaluateJavascript("window.SemiNativeEditor?.localDocument()") { encoded ->
            scope.launch {
                try {
                    val raw = runCatching { JSONObject(JSONTokener(encoded).nextValue() as String) }.getOrElse { JSONObject() }
                    val currentId = raw.id("remoteId") ?: remoteHint
                    val local = DraftContent.fromEditor(raw)
                    val dirty = raw.optBoolean("dirty")
                    remoteHint = currentId
                    val document = currentId?.let { checkNotNull(business).editDraft(checkNotNull(owner), it) }
                    if (activeStorageKey != key) return@launch
                    serverDraft = document
                    val divergent = document != null && dirty && local != document.content &&
                        (!document.editable || raw.text("remoteVersion")?.toLongOrNull() != document.version)
                    blocked = !reload && divergent
                    val message = when {
                        blocked -> "云端已有新版本，本机修改已保留"
                        document?.editable == true && document.version == null -> "服务器尚未提供草稿版本"
                        document?.status == "PENDING" -> "待审核"
                        document?.status == "RETURNED" -> "退回修改"
                        document?.status == "APPROVED" -> "已发布"
                        document?.status == "REJECTED" -> "未通过"
                        document != null -> "云端草稿"
                        else -> "草稿自动保存到本机"
                    }
                    val keep = !reload && dirty && (document == null || local != document.content)
                    applyCloudOpen(document, keep, blocked, message, locked = document != null && !document.editable)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (activeStorageKey == key) {
                        blocked = true
                        applyCloudOpen(null, true, true, error.userMessage(), locked = false)
                    }
                } finally {
                    preparing = false
                    if (activeStorageKey == key && !destroyed) ready()
                }
            }
        }
    }

    private fun applyCloudOpen(document: CloudDraft?, keepLocal: Boolean, conflict: Boolean, message: String, locked: Boolean) {
        web?.evaluateJavascript("window.SemiNativeEditor?.openCloud(${document?.editorJson() ?: "null"},$keepLocal,$conflict," +
            "${JSONObject.quote(message)},$locked)", null)
    }

    private fun syncCloud(action: String, raw: String) {
        if (syncing || preparing || mediaRequests > 0 || action !in setOf("auto", "save", "submit", "copy", "cancel")) {
            web?.evaluateJavascript("window.SemiNativeEditor?.cloudFailed('请等待当前操作完成', false)", null)
            return
        }
        val content = runCatching { DraftContent.fromEditor(JSONObject(raw)) }.getOrNull() ?: return
        if (content.empty && (action == "copy" || (action in setOf("auto", "save") && remoteHint == null))) {
            web?.evaluateJavascript("window.SemiNativeEditor?.cloudFailed('请输入草稿内容', false)", null)
            return
        }
        if (action != "copy" && blocked) {
            web?.evaluateJavascript("window.SemiNativeEditor?.cloudFailed('请先处理云端版本冲突', true)", null)
            return
        }
        syncing = true
        scope.launch {
            try {
                val live = checkNotNull(business)
                val account = checkNotNull(owner)
                if (action == "cancel") {
                    val pending = serverDraft?.takeIf { it.status == "PENDING" }
                        ?: throw ApiFailure("当前文章不在审核中")
                    val result = live.cancelReview(account, pending.id)
                    serverDraft = result
                    applyCloudOpen(result, false, false, "已撤回审核，可继续编辑", locked = false)
                    return@launch
                }
                var draft = serverDraft
                if (action == "copy" || draft == null) {
                    if (action != "copy" && remoteHint != null) throw ApiFailure("请先重新读取云端草稿")
                    draft = live.createDraft(account)
                    serverDraft = draft
                    remoteHint = draft.id
                    blocked = false
                    web?.evaluateJavascript("window.SemiNativeEditor?.cloudLinked(${draft.editorJson()})", null)
                }
                if (!draft.editable) throw ApiFailure(409, "当前文章不能编辑")
                if (draft.content != content) {
                    draft = live.saveDraft(account, draft, content)
                    serverDraft = draft
                }
                if (action == "submit") {
                    try {
                        draft = live.submitDraft(account, draft)
                        serverDraft = draft
                    } catch (error: ApiFailure) {
                        if (!error.isDraftConflict) notifyCloudSaved(draft, raw, "已保存草稿，提交未完成", false)
                        throw error
                    }
                }
                notifyCloudSaved(draft, raw, if (action == "submit") "已提交审核" else "草稿已同步", action != "auto")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if ((error as? ApiFailure)?.isDraftConflict == true) blocked = true
                web?.evaluateJavascript("window.SemiNativeEditor?.cloudFailed(${JSONObject.quote(error.userMessage())},$blocked)", null)
            } finally {
                syncing = false
            }
        }
    }

    private fun notifyCloudSaved(draft: CloudDraft, raw: String, message: String, announce: Boolean) {
        web?.evaluateJavascript("window.SemiNativeEditor?.cloudSaved(${draft.editorJson()},${JSONObject.quote(raw)}," +
            "${JSONObject.quote(message)},$announce)", null)
    }

    private fun uploadImage(request: String, type: String, data: String) {
        if (type !in setOf("cover", "body") || !request.matches(Regex("[0-9]+")) || data.length > 7_000_000) return
        val key = activeStorageKey
        mediaRequests++
        scope.launch {
            try {
                if (serverDraft?.editable == false) throw ApiFailure("该文章当前不能编辑")
                val bytes = withContext(Dispatchers.Default) {
                    val raw = data.removePrefix("data:image/jpeg;base64,")
                    if (raw == data) throw ApiFailure("无法读取图片")
                    Base64.decode(raw, Base64.DEFAULT)
                }
                val image = checkNotNull(business).upload(checkNotNull(owner), bytes, "image/jpeg",
                    if (type == "cover") "ARTICLE_COVER" else "ARTICLE_CONTENT")
                if (key == activeStorageKey) web?.evaluateJavascript(
                    "window.SemiNativeEditor?.cloudImage($request,${JSONObject.quote(image.url)},null)", null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (key == activeStorageKey) web?.evaluateJavascript(
                    "window.SemiNativeEditor?.cloudImage($request,null,${JSONObject.quote(error.userMessage())})", null)
            } finally { mediaRequests-- }
        }
    }

    private fun draftLoadFailed() {
        if (draftsLoading) {
            draftsLoading = false
            draftsFailed = true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(palette: EditorPalette): WebView {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
        return object : WebView(context) {
            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                super.onScrollChanged(l, t, oldl, oldt)
                if (l != 0 || t != 0) scrollTo(0, 0)
            }
        }.apply {
            web = this
            contentDescription = "文章编辑器"
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.useWideViewPort = true
            addJavascriptInterface(Bridge(), "SemiNative")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    loader.shouldInterceptRequest(request.url)?.let { return it }
                    if (request.isForMainFrame) return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    return null
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net"
                        && uri.path == "/assets/editor/index.html") return false
                    if (request.isForMainFrame && request.hasGesture() && uri.scheme == "https") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
                    }
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    documentReady = false
                    loaded = false
                    failed = false
                    callbacks?.loading?.invoke()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    // A parked renderer can read storage before it has a visible animation frame.
                    if (draftsLoading && !failed) readDrafts()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        failed = true
                        draftLoadFailed()
                        callbacks?.failed?.invoke()
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                    val host = callbacks
                    if (host == null) callback.onReceiveValue(null) else host.chooseImage(callback)
                    return true
                }

                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e("ArticleEditor", "${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                        if (!documentReady) {
                            failed = true
                            draftLoadFailed()
                            callbacks?.failed?.invoke()
                        }
                    }
                    return true
                }
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                ViewCompat.getRootWindowInsets(this)?.let { applyEditorKeyboardInset(it) }
            }
            loadUrl(documentUrl())
        }
    }

    fun detach(host: EditorHostCallbacks) {
        if (callbacks !== host) return
        callbacks = null
        web?.let { browser ->
            browser.clearFocus()
            // Save/reset transient UI before parking. A rapid reentry must not pause the new host.
            browser.evaluateJavascript("window.SemiNativeEditor?.suspend()") {
                if (!destroyed && callbacks == null) browser.onPause()
            }
            (browser.parent as? ViewGroup)?.removeView(browser)
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        scope.cancel()
        callbacks = null
        web?.let { browser ->
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.evaluateJavascript("window.SemiNativeEditor?.flush()") {
                browser.removeJavascriptInterface("SemiNative")
                browser.stopLoading()
                browser.destroy()
            }
        }
        web = null
    }
}

@Composable
internal fun rememberArticleEditorSession(storageKey: String = EDITOR_DRAFT_KEY, business: LiveBusiness? = null,
    owner: String? = null): ArticleEditorSession {
    val context = LocalContext.current
    val session = remember(storageKey, business, owner) { ArticleEditorSession(context, storageKey, business, owner) }
    DisposableEffect(session) { onDispose { session.destroy() } }
    return session
}
