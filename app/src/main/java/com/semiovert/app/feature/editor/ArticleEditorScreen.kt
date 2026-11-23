package com.semiovert.app.feature.editor
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsAnimationCompat
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.web.WebViewStartup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

internal const val EDITOR_URL = "https://appassets.androidplatform.net/assets/editor/index.html"
internal const val EDITOR_DRAFT_KEY = "article-editor-current-v1"

/** The home scene remains mounted underneath the ink mask, preserving its exact card/scroll state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArticleEditorScreen(
    onBack: () -> Unit,
    storageKey: String = EDITOR_DRAFT_KEY,
    onReady: () -> Unit = {},
    session: ArticleEditorSession? = null,
) {
    val context = LocalContext.current
    val editorSession = session ?: rememberArticleEditorSession(storageKey)
    val lifecycle = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val navigationBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    val motion = LocalMotionEnabled.current
    val palette = EditorPalette.forTheme(MaterialTheme.colorScheme.background.luminance() < .5f)
    val currentPalette by rememberUpdatedState(palette)
    val scope = rememberCoroutineScope()
    val currentBack by rememberUpdatedState(onBack)
    val currentReady by rememberUpdatedState(onReady)
    val alive = remember { AtomicBoolean(true) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var frame by remember { mutableStateOf<InkRevealLayout?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var engineStarted by remember { mutableStateOf(false) }
    var photoCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var exportContent by remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    val requestClose: () -> Unit = {
        if (!closing) {
            frame?.documentReady = false
            web?.clearFocus()
            closing = true
        }
    }
    val currentRequestClose by rememberUpdatedState(requestClose)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photoCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        photoCallback = null
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val content = exportContent
        exportContent = null
        if (uri != null && content != null) scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                }.isSuccess
            }
            Toast.makeText(context, if (success) "已导出" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
    val host = remember {
        EditorHostCallbacks(
            ready = {
                web?.applyEditorTheme(currentPalette)
                web?.let { browser ->
                    ViewCompat.getRootWindowInsets(browser)?.let { browser.applyEditorKeyboardInset(it) }
                }
                web?.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (alive.get()) {
                            ready = true
                            currentReady()
                        }
                    }
                })
            },
            loading = { ready = false; failed = false },
            failed = { failed = true },
            close = { currentRequestClose() },
            export = { name, content ->
                exportContent = content
                exportPicker.launch(name.replace(Regex("[\\\\/:*?\"<>|]"), "-").take(100))
            },
            chooseImage = { callback ->
                photoCallback?.onReceiveValue(null)
                photoCallback = callback
                imagePicker.launch("image/*")
            },
        )
    }
    LaunchedEffect(Unit) {
        try {
            WebViewStartup.await(context)
            engineStarted = true
        } catch (error: androidx.webkit.WebViewStartupException) {
            Log.e("ArticleEditor", "WebView startup failed", error)
            failed = true
        }
    }
    LaunchedEffect(ready, motion, closing) {
        if (closing) {
            // Reverse the same mask and random delays, including a partially finished entrance.
            if (motion) progress.animateTo(0f, tween(
                (LiquidRevealMotion.DURATION_MS * progress.value).roundToInt().coerceAtLeast(1),
                easing = LinearEasing,
            ))
            else progress.snapTo(0f)
            currentBack()
        } else if (ready) {
            if (motion) progress.animateTo(1f, tween(LiquidRevealMotion.DURATION_MS, easing = LinearEasing))
            else progress.snapTo(1f)
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                web?.evaluateJavascript("window.SemiNativeEditor?.flush()", null)
                web?.onPause()
            } else if (event == Lifecycle.Event.ON_START) web?.onResume()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            alive.set(false)
            photoCallback?.onReceiveValue(null)
            editorSession.detach(host)
        }
    }
    BackHandler {
        if (closing) return@BackHandler
        if (!ready || failed) requestClose()
        else web?.evaluateJavascript("window.SemiNativeEditor?.back()", null)
    }
    val statusTop = WindowInsets.statusBars.getTop(density)
    val left = WindowInsets.displayCutout.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val right = WindowInsets.displayCutout.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    Box(Modifier.fillMaxSize().testTag("article-editor")) {
        if (engineStarted) AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                InkRevealLayout(viewContext).apply {
                    this.palette = palette
                    frame = this
                    val browser = editorSession.attach(palette, host)
                    web = browser
                    addView(browser, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    var keyboardAnimating = false
                    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                        if (!keyboardAnimating) browser.applyEditorKeyboardInset(insets)
                        insets
                    }
                    ViewCompat.setWindowInsetsAnimationCallback(this, object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                        override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                            if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) keyboardAnimating = true
                        }
                        override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
                            browser.applyEditorKeyboardInset(insets)
                            return insets
                        }
                        override fun onEnd(animation: WindowInsetsAnimationCompat) {
                            if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                                keyboardAnimating = false
                                ViewCompat.getRootWindowInsets(browser)?.let { browser.applyEditorKeyboardInset(it) }
                            }
                        }
                    })
                }
            },
            update = {
                if (it.palette != palette) {
                    it.palette = palette
                    web?.applyEditorTheme(palette)
                }
                it.setPadding(left, statusTop, right, navigationBottom)
                it.documentReady = ready && !closing
                it.revealProgress = progress.value
            },
        )
        if (failed) Column(
            Modifier.fillMaxSize().background(Color(palette.background)),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("编辑器加载失败", color = Color(palette.foreground))
            if (web != null) Button(onClick = { failed = false; web?.reload() }) { Text("重试") }
            Button(onClick = requestClose) { Text("返回") }
        }
    }
}

internal fun WebView.applyEditorKeyboardInset(insets: WindowInsetsCompat) {
    val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    val overlap = if (ime > 0 && height > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val windowBottom = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.bottom
        // Subtract only the part of this WebView actually covered by the IME.
        (location[1] + height - (windowBottom - ime)).coerceIn(0, ime)
    } else (ime - insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom).coerceAtLeast(0)
    evaluateJavascript("window.SemiNativeEditor?.setKeyboardInset($overlap)", null)
}

internal fun WebView.applyEditorTheme(palette: EditorPalette) {
    evaluateJavascript("window.SemiNativeEditor?.setTheme('${palette.name}')", null)
}
