package com.semiovert.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.airbnb.lottie.LottieCompositionFactory
import com.semiovert.app.core.data.LiveBusiness
import com.semiovert.app.core.data.LocalBusiness
import com.semiovert.app.core.data.OfflineSnapshots
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.data.ProfileImages
import com.semiovert.app.core.data.ReadingPosition
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.data.json
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.DeckCounterHost
import com.semiovert.app.core.designsystem.wave.WaveArticleDeck
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.model.ReadingDuration
import com.semiovert.app.core.model.SearchScope
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.account.AccountForm
import com.semiovert.app.feature.category.CategoryScreen
import com.semiovert.app.feature.category.CategoryViewModel
import com.semiovert.app.feature.editor.ArticleEditorScreen
import com.semiovert.app.feature.editor.ArticleEditorSession
import com.semiovert.app.feature.editor.EditorDraft
import com.semiovert.app.feature.editor.EditorPalette
import com.semiovert.app.feature.editor.rememberArticleEditorSession
import com.semiovert.app.feature.profile.AuthorScreen
import com.semiovert.app.feature.reader.ArticleExpansionHost
import com.semiovert.app.feature.reader.ReaderScreen
import com.semiovert.app.feature.reader.ReaderViewModel
import com.semiovert.app.feature.reader.rememberArticleExpansionState
import com.semiovert.app.feature.search.SearchScreen
import com.semiovert.app.feature.search.SearchSubmissionGate
import com.semiovert.app.feature.search.SearchViewModel
import com.semiovert.app.navigation.AppNavigation
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.DraftBoxBubble
import com.semiovert.app.navigation.DraftBoxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RemoteLoadingTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()

    @Test fun returningHomeShowsTheStartupCardsWithoutAnotherRequest(): Unit = Fixture().use { fixture ->
        val model = PreviewViewModel(ApplicationProvider.getApplicationContext<Application>())
        screen(fixture) {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                AppNavigation(PreviewState(), model) {}
            }
        }
        fixture.request("/api/v1/home").use { it.reply(json("hero" to json("primary" to article(10)))) }
        waitFor("home-list-cover-10")
        val homeCard = ui.onNodeWithTag("home-list-cover-10", useUnmergedTree = true)

        fun navigate(label: String) {
            val action = ui.onNodeWithTag("main-circle").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].first { it.label == "前往$label" }
            ui.runOnIdle { assertTrue(action.action()) }
            ui.waitForIdle()
        }
        navigate("搜索")
        ui.onNodeWithTag("search-input").assertIsDisplayed()
        homeCard.assertDoesNotExist()
        navigate("首页")
        fixture.assertNoRequest()
        homeCard.assertIsDisplayed()
    }

    @Test fun homeFillsTheArcWithColorCardsThatCannotBeOpened(): Unit = Fixture().use { fixture ->
        val model=PreviewViewModel(ApplicationProvider.getApplicationContext<Application>())
        val motion=mutableStateOf(true)
        screen(fixture) {CompositionLocalProvider(LocalMotionEnabled provides motion.value) {AppNavigation(PreviewState(),model) {}}}
        fixture.request("/api/v1/home").use {it.reply(json("hero" to json("primary" to article(10,cover="/home-cover.png"))))}
        waitFor("home-arc")
        fixture.request("/home-cover.png").use {it.send(png(android.graphics.Color.MAGENTA),"image/png")}
        fun assertPhotoCover(tag: String) {
            waitFor(tag)
            ui.waitUntil(5_000) {
                val pixels=ui.onNodeWithTag(tag,useUnmergedTree=true).captureToImage().toPixelMap()
                val cover=pixels[pixels.width/3,pixels.height/8]
                cover.red>.45f&&cover.blue>.45f&&cover.green<.05f
            }
        }
        assertPhotoCover("arc-cover-10")
        ui.onAllNodes(SemanticsMatcher("home filler") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("arc-fill-")==true
        },useUnmergedTree=true).assertCountEquals(10)
        val filler=ui.onNodeWithTag("arc-fill-1",useUnmergedTree=true)
        filler.assertHasNoClickAction()
        val real=ui.onNodeWithTag("article-10")
        assertTrue(filler.fetchSemanticsNode().boundsInRoot.overlaps(real.fetchSemanticsNode().boundsInRoot))
        repeat(2) {filler.performTouchInput {click(Offset(width*.5f,height*.08f))}}
        real.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"未展开"))
        ui.onNodeWithTag("reader-screen").assertDoesNotExist()
        ui.onNodeWithTag("feed-home").assertHomeResting(0,1)
        real.performTouchInput {click(Offset(width*.5f,height*.08f))}
        real.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已展开，再次点击阅读"))
        ui.runOnIdle {motion.value=false}
        assertPhotoCover("home-list-cover-10")
    }

    @Test fun accountFormAlwaysRequestsAPersistentSession(): Unit = Fixture().use { fixture ->
        runBlocking {fixture.business.restore()}
        val completed=AtomicBoolean()
        screen(fixture) {AccountForm({completed.set(true)})}
        ui.onNodeWithText("保持登录").assertDoesNotExist()
        ui.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("account-email")),useUnmergedTree=true)
            .performTextInput("fixture")
        ui.onNodeWithTag("account-password").performTextInput("fixture-only")
        closeSoftKeyboard()
        ui.onNodeWithTag("account-submit").performClick()
        fixture.request("/api/v1/auth/login","POST").use {
            assertTrue(it.readJsonBody().getBoolean("rememberMe"))
            it.reply(null,status=401)
        }
        waitFor("account-error")
        assertFalse(completed.get())
        ui.onNodeWithTag("account-submit").performClick()
        fixture.request("/api/v1/auth/login","POST").use {
            assertEquals("fixture-only",it.readJsonBody().getString("password"))
            it.reply(json("id" to 3,"nickname" to "测试作者","accessToken" to "fixture-token"),cookie=true)
        }
        ui.waitUntil(5_000) {completed.get()}
        assertTrue(fixture.business.session.value.authenticated)
        ui.onNodeWithTag("account-error").assertDoesNotExist()
        ui.onNodeWithText("请输入密码").assertDoesNotExist()
    }

    @Test fun savedProfileReusesBothUploadedImagesWithoutADownload(): Unit = Fixture().use { fixture ->
        runBlocking {
            val app=fixture.business
            val login=async(Dispatchers.IO) {app.signIn("fixture","fixture-only",true)}
            fixture.request("/api/v1/auth/login","POST").use {
                it.reply(json("id" to 3,"nickname" to "测试作者","accessToken" to "fixture-token"),cookie=true)
            }
            login.await()
            val initial=checkNotNull(app.session.value.account).profile
            val avatar=checkNotNull(ProfileImages.save(context) {
                Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.CYAN)}
            })
            val cover=checkNotNull(ProfileImages.save(context) {
                Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.MAGENTA)}
            })
            try {
                val save=async(Dispatchers.IO) {app.saveProfile("3",initial,initial.copy(avatarPhoto=avatar,coverPhoto=cover))}
                fixture.request("/api/v1/upload","POST").use {it.reply(json("url" to "/saved-avatar.png"))}
                fixture.request("/api/v1/upload","POST").use {it.reply(json("url" to "/saved-cover.png"))}
                fixture.request("/api/v1/users/me","PUT").use {
                    val profile=it.readJsonBody().put("id",3)
                    assertEquals("${fixture.origin}/saved-avatar.png",profile.getString("avatarUrl"))
                    assertEquals("${fixture.origin}/saved-cover.png",profile.getString("coverUrl"))
                    it.reply(profile)
                }
                save.await()
                val saved=checkNotNull(app.session.value.account).profile
                for((url,color) in listOf(saved.avatarPhoto to android.graphics.Color.CYAN,saved.coverPhoto to android.graphics.Color.MAGENTA)) {
                    assertEquals(color,app.media.memory(checkNotNull(url),1200)?.getPixel(0,0))
                    assertNotNull(app.media.open(url)?.use {it.read()})
                }
                assertNull(ProfileImages.uploadFile(context,avatar))
                assertNull(ProfileImages.uploadFile(context,cover))
            } finally {
                ProfileImages.delete(context,avatar)
                ProfileImages.delete(context,cover)
            }
        }
        fixture.assertNoRequest()
    }

    @Test fun categoryCounterStaysLocalAndCoverPrefersArticleArtwork(): Unit = Fixture().use { fixture ->
        val model=CategoryViewModel(SavedStateHandle())
        ui.mainClock.autoAdvance=false
        screen(fixture) {DeckCounterHost("category",20) {CategoryScreen({},{},model)}}
        ui.mainClock.advanceTimeBy(384)
        val counter=ui.onNodeWithTag("deck-counter")
        fixture.request("/api/v1/categories/QUICK/articles").use {request ->
            ui.onNodeWithTag("category-label").assertTextEquals(ReadingDuration.QUICK.label)
            counter.assertContentDescriptionEquals("正在加载分类")
            val bounds=counter.getUnclippedBoundsInRoot()
            assertMoving(counter)
            assertEquals(bounds,counter.getUnclippedBoundsInRoot())
            ui.mainClock.autoAdvance=true
            request.reply(page(article(10)))
        }
        waitFor("wave-cover-10")
        counter.assertContentDescriptionEquals("第 1 篇，共 1 篇")
        val palette=cardPixel("wave-cover-10")
        val expected=homeArticleCoverColor(0)
        assertEquals(expected.green,palette.green,.02f)
        assertEquals(expected.red,palette.red,.02f)

        ui.onNodeWithTag("main-circle").performClick()
        ui.waitForIdle()
        fixture.request("/api/v1/categories/SHORT/articles").use {request ->
            ui.onNodeWithTag("category-label").assertTextEquals(ReadingDuration.MEDIUM.label)
            counter.assertContentDescriptionEquals("正在加载分类")
            request.reply(status=503,data=null)
        }
        ui.waitUntil(5_000) {ui.onAllNodesWithTag("remote-retry").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("remote-retry").performClick()
        ui.waitForIdle()
        fixture.request("/api/v1/categories/SHORT/articles").use {request ->
            counter.assertContentDescriptionEquals("正在加载分类")
            request.reply(page(article(12,cover="/cover.png")))
        }
        waitFor("wave-cover-12")
        fixture.request("/cover.png").use {it.send(png(android.graphics.Color.MAGENTA),"image/png")}
        ui.waitUntil(5_000) {cardPixel("wave-cover-12").red>.5f}
        assertTrue(cardPixel("wave-cover-12").blue>.5f)
        counter.assertContentDescriptionEquals("第 1 篇，共 1 篇")
    }

    @Test fun categoryLoadsWithoutSavingAndRemovesTheOldSnapshot(): Unit = Fixture().use { fixture ->
        runBlocking {
            val snapshots = OfflineSnapshots(context, "${fixture.origin}/api/v1")
            val key = "category/${ReadingDuration.QUICK.apiCode}"
            snapshots.write(key, page(article(12)))
            fixture.business.restore()
            assertNull(snapshots.read(key))
            val result = async(Dispatchers.IO) { fixture.business.category(ReadingDuration.QUICK, 1) }
            fixture.request("/api/v1/categories/QUICK/articles").use { it.reply(page(article(41))) }
            assertEquals("41", result.await().items.single().id)
            assertNull(snapshots.read(key))
        }
    }

    @Test fun onlineSearchSlidesBothWaysWithTheArticleCounterAndAuthorLoader(): Unit = Fixture().use {fixture ->
        val model=SearchViewModel(SavedStateHandle())
        screen(fixture) {DeckCounterHost("search",20) {SearchScreen({},{},{},model)}}
        val counter=ui.onNodeWithTag("deck-counter")
        val loader=ui.onNodeWithTag(CONTENT_LOADING_TAG)
        val input=ui.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("search-input")),useUnmergedTree=true)
        input.performTextInput("lap")
        counter.assertContentDescriptionEquals("第 0 篇，共 0 篇")
        input.performImeAction()
        ui.waitForIdle()
        fixture.request("/api/v1/search?keyword=lap").use {request ->
            counter.assertContentDescriptionEquals("正在搜索文章")
            loader.assertDoesNotExist()
            ui.onNodeWithTag("search-wave").assertDoesNotExist()
            request.reply(page(article(10)))
        }
        waitFor("search-wave")
        counter.assertContentDescriptionEquals("第 1 篇，共 1 篇")
        val deck=ui.onNodeWithTag("search-wave")
        val deckBounds=deck.getUnclippedBoundsInRoot()
        val counterBounds=counter.getUnclippedBoundsInRoot()
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithText("作者").performClick()
            ui.mainClock.advanceTimeBy(96)
            fixture.request("/api/v1/search/users?keyword=lap").use {request ->
                assertTrue(deck.getUnclippedBoundsInRoot().left<deckBounds.left)
                assertTrue(counter.getUnclippedBoundsInRoot().left<counterBounds.left)
                val ringBounds=loader.getUnclippedBoundsInRoot()
                assertTrue(ringBounds.left+ringBounds.right>deckBounds.left+deckBounds.right)
                ui.mainClock.advanceTimeBy(400)
                counter.assertDoesNotExist()
                loader.assertIsDisplayed().assertContentDescriptionEquals("正在搜索作者")
                request.reply(page(json("id" to 3,"nickname" to "远端作者")).put("pages",2).put("total",2))
            }
        } finally {
            ui.mainClock.autoAdvance=true
        }
        waitFor("author-3")
        loader.assertDoesNotExist()
        counter.assertDoesNotExist()
        ui.onNodeWithTag("search-author-count",useUnmergedTree=true).assertTextEquals("2")
        ui.onNodeWithTag("load-more").performClick()
        fixture.request("/api/v1/search/users?keyword=lap").use {request ->
            loader.assertIsDisplayed().assertContentDescriptionEquals("正在加载更多作者")
            request.reply(page(json("id" to 4,"nickname" to "另一位作者")).put("page",2).put("pages",2).put("total",2))
        }
        waitFor("author-4")
        loader.assertDoesNotExist()

        val authors=ui.onNodeWithTag("author-results")
        val authorBounds=authors.getUnclippedBoundsInRoot()
        val canSwitchBackAt=SystemClock.elapsedRealtime()+SearchSubmissionGate.SCOPE_INTERVAL_MS
        ui.waitUntil(2_000) {SystemClock.elapsedRealtime()>=canSwitchBackAt}
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithText("文章").performClick()
            ui.mainClock.advanceTimeBy(96)
            fixture.request("/api/v1/search?keyword=lap").use {request ->
                assertTrue(authors.getUnclippedBoundsInRoot().left>authorBounds.left)
                assertTrue(counter.getUnclippedBoundsInRoot().left<counterBounds.left)
                ui.mainClock.advanceTimeBy(400)
                authors.assertDoesNotExist()
                request.reply(page(article(10)))
            }
        } finally {
            ui.mainClock.autoAdvance=true
        }
        waitFor("search-wave")
        assertEquals(counterBounds,counter.getUnclippedBoundsInRoot())
    }

    @Test fun authorSearchStaysIdleUntilSubmittedAndCentersTheEmptyMessage(): Unit = Fixture().use {fixture ->
        val model=SearchViewModel(SavedStateHandle(mapOf("scope" to SearchScope.AUTHORS.name)))
        screen(fixture) {DeckCounterHost("search",20) {SearchScreen({},{},{},model)}}
        val input=ui.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("search-input")),useUnmergedTree=true)
        val loader=ui.onNodeWithTag(CONTENT_LOADING_TAG)
        val icon=ui.onNodeWithTag("empty-content-icon",useUnmergedTree=true)
        input.performTextInput("no-match")
        loader.assertDoesNotExist()
        icon.assertDoesNotExist()
        ui.onNodeWithTag("deck-counter").assertDoesNotExist()
        fixture.assertNoRequest()
        input.performImeAction()
        ui.waitForIdle()
        fixture.request("/api/v1/search/users?keyword=no-match").use {request ->
            loader.assertIsDisplayed()
            icon.assertDoesNotExist()
            request.reply(page())
        }
        ui.waitUntil(5_000) {ui.onAllNodesWithText("未找到作者",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val empty=ui.onNodeWithText("未找到作者",useUnmergedTree=true).assertIsDisplayed()
        val iconBounds=icon.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val textBounds=empty.fetchSemanticsNode().boundsInRoot
        assertEquals(textBounds.center.x,iconBounds.center.x,2f)
        assertTrue(iconBounds.bottom<textBounds.top)
        val layout=mutableListOf<TextLayoutResult>()
        empty.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {assertTrue(it(layout))}
        assertEquals(TextAlign.Center,layout.single().layoutInput.style.textAlign)
        loader.assertDoesNotExist()
        ui.onNodeWithTag("deck-counter").assertDoesNotExist()
    }

    @Test fun readerCentersTheNewLoopAndKeepsPlayingWhenMarkdownIsLate() = checkReaderLoader(ReaderResponse.SLOW)

    @Test fun readerPlaysOneCycleEvenWhenUncachedMarkdownArrivesEarly() = checkReaderLoader(ReaderResponse.FAST)

    @Test fun readerShowsCachedMarkdownWithoutTheLoopAndRefreshesInTheBackground() = checkReaderLoader(ReaderResponse.CACHED)

    private enum class ReaderResponse { CACHED, FAST, SLOW }

    private fun checkReaderLoader(response: ReaderResponse): Unit = Fixture().use {fixture ->
        val business=fixture.business
        val cached=response==ReaderResponse.CACHED
        val initialTitle=if(cached)"缓存的文章标题" else "远端文章 10"
        val refreshedTitle="服务器更新后的标题"
        val detail=article(10,content="## 正文标题\n\n正文渲染成功。")
            .put("title",refreshedTitle)
        runBlocking {
            val seed=async(Dispatchers.IO) {business.category(ReadingDuration.QUICK,1)}
            fixture.request("/api/v1/categories/QUICK/articles").use {it.reply(page(article(10)))}
            seed.await()
            if(cached)OfflineSnapshots(context,"${fixture.origin}/api/v1")
                .write("recent/10",JSONObject(detail.toString()).put("title",initialTitle))
        }
        val artwork=checkNotNull(LottieCompositionFactory.fromAssetSync(context,CONTENT_LOADING_ASSET).value)
        val marker=checkNotNull(artwork.getMarker(CONTENT_LOADING_MARKER))
        val cycleMillis=(1000f*marker.durationFrames/artwork.frameRate).toLong()
        assertEquals(2_000L,cycleMillis)
        val model=ReaderViewModel(SavedStateHandle(mapOf("articleId" to "10")))
        val reading=mutableStateOf(false)
        lateinit var expansion: ArticleExpansionState
        ui.mainClock.autoAdvance=false
        try {
            screen(fixture) {
                expansion=rememberArticleExpansionState()
                ArticleExpansionHost(expansion) {
                    if(reading.value)ReaderScreen({reading.value=false},{},{},Destination.HOME,model)
                }
            }
            val start=ui.mainClock.currentTime
            ui.runOnIdle {
                val source=ArticleSource(checkNotNull(business.knownArticle("10")),Rect(24f,180f,384f,600f),
                    Rect(48f,216f,270f,248f),Rect(48f,270f,120f,294f),Rect(48f,302f,148f,326f),
                    Rect(156f,302f,228f,326f),Color.Black,ArticleOrigin.HOME)
                expansion.open(source) {reading.value=true}
            }
            ui.waitUntil(5_000) {expansion.openingLoad?.cacheChecked==true}
            ui.mainClock.advanceTimeBy(240)
            ui.runOnIdle {assertFalse(reading.value)}
            val preview=ui.onNode(hasTestTag(CONTENT_LOADING_TAG) and hasAnyAncestor(hasTestTag("reader-preview")))
            val center=if(cached) {
                preview.assertDoesNotExist()
                null
            } else {
                val actualCenter=preview.assertIsDisplayed().assertContentDescriptionEquals("正在加载正文")
                    .fetchSemanticsNode().boundsInRoot.center
                val expectedCenter=ui.onNodeWithTag("reader-preview").fetchSemanticsNode().boundsInRoot.center
                assertEquals(expectedCenter.x,actualCenter.x,1f)
                assertEquals(expectedCenter.y,actualCenter.y,1f)
                actualCenter
            }
            ui.mainClock.advanceTimeBy(SceneMotion.HOME_READING_OPEN_MS.toLong())
            fixture.request("/api/v1/articles/10").use {request ->
                ui.mainClock.advanceTimeBy(48)
                ui.onNodeWithTag("reader-title").assertTextEquals(initialTitle)
                val loader=ui.onNode(hasTestTag(CONTENT_LOADING_TAG) and hasAnyAncestor(hasTestTag("reader-screen")))
                val body=ui.onNodeWithTag("section-rich-0")
                if(center==null) {
                    loader.assertDoesNotExist()
                    assertTrue(ui.mainClock.currentTime-start<cycleMillis)
                    body.assertExists()
                } else {
                    loader.assertIsDisplayed().assertContentDescriptionEquals("正在加载正文")
                    val readingCenter=loader.fetchSemanticsNode().boundsInRoot.center
                    assertEquals(center.x,readingCenter.x,1f)
                    assertEquals(center.y,readingCenter.y,1f)
                    if(response==ReaderResponse.SLOW) {
                        ui.mainClock.advanceTimeBy(start+cycleMillis+480-ui.mainClock.currentTime)
                        loader.assertIsDisplayed()
                        body.assertDoesNotExist()
                        assertMoving(loader)
                    }
                }
                request.reply(detail)
                if(response==ReaderResponse.FAST) {
                    ui.waitUntil(5_000) {
                        ui.mainClock.advanceTimeByFrame()
                        ui.onAllNodes(hasTestTag("reader-title") and hasText(refreshedTitle)).fetchSemanticsNodes().isNotEmpty()
                    }
                    assertTrue(ui.mainClock.currentTime-start<cycleMillis)
                    body.assertDoesNotExist()
                    loader.assertIsDisplayed()
                    ui.mainClock.advanceTimeBy(cycleMillis-(ui.mainClock.currentTime-start)+480)
                    body.assertExists()
                }
                if(!cached) {
                    ui.waitUntil(5_000) {
                        ui.mainClock.advanceTimeByFrame()
                        ui.onAllNodesWithTag("section-rich-0").fetchSemanticsNodes().isNotEmpty()
                    }
                    if(response==ReaderResponse.SLOW)loader.assertExists()
                    ui.mainClock.advanceTimeBy(224)
                    loader.assertDoesNotExist()
                }
            }
        } finally {
            ui.mainClock.autoAdvance=true
        }
        waitFor("section-rich-0")
        onView(withText("正文渲染成功。")).check(matches(isDisplayed()))
        ui.onNodeWithTag(CONTENT_LOADING_TAG).assertDoesNotExist()
        ui.waitUntil(5_000) {
            ui.onAllNodes(hasTestTag("reader-title") and hasText(refreshedTitle)).fetchSemanticsNodes().isNotEmpty()
        }
        val top=ui.onNodeWithTag("section-rich-0").getUnclippedBoundsInRoot().top
        ui.mainClock.autoAdvance=false
        try {
            pressBack()
            ui.mainClock.advanceTimeBy(160)
            val returning=ui.onNode(hasTestTag("section-rich-0") and hasAnyAncestor(hasTestTag("reader-preview")))
            ui.onNode(hasText("正文标题") and hasAnyAncestor(hasTestTag("reader-preview")),useUnmergedTree=true).assertExists()
            assertTrue(returning.getUnclippedBoundsInRoot().top>top)
            ui.mainClock.advanceTimeBy(SceneMotion.HOME_READING_CLOSE_MS.toLong())
            ui.onNodeWithTag("reading-expansion").assertDoesNotExist()
            ui.onNodeWithTag("reader-screen").assertDoesNotExist()
        } finally {ui.mainClock.autoAdvance=true}
    }

    @Test fun authorPageUsesTheSameLoaderWhileTheProfileAndArticlesArePending(): Unit = Fixture().use {fixture ->
        screen(fixture) {AuthorScreen("3",{},{},{})}
        fixture.request("/api/v1/users/3/profile").use {request ->
            ui.onNodeWithTag(CONTENT_LOADING_TAG).assertContentDescriptionEquals("正在加载作者主页")
            ui.onNodeWithTag("author-screen").assertDoesNotExist()
            request.reply(page(article(10)).apply {put("user",json("id" to 3,"nickname" to "远端作者"))})
        }
        waitFor("author-screen")
        ui.onNode(hasText("远端作者") and hasAnyAncestor(hasTestTag("profile-info"))).assertExists()
        ui.onNodeWithTag("article-10").assertExists()
        ui.onNodeWithTag(CONTENT_LOADING_TAG).assertDoesNotExist()
    }

    @Test fun identityHoldsItsCoarsePlaceholderUntilTheAvatarIsDecoded(): Unit = Fixture().use {fixture ->
        val url="${fixture.origin}/avatar.png"
        screen(fixture) {
            val photo=rememberProfilePhoto(url)
            CompositionLocalProvider(LocalIdentity provides IdentityAnchor(true,0,photo.bitmap,url,photo.loaded)) {
                Box(Modifier.testTag("identity")) {IdentityAvatar(size=76)}
            }
        }
        fixture.request("/avatar.png").use {request ->
            val coarse=ui.onNodeWithTag("identity-avatar-coarse",useUnmergedTree=true)
            coarse.assertIsDisplayed()
            ui.mainClock.advanceTimeBy(AvatarPixelMotion.DURATION_MILLIS*2L)
            coarse.assertIsDisplayed()
            request.send(png(android.graphics.Color.MAGENTA),"image/png")
        }
        ui.waitUntil(5_000) {
            ui.onAllNodesWithTag("identity-avatar-coarse",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()
        }
        val photo=ui.onNodeWithTag("identity").captureToImage().toPixelMap()
        assertEquals(Color.Magenta,photo[photo.width/2,photo.height/2])
    }

    @Test fun draftBoxFadesTheLoadingLoopOutWhenTheListArrives() {
        val loading=mutableStateOf(true)
        val item=EditorDraft("云端草稿","",0,"2026-11-24T00:00:00Z","fixture")
        ui.setContent {
            SoTheme(ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    val state=remember {DraftBoxState().apply {open(Offset(350f,650f))}}
                    DraftBoxBubble(state,listOf(item),loading.value,false,{},{})
                }
            }
        }
        val loader=ui.onNodeWithTag(CONTENT_LOADING_TAG,useUnmergedTree=true)
        loader.assertContentDescriptionEquals("正在读取草稿箱")
        ui.onNodeWithText(item.title).assertDoesNotExist()
        ui.mainClock.autoAdvance=false
        try {
            ui.runOnIdle {loading.value=false}
            ui.mainClock.advanceTimeBy(64)
            loader.assertExists()
            ui.mainClock.advanceTimeBy(200)
            loader.assertDoesNotExist()
            ui.onNodeWithText(item.title).assertIsDisplayed()
        } finally {ui.mainClock.autoAdvance=true}
    }

    @Test fun draftBoxReusesStartupRefreshAndShowsUnsyncedEditsInTheSameList(): Unit = Fixture().use { fixture ->
        val app = fixture.business
        val owner = "3"
        val profile = json("id" to 3, "nickname" to "测试作者")
        val first = json("id" to 21, "title" to "原始标题", "status" to "DRAFT")
        val second = json("id" to 22, "title" to "另一篇草稿", "status" to "DRAFT")
        runBlocking {
            val signIn = async(Dispatchers.IO) { app.signIn("fixture", "fixture-only", true) }
            fixture.request("/api/v1/auth/login", "POST").use {
                it.reply(json("id" to 3, "nickname" to "测试作者", "accessToken" to "fixture-token"), cookie = true)
            }
            signIn.await()
            val responses = mutableMapOf<String, Any>(
                "/api/v1/users/me" to profile,
                "/api/v1/users/3/profile?tab=approved&page=1&pageSize=20" to page().put("user", profile),
                "/api/v1/articles/drafts?includeRejected=true" to JSONArray(listOf(first, second)),
                "/api/v1/articles/21" to JSONObject(first.toString()).put("authorId", 3).put("version", 1),
                "/api/v1/articles/22" to JSONObject(second.toString()).put("authorId", 3).put("version", 1),
            )
            val sync = async(Dispatchers.IO) { app.syncAtOpen(owner) }
            repeat(responses.size) {
                fixture.take().use { it.reply(checkNotNull(responses.remove(it.path)) { "Unexpected request ${it.path}" }) }
            }
            sync.await()
            assertEquals(listOf("21", "22"), app.draftIndex.value?.request?.value?.map { it.id })
        }
        val storageKey = "draft-box-test:${UUID.randomUUID()}"
        val ready = AtomicBoolean(false)
        val showEditor = mutableStateOf(true)
        val box = DraftBoxState()
        lateinit var session: ArticleEditorSession
        ui.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMotionEnabled provides false, LocalBusiness provides app) {
                    session = rememberArticleEditorSession(storageKey, app, owner)
                    if (showEditor.value) ArticleEditorScreen({}, session = session, onReady = { ready.set(true) })
                    DraftBoxBubble(box, session.drafts, session.draftsLoading, session.draftsFailed,
                        { session.loadDrafts(EditorPalette.Light) }, {}, message = session.draftsMessage)
                }
            }
        }
        ui.waitUntil(20_000) { ready.get() }
        val edited = json("title" to "未同步的新标题", "remoteId" to 21, "remoteVersion" to "1", "dirty" to true)
        val fresh = json("title" to "刚写的新草稿", "dirty" to true)
        val stored = AtomicBoolean(false)
        onView(withContentDescription("文章编辑器")).check { view, error ->
            if (error != null) throw error
            (view as WebView).evaluateJavascript("""
                localStorage.setItem(${JSONObject.quote("$storageKey:edit")}, ${JSONObject.quote(edited.toString())});
                localStorage.setItem(${JSONObject.quote("$storageKey:new")}, ${JSONObject.quote(fresh.toString())});
                true;
            """.trimIndent()) { stored.set(it == "true") }
        }
        ui.waitUntil(6_500) { stored.get() }
        ui.runOnIdle {
            showEditor.value = false
            box.open(Offset(350f, 650f))
        }
        waitFor("draft-box-list")
        ui.onNodeWithText("未同步的新标题").assertIsDisplayed()
        ui.onNodeWithText("刚写的新草稿").assertIsDisplayed()
        ui.onNodeWithText("另一篇草稿").assertIsDisplayed()
        ui.onNodeWithText("原始标题").assertDoesNotExist()
        ui.onNodeWithText("云端草稿", substring = true).assertDoesNotExist()
        fixture.assertNoRequest()
        runBlocking {
            val delete = async(Dispatchers.IO) { app.deleteDraft(owner, "22") }
            fixture.request("/api/v1/articles/22", "DELETE").use { it.reply(null) }
            delete.await()
        }
        ui.waitUntil(5_000) { ui.onAllNodesWithText("另一篇草稿").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun aSingleCardIgnoresQueueSwipesButStillOpens() {
        val articles = mutableStateOf(SampleLibrary.articles.take(2))
        val selection = mutableStateOf(0)
        val opened = AtomicReference<String>()
        ui.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMotionEnabled provides true) {
                    WaveArticleDeck(articles.value, selection.value.coerceAtMost(articles.value.lastIndex),
                        { selection.value = it }, { opened.set(it) }, listTag = "profile-articles", showCounter = false)
                }
            }
        }
        val deck = ui.onNodeWithTag("profile-articles")
        deck.performScrollToIndex(1)
        ui.runOnIdle {
            assertEquals(1, selection.value)
            articles.value = articles.value.take(1)
        }
        val card = ui.onNodeWithTag("article-${articles.value.single().id}")
        val bounds = card.getUnclippedBoundsInRoot()
        deck.performTouchInput { swipeUp() }
        deck.performTouchInput { swipeDown() }
        deck.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
        deck.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        assertEquals(bounds, card.getUnclippedBoundsInRoot())
        card.performClick()
        assertEquals(articles.value.single().id, opened.get())
    }

    @Test fun startupRefreshSurvivesRestartAndKeepsDirtyDraftsSeparate() {
        val reopened = Fixture().use { fixture ->
            runBlocking {
                val app = fixture.business
                val signIn = async(Dispatchers.IO) { app.signIn("fixture", "fixture-only", true) }
                fixture.request("/api/v1/auth/login", "POST").use {
                    it.reply(json("id" to 3, "nickname" to "离线作者", "accessToken" to "fixture-token"), cookie = true)
                }
                signIn.await()
                val day = "2026-11-24"
                val profile = json("id" to 3, "nickname" to "离线作者", "avatarUrl" to "/avatar.png")
                val first = page(article(31, cover = "/public-cover.png")).apply {
                    put("user", profile); put("pages", 2); put("total", 2)
                    put("writingCalendar", JSONArray(listOf(json("date" to day, "wordCount" to 58))))
                }
                val second = page(article(32)).apply { put("page", 2); put("pages", 2); put("total", 2) }
                fun draftSummary(id: Int, version: Int) =
                    json("id" to id, "title" to "草稿 $id", "status" to "DRAFT", "updatedAt" to "revision-$version")
                fun draftBody(id: Int, version: Int) = draftSummary(id, version).apply {
                    put("authorId", 3); put("version", version); put("content", "云端正文版本 $version")
                    if (id == 21) put("coverUrl", "/draft-cover.png")
                }
                suspend fun refresh(version: Int, initial: Boolean) {
                    val responses = mutableMapOf<String, Any>(
                        "/api/v1/users/me" to profile,
                        "/api/v1/users/3/profile?tab=approved&page=1&pageSize=20" to first,
                        "/api/v1/users/3/profile?tab=approved&page=2&pageSize=20" to second,
                        "/api/v1/articles/drafts?includeRejected=true" to JSONArray(listOf(draftSummary(21,version),draftSummary(22,1))),
                        "/api/v1/articles/21" to draftBody(21, version),
                    )
                    val images = mutableSetOf<String>()
                    if (initial) {
                        responses["/api/v1/articles/22"] = draftBody(22, 1)
                        responses["/api/v1/articles/31"] = article(31, content = "自己的第一篇正文")
                        responses["/api/v1/articles/32"] = article(32, content = "自己的第二篇正文")
                        images += listOf("/avatar.png", "/public-cover.png", "/draft-cover.png")
                    }
                    val sync = async(Dispatchers.Default) { app.syncAtOpen("3") }
                    repeat(responses.size + images.size) {
                        fixture.take().use { request ->
                            if (images.remove(request.path)) request.send(png(android.graphics.Color.MAGENTA), "image/png")
                            else request.reply(checkNotNull(responses.remove(request.path)) { "Unexpected request ${request.path}" })
                        }
                    }
                    sync.await()
                }
                refresh(1, true)
                refresh(2, false)
                val recent = async(Dispatchers.IO) { app.articleDetail("41") }
                fixture.request("/api/v1/articles/41").use {
                    it.reply(article(41, content = "最近阅读的正文").put("authorId", 4))
                }
                recent.await()
                app.saveReadingPosition("41", "3", ReadingPosition(5, 24))
                LiveBusiness(context, "${fixture.origin}/api/v1")
            }
        }
        runBlocking {
            reopened.restore()
            assertEquals("3", reopened.session.value.account?.id)
            assertFalse(reopened.session.value.authenticated)
            reopened.syncAtOpen("3")
            val saved = checkNotNull(reopened.ownProfile.value?.request?.value)
            assertEquals(listOf("31", "32"), saved.articles.items.map { it.id })
            assertEquals(58, saved.calendar[LocalDate.parse("2026-11-24")])
            assertEquals("自己的第二篇正文", reopened.cachedArticle("32")?.markdown)
            assertEquals("最近阅读的正文", reopened.cachedArticle("41")?.markdown)
            assertEquals(ReadingPosition(5, 24), reopened.readingPosition("41", "3"))
            assertEquals(listOf("21", "22"), reopened.cachedDrafts("3")?.map { it.id })
            assertEquals(listOf("21", "22"), reopened.draftIndex.value?.request?.value?.map { it.id })
            assertEquals(2L, reopened.cachedDraft("3", "21")?.version)
            assertEquals("云端正文版本 2", reopened.cachedDraft("3", "21")?.content?.body)
            assertNull(reopened.cachedDraft("4", "21"))
            assertNull(reopened.cachedDrafts("4"))
            assertNotNull(reopened.media.open("/draft-cover.png")?.use { it.read() })
        }

        val storageKey = "offline-test:${UUID.randomUUID()}"
        val localKey = "$storageKey:cloud:21"
        val ready = AtomicBoolean(false)
        lateinit var editor: ArticleEditorSession
        ui.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMotionEnabled provides false, LocalBusiness provides reopened) {
                    editor = rememberArticleEditorSession(storageKey, reopened, "3")
                    ArticleEditorScreen({}, session = editor, onReady = { ready.set(true) })
                }
            }
        }
        fun evaluate(script: String): String {
            val result = AtomicReference<String>()
            onView(withContentDescription("文章编辑器")).check { view, error ->
                if (error != null) throw error
                (view as WebView).evaluateJavascript(script, result::set)
            }
            ui.waitUntil(6_500) { result.get() != null }
            return result.get()
        }
        ui.waitUntil(20_000) { ready.get() }
        val dirty = json("remoteId" to "21", "remoteVersion" to "1", "status" to "DRAFT", "dirty" to true,
            "title" to "未同步标题", "body" to "未同步正文")
        evaluate("localStorage.setItem(${JSONObject.quote(localKey)},${JSONObject.quote(dirty.toString())}); true")
        ui.runOnIdle {
            ready.set(false)
            editor.selectDraft(EditorDraft("草稿 21", "", 0, "", localKey, "21"))
        }
        ui.waitUntil(20_000) { ready.get() }
        assertEquals("true", evaluate("body.trim() === '未同步正文' && cloudMetadata.dirty && cloudBlocked"))
        runBlocking {
            reopened.signOut()
            assertNull(reopened.cachedDraft("3", "21"))
            assertNull(reopened.ownProfile.value)
            assertNull(reopened.draftIndex.value)
        }
    }

    private fun screen(fixture: Fixture,content: @Composable ()->Unit) {
        ui.setContent {SoTheme(ThemeMode.LIGHT) {
            CompositionLocalProvider(LocalBusiness provides fixture.business,LocalMotionEnabled provides true,content=content)
        }}
    }

    private fun waitFor(tag: String) {
        ui.waitUntil(5_000) {ui.onAllNodesWithTag(tag,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        ui.waitForIdle()
    }

    private fun cardPixel(tag: String): Color {
        val image=ui.onNodeWithTag(tag,useUnmergedTree=true).captureToImage().toPixelMap()
        return image[image.width/4,image.height*3/4]
    }

    private fun assertMoving(node: SemanticsNodeInteraction) {
        val before=node.captureToImage().toPixelMap()
        ui.mainClock.advanceTimeBy(224)
        val after=node.captureToImage().toPixelMap()
        assertEquals(before.width,after.width)
        assertEquals(before.height,after.height)
        assertTrue((0 until before.height).any {row ->
            (0 until before.width).any {column -> before[column,row]!=after[column,row]}
        })
    }

    private fun article(id: Int,cover: String?=null,content: String?=null)=
        json("id" to id,"title" to "远端文章 $id","authorId" to 3,"authorName" to "远端作者",
            "readMinutes" to 2,"publishedAt" to "2026-11-24","coverUrl" to cover,"content" to content)

    private fun page(vararg items: JSONObject)=
        json("page" to 1,"pages" to 1,"total" to items.size,"list" to JSONArray(items.toList()))

    private fun png(color: Int): ByteArray {
        val image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        image.eraseColor(color)
        val output=ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG,100,output)
        image.recycle()
        return output.toByteArray()
    }

    private inner class Fixture: Closeable {
        private val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1")).apply {soTimeout=8_000}
        val origin="http://127.0.0.1:${server.localPort}"
        val business=LiveBusiness(context,"$origin/api/v1")

        fun request(path: String, method: String = "GET"): Request =
            take().also { request ->
                if (request.method != method || !request.path.startsWith(path)) {
                    request.close()
                    fail("Expected $method $path, got ${request.method} ${request.path}")
                }
            }

        fun take(): Request {
            val socket=server.accept().apply {soTimeout=5_000}
            try {
                val reader=socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val request=reader.readLine().split(' ')
                var bodyLength=0
                while(true) {
                    val header=reader.readLine()?.takeUnless(String::isEmpty) ?: break
                    if(header.substringBefore(':').equals("Content-Length",ignoreCase=true))
                        bodyLength=header.substringAfter(':').trim().toInt()
                }
                return Request(socket, request[0], request[1], reader, bodyLength)
            } catch(error: Throwable) {
                socket.close()
                throw error
            }
        }

        fun assertNoRequest() {
            server.soTimeout = 500
            try {
                take().use { fail("Unexpected ${it.method} ${it.path}") }
            } catch (_: SocketTimeoutException) {
                return
            } finally {
                server.soTimeout = 8_000
            }
        }

        override fun close()=server.close()
    }

    private class Request(private val socket: Socket,val method: String,val path: String,
        private val reader: BufferedReader,private val bodyLength: Int): Closeable {
        fun readJsonBody(): JSONObject {
            val bytes=ByteArray(bodyLength) { reader.read().also {check(it>=0)}.toByte() }
            return JSONObject(bytes.toString(Charsets.UTF_8))
        }

        fun reply(data: Any?,status: Int=200,cookie: Boolean = false)=
            send(json("code" to status,"data" to data).toString().toByteArray(),"application/json",status,cookie)

        fun send(body: ByteArray,type: String,status: Int=200,cookie: Boolean = false) {
            val output=socket.getOutputStream()
            val session = if (cookie) "Set-Cookie: session=fixture; Path=/api; HttpOnly; Max-Age=3600\r\n" else ""
            output.write(("HTTP/1.1 $status ${if(status==200)"OK" else "Unavailable"}\r\nContent-Type: $type\r\n$session"+
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
            output.write(body)
            output.flush()
        }

        override fun close()=socket.close()
    }
}
