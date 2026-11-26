package com.semiovert.app

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.data.*
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.ReadingDuration
import com.semiovert.app.feature.editor.ArticleEditorScreen
import com.semiovert.app.feature.editor.ArticleEditorSession
import com.semiovert.app.feature.editor.rememberArticleEditorSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LiveBusinessIntegrationTest {
    @get:Rule val ui = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val password = "android-fixture-" + UUID.randomUUID()

    private fun fixtureUri(argument: String): String {
        val value = arguments.getString(argument).orEmpty()
        assumeTrue("Requires an isolated local fixture", value.isNotBlank())
        val uri = URI(value)
        require(uri.scheme == "http" && uri.host in setOf("127.0.0.1", "10.0.2.2"))
        return value
    }

    private suspend fun mailCode(email: String, subject: String): String = withContext(Dispatchers.IO) {
        val base = fixtureUri("fixtureMail")
        repeat(15) {
            val messages = readJson("$base/api/v1/messages").optJSONArray("messages").objects()
            val message = messages.firstOrNull { item ->
                item.optString("Subject").contains(subject) &&
                    item.optJSONArray("To").objects().any { it.optString("Address").equals(email, true) }
            }
            if (message != null) {
                val content = readJson("$base/api/v1/message/${segment(message.getString("ID"))}").getString("Text")
                return@withContext checkNotNull(Regex("(?m)^([0-9]{6})\\s*$").find(content)).groupValues[1]
            }
            delay(300)
        }
        error("Fixture email did not arrive")
    }

    private fun readJson(url: String): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 2500
            connection.readTimeout = 3500
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    private suspend fun register(api: LiveBusiness): Pair<String, String> {
        val name = "android_" + UUID.randomUUID().toString().take(12)
        val email = "$name@semi-overt.test"
        api.requestCode(email, registration = true, challenge = null)
        api.register(name, email, password, mailCode(email, "注册"))
        return checkNotNull(api.session.value.account).id to email
    }

    @Test fun credentialsUploadsWritingReviewAndReadingUseTheRustApi() = runBlocking {
        val endpoint = fixtureUri("fixtureEndpoint")
        val transport = ApiTransport(endpoint)
        val initial = LiveBusiness(context, endpoint)
        val (owner, email) = register(initial)
        val app = LiveBusiness(context, endpoint)
        app.restore()
        assertEquals(owner, app.session.value.account?.id)
        val original = app.updateCurrentProfile(owner).profile
        val image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().apply { image.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        image.recycle()
        val media = app.upload(owner, bytes, "image/png", "ARTICLE_COVER")
        assertTrue(media.url.contains("/static/uploads/"))
        val avatar = checkNotNull(ProfileImages.save(context) { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888) })
        val cover = checkNotNull(ProfileImages.save(context) { Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888) })
        app.saveProfile(owner, original, original.copy(name = "Android 联调作者", bio = "公开简介",
            avatarPhoto = avatar, coverPhoto = cover, coverPosition = .25f))
        val publicProfile = app.profile(owner, 1, false).account.profile
        assertEquals("公开简介", publicProfile.bio)
        assertTrue(publicProfile.avatarPhoto.orEmpty().contains("/static/uploads/"))
        assertTrue(publicProfile.coverPhoto.orEmpty().contains("/static/uploads/"))
        assertEquals(.25f, app.updateCurrentProfile(owner).profile.coverPosition, 0f)
        app.saveProfile(owner, app.updateCurrentProfile(owner).profile,
            app.updateCurrentProfile(owner).profile.copy(bio = ""))
        assertEquals("", app.profile(owner, 1, false).account.profile.bio)
        assertNull(app.profile(owner, 1, false).account.email)

        app.history.enable(owner, true)
        val created = app.createDraft(owner)
        val text = "## 第一节\n\n" + "跨端草稿同步与服务器审核验证。".repeat(8)
        val content = DraftContent("安卓集成验证 ${created.id}", text, "摘要", media.url)
        val saved = app.saveDraft(owner, created, content)
        assertEquals(saved.version, app.editDraft(owner, saved.id).version)
        val stale = runCatching { app.saveDraft(owner, created, content.copy(title = "旧写入")) }.exceptionOrNull()
        assertEquals(409, (stale as? ApiFailure)?.status)
        assertEquals(text, app.history.list(owner).single().after)
        assertTrue(app.history.list((owner.toLong() + 1000).toString()).isEmpty())
        assertTrue(app.profile(owner, 1, true).calendar.values.any { it > 0 })
        assertTrue(app.profile(owner, 1, false).calendar.isEmpty())

        val reviewer = transport.request("/auth/login", "POST",
            json("account" to "fixture_admin", "password" to "fixture-reviewer-2026").bytes()).obj().getString("token")
        val pending = app.submitDraft(owner, saved)
        assertEquals("PENDING", pending.status)
        assertEquals("DRAFT", app.cancelReview(owner, pending.id).status)
        val resubmitted = app.submitDraft(owner, app.editDraft(owner, saved.id))
        transport.request("/review/${resubmitted.id}/decision", "POST",
            json("action" to "RETURN", "reason" to "补充来源").bytes(), accessToken = reviewer)
        assertEquals("补充来源", app.drafts(owner).single { it.id == saved.id }.reason)
        val returned = app.editDraft(owner, saved.id)
        assertEquals("RETURNED", returned.status)
        val corrected = app.saveDraft(owner, returned, content.copy(body = "$text\n\n补充来源。"))
        app.submitDraft(owner, corrected)
        transport.request("/review/${saved.id}/decision", "POST", json("action" to "APPROVE").bytes(),
            accessToken = reviewer)
        assertTrue(app.home().any { it.id == saved.id })
        assertTrue(app.category(ReadingDuration.QUICK, 1).items.any { it.id == saved.id })
        assertTrue(app.searchArticles(content.title, 1).items.any { it.id == saved.id })
        assertTrue(app.searchAuthors("Android 联调作者", 1).items.any { it.id == owner })
        assertTrue(app.articleDetail(saved.id).markdown.orEmpty().endsWith("补充来源。"))
        assertTrue(app.drafts(owner).none { it.id == saved.id })

        val rejected = app.saveDraft(owner, app.createDraft(owner), content.copy(title = "拒审验证"))
        app.submitDraft(owner, rejected)
        transport.request("/review/${rejected.id}/decision", "POST",
            json("action" to "REJECT", "reason" to "不符合发布要求").bytes(), accessToken = reviewer)
        val rejection = app.drafts(owner).single { it.id == rejected.id }
        assertEquals("REJECTED", rejection.status)
        assertEquals("不符合发布要求", rejection.reason)
        assertFalse(app.editDraft(owner, rejected.id).editable)
        app.deleteDraft(owner, rejected.id)

        app.history.clear(owner)
        assertTrue(app.history.list(owner).isEmpty())
        assertTrue(app.profile(owner, 1, true).calendar.values.any { it > 0 })
        try {
            app.requestCode(email, registration = false, challenge = null)
        } catch (failure: ApiFailure) {
            val seconds = failure.retryAfter?.takeIf { failure.status == 429 && it in 1..60 } ?: throw failure
            delay(seconds * 1000 + 250)
            app.requestCode(email, registration = false, challenge = null)
        }
        app.reset(email, mailCode(email, "重置"), "fixture-reset-2026")
        assertNull(app.session.value.account)
        assertTrue(runCatching { app.signIn(email, password, true) }.isFailure)
        app.signIn(email, "fixture-reset-2026", true)
        app.signOut()
        val signedOut = LiveBusiness(context, endpoint)
        signedOut.restore()
        assertNull(signedOut.session.value.account)
    }

    @Test fun editorAutosavesAndSubmitsTheBundledDocument() {
        val endpoint = fixtureUri("fixtureEndpoint")
        val api = LiveBusiness(context, endpoint)
        val owner = runBlocking { register(api).first }
        val storageKey = "business-test:$owner:${UUID.randomUUID()}"
        val ready = AtomicBoolean(false)
        val open = mutableStateOf(true)
        val editorSession = AtomicReference<ArticleEditorSession>()
        ui.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides true, LocalBusiness provides api) {
                SoTheme {
                    val editor = rememberArticleEditorSession(storageKey, api, owner)
                    editorSession.set(editor)
                    if (open.value) ArticleEditorScreen(
                        onBack = { open.value = false }, onReady = { ready.set(true) }, session = editor,
                    )
                }
            }
        }
        ui.waitUntil(20_000) { ready.get() }
        assertEquals("true", evaluate("cloudReady && !cloudBlocked && !cloudLocked && !cloudMetadata.remoteId"))
        val content = "编辑器保存和送审使用真实服务器接口，版本以服务器响应为准。".repeat(6)
        evaluate("""
            document.querySelector('#topic-title').value = '安卓编辑器云端自动保存';
            editor.commands.setContent(${JSONObject.quote("<p>$content</p>")});
            document.querySelector('#topic-title').dispatchEvent(new Event('input'));
            true;
        """.trimIndent())
        val draft = runBlocking {
            repeat(20) {
                val saved = api.drafts(owner).firstOrNull { it.title == "安卓编辑器云端自动保存" }
                if (saved != null) return@runBlocking saved
                delay(500)
            }
            error("Editor changes did not reach SQLite")
        }
        val saved = runBlocking { api.editDraft(owner, draft.id) }
        assertEquals(content, saved.content.body.trim())
        ui.waitUntil(10_000) { evaluate("!cloudBusy") == "true" }
        val remoteBody = "$content\n\n另一台设备已修改正文。"
        runBlocking { api.saveDraft(owner, saved, saved.content.copy(body = remoteBody)) }
        assertEquals("true", evaluate("""
            window.publishIconCentered = () => {
              const bounds = $('.send-button').getBoundingClientRect();
              const artwork = $('.send-button [data-icon="send"] path');
              const center = new DOMPoint(13.75, 10.25).matrixTransform(artwork.getScreenCTM());
              return Math.hypot(center.x - (bounds.left + bounds.width / 2),
                center.y - (bounds.top + bounds.height / 2)) < .5;
            };
            const send = $('.send-button');
            const sendIcon = $('.send-button [data-icon="send"]');
            const ring = $('.sling-well');
            const ink = getComputedStyle(sendIcon).color;
            window.recordPublishFade = change => {
              const read = () => [
                Number(getComputedStyle(send, '::before').opacity), Number(getComputedStyle(ring).opacity),
                getComputedStyle(send).opacity === '1' && getComputedStyle(send).backgroundColor === 'rgb(229, 240, 230)' &&
                  getComputedStyle(sendIcon).color === ink && getComputedStyle(sendIcon).opacity === '1',
              ];
              const frames = [read()];
              window.publishFrames = frames;
              window.publishFadeDone = false;
              change();
              const finish = performance.now() + 300;
              requestAnimationFrame(function sample(now) {
                frames.push(read());
                if (now < finish) requestAnimationFrame(sample);
                else window.publishFadeDone = true;
              });
            };
            window.publishFadeMatches = (start, end) => {
              const frames = publishFrames;
              return frames[0][0] === start && frames[0][1] === start &&
                frames.at(-1)[0] === end && frames.at(-1)[1] === end &&
                (reducePreviewMotion() || frames.some(([fill]) => fill > 0 && fill < 1)) &&
                frames.every(([fill, ring, stable], index) => stable && Math.abs(fill - ring) < .01 &&
                  (!index || (fill - frames[index - 1][0]) * (end - start) >= 0));
            };
            cloudBusy = true;
            recordPublishFade(() => send.click());
            state.preview && send.disabled && !dialog.open && $('#publish-control').classList.contains('sling') &&
              getComputedStyle(send, '::before').backgroundColor === 'rgb(17, 132, 128)' &&
              sendIcon === $('.send-button [data-icon]') && publishIconCentered() &&
              $('#preview-author-name').textContent === ${JSONObject.quote(api.session.value.account?.profile?.name)}
        """.trimIndent()))
        ui.waitUntil(5_000) { evaluate("publishFadeDone") == "true" }
        assertEquals("true", evaluate("publishFadeMatches(0, 1)"))
        assertEquals("true", evaluate("""
            cloudBusy = false;
            updateCloudButtons();
            recordPublishFade(() => $('#back-button').click());
            !state.preview && !$('.send-button').disabled
        """.trimIndent()))
        ui.waitUntil(5_000) { evaluate("publishFadeDone") == "true" }
        assertEquals("true", evaluate("publishFadeMatches(1, 0)"))
        assertEquals("DRAFT", runBlocking { api.editDraft(owner, draft.id).status })
        assertEquals("true", evaluate("""
            $('.send-button').click();
            $('.send-button').click();
            $('.send-button').click();
            !dialog.open && cloudBusy && $('.send-button').disabled &&
              (reducePreviewMotion() || $('#publish-control').hasAttribute('data-launching'))
        """.trimIndent()))
        ui.waitUntil(10_000) { evaluate("cloudBlocked") == "true" }
        assertEquals("true", evaluate("$('.send-button').disabled && getComputedStyle($('.send-button')).opacity === '1'"))
        assertEquals("true", evaluate("JSON.parse(localStorage.getItem(draftKey)).dirty && body.trim() === ${JSONObject.quote(content)}"))
        assertEquals("DRAFT", runBlocking { api.editDraft(owner, draft.id).status })
        evaluate("$('#cloud-recovery').click(); dialog.querySelector('[data-action=cloud-reload]').click(); true;")
        ui.waitUntil(10_000) { evaluate("!cloudBusy && !cloudBlocked") == "true" }
        assertEquals("true", evaluate("body.trim() === ${JSONObject.quote(remoteBody)}"))
        assertEquals("true", evaluate("""
            $('.send-button').click();
            window.pullPublish = (offsetX, offsetY, expectedProgress, expectedRotation) => {
              const control = $('#publish-control');
              const button = $('.send-button');
              const scale = control.getBoundingClientRect().width / control.offsetWidth;
              const pointer = (type, x, y) => button.dispatchEvent(new PointerEvent(type, {
                pointerId: 1, pointerType: 'touch', clientX: 50 + x * scale, clientY: 50 + y * scale, bubbles: true,
              }));
              pointer('pointerdown', 0, 0);
              pointer('pointermove', offsetX, offsetY);
              const armed = control.hasAttribute('data-armed');
              const progress = parseFloat(control.querySelector('.sling-tension').getAttribute('stroke-dasharray'));
              const rotation = button.querySelector('[data-icon="send"]').style.transform;
              const centered = publishIconCentered();
              pointer('pointerup', offsetX, offsetY);
              button.dispatchEvent(new MouseEvent('click', { detail: 1, bubbles: true }));
              return armed === (expectedProgress === 1) && Math.abs(progress - expectedProgress) < .001 &&
                rotation === 'rotate(' + expectedRotation + 'deg)' && centered;
            };
            pullPublish(0, 6, .06, -45) && !cloudBusy &&
              pullPublish(-68, 0, .68, 45) && !cloudBusy && !dialog.open
        """.trimIndent()))
        assertEquals("true", evaluate("pullPublish(0, 100, 1, -45) && cloudBusy && !dialog.open"))
        val pending = runBlocking {
            repeat(15) {
                val current = api.editDraft(owner, draft.id)
                if (current.status == "PENDING") return@runBlocking current
                delay(400)
            }
            error("Editor did not submit the synchronized draft")
        }
        ui.waitUntil(10_000) {
            evaluate("""
                cloudLocked && state.preview && titleInput.readOnly && !editor.isEditable &&
                  $('#review-status').textContent === '待审核' && !$('#cancel-review').hidden
            """.trimIndent()) == "true"
        }
        assertEquals(checkNotNull(saved.version) + 2, pending.version)
        evaluate("SemiNativeEditor.back()")
        ui.waitUntil(10_000) { !open.value }
        ui.runOnIdle {
            ready.set(false)
            editorSession.get().selectDraft(null)
            open.value = true
        }
        ui.waitUntil(20_000) { ready.get() }
        assertEquals("true", evaluate("!state.preview && editor.isEditable && !titleInput.readOnly"))
        evaluate("titleInput.value = '即将舍弃的草稿'; titleInput.dispatchEvent(new Event('input')); true;")
        ui.waitUntil(10_000) { evaluate("Boolean(cloudMetadata.remoteId) && !cloudBusy") == "true" }
        val discardId = JSONTokener(evaluate("cloudMetadata.remoteId")).nextValue().toString()
        assertEquals("true", evaluate("""
            const discard = $('#overflow-menu [data-action=discard]');
            $('#more-button').click();
            discard.click();
            const confirm = dialog.open && $('#dialog-content').textContent.includes('云端');
            $('#dialog-form').requestSubmit();
            confirm && cloudBusy
        """.trimIndent()))
        ui.waitUntil(10_000) {
            evaluate("!cloudBusy && !cloudMetadata.remoteId && !titleInput.value && !localStorage.getItem(draftKey)") == "true"
        }
        assertEquals(404, assertThrows(ApiFailure::class.java) {
            runBlocking { api.editDraft(owner, discardId) }
        }.status)
        runBlocking {
            api.cancelReview(owner, draft.id)
            api.deleteDraft(owner, draft.id)
            api.signOut()
        }
    }

    private fun evaluate(script: String): String {
        val result = AtomicReference<String>()
        onView(withContentDescription("文章编辑器")).check { view, error ->
            if (error != null) throw error
            (view as WebView).evaluateJavascript(script, result::set)
        }
        ui.waitUntil(6500) { result.get() != null }
        return result.get()
    }
}
