package com.semiovert.app.core.data

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import com.semiovert.app.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import java.io.File
import java.io.ByteArrayOutputStream

internal val LocalBusiness = staticCompositionLocalOf<LiveBusiness?> { null }

internal data class Account(val id: String, val profile: PreviewProfile, val email: String?, val role: String?)
internal data class AccountState(val account: Account? = null, val restoring: Boolean = true, val error: String? = null)
internal data class PublicProfile(val account: Account, val articles: Page<Article>, val calendar: Map<LocalDate, Int>)
internal data class UploadedImage(val url: String, val color: String?)

internal class LiveBusiness(
    private val context: Context,
    baseUrl: String = PRODUCTION_API,
    val challengeSiteKey: String = "",
) {
    private val transport = ApiTransport(baseUrl)
    private val store = SessionStore(context, if (baseUrl == PRODUCTION_API) "device-session" else "fixture-device-session")
    private val lock = Mutex()
    private val cards = LruCache<String, Article>(300)
    private val authors = LruCache<String, Author>(240)
    private val profileSettings = context.getSharedPreferences("account_display", Context.MODE_PRIVATE)
    private val accountState = MutableStateFlow(AccountState())
    val session = accountState.asStateFlow()
    val history = WritingHistory(context)
    private val updates = MutableStateFlow(0)
    val writes = updates.asStateFlow()

    private data class Credentials(val token: String, val cookie: RefreshCookie, val owner: String, val generation: UUID)
    @Volatile private var credentials: Credentials? = null
    private var restored = false
    private var pendingCookie: RefreshCookie? = null

    fun mediaUrl(value: String?) = transport.mediaUrl(value)
    fun knownArticle(id: String) = cards.get(id)
    fun knownAuthor(id: String) = authors.get(id)
    fun markWrite() { updates.value++ }

    suspend fun restore() = lock.withLock {
        if (restored) return@withLock
        accountState.value = AccountState()
        try {
            val cookie = pendingCookie ?: store.read()
            if (cookie != null) rotate(cookie, UUID.randomUUID())
            else accountState.value = AccountState(restoring = false)
            restored = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if ((error as? ApiFailure)?.status == 401) {
                clear()
                restored = true
            } else accountState.value = AccountState(restoring = false, error = error.userMessage())
        }
    }

    suspend fun signIn(account: String, password: String, remember: Boolean) =
        startSession("/auth/login", json("account" to account.trim(), "password" to password, "rememberMe" to remember))

    suspend fun register(username: String, email: String, password: String, code: String) =
        startSession("/auth/register", json("username" to username.trim(), "email" to email.trim(),
            "password" to password, "emailCode" to code))

    suspend fun requestCode(email: String, registration: Boolean, challenge: String?) {
        transport.request(if (registration) "/auth/register-code" else "/auth/forgot-password", "POST",
            json("email" to email.trim(), "cfTurnstileToken" to challenge).bytes())
    }

    suspend fun reset(email: String, code: String, password: String) = lock.withLock {
        withContext(NonCancellable) {
            transport.request("/auth/reset-password", "POST",
                json("email" to email.trim(), "code" to code, "newPassword" to password).bytes())
            if (session.value.account?.email?.equals(email.trim(), true) == true) clear()
        }
    }

    suspend fun signOut() = lock.withLock {
        withContext(NonCancellable) {
            val cookie = credentials?.cookie ?: pendingCookie ?: store.read()
            if (cookie != null) transport.request("/auth/logout", "POST", cookie = cookie.header)
            clear()
            restored = true
        }
    }

    private suspend fun startSession(path: String, payload: JSONObject) = lock.withLock {
        withContext(NonCancellable) {
            val previous = credentials?.cookie ?: pendingCookie ?: store.read()
            accept(transport.request(path, "POST", payload.bytes(), cookie = previous?.header), UUID.randomUUID())
            restored = true
        }
    }

    private suspend fun rotate(cookie: RefreshCookie, generation: UUID) = withContext(NonCancellable) {
        if (!cookie.usable()) throw ApiFailure(401, "登录已过期，请重新登录")
        pendingCookie = cookie
        accept(transport.request("/auth/refresh", "POST", cookie = cookie.header), generation)
    }

    private suspend fun accept(response: ApiReply, generation: UUID) {
        val auth = response.obj()
        val token = auth.text("accessToken") ?: auth.text("token") ?: throw ApiFailure("未收到登录凭据")
        val cookie = RefreshCookie.from(response.cookies) ?: throw ApiFailure("未收到设备会话")
        val basic = account(auth)
        credentials = Credentials(token, cookie, basic.id, generation)
        pendingCookie = null
        val persisted = runCatching { store.write(cookie) }.isSuccess
        if (!persisted) runCatching { store.write(null) }
        val existing = accountState.value.account?.takeIf { it.id == basic.id }
        accountState.value = AccountState(existing ?: basic, restoring = false,
            error = if (!persisted) "无法保存登录状态，重新打开应用后需再登录" else null)
    }

    private suspend fun clear() {
        credentials = null
        pendingCookie = null
        store.write(null)
        accountState.value = AccountState(restoring = false)
    }

    private fun requireSame(current: Credentials?, original: Credentials): Credentials =
        current?.takeIf { it.generation == original.generation } ?: throw ApiFailure(401, "登录已变更，请重新操作")

    private suspend fun authenticated(path: String, method: String = "GET", body: ByteArray? = null,
        contentType: String = "application/json", owner: String? = null, optional: Boolean = false): ApiReply {
        val initial = credentials
        if (initial == null && optional) return transport.request(path, method, body, contentType)
        if (initial == null || (owner != null && initial.owner != owner)) throw ApiFailure(401, "请先登录")
        val reply = try {
            transport.request(path, method, body, contentType, accessToken = initial.token)
        } catch (error: ApiFailure) {
            if (error.status != 401) throw error
            val fresh = lock.withLock {
                val current = requireSame(credentials, initial)
                if (current.cookie == initial.cookie) {
                    try {
                        rotate(current.cookie, current.generation)
                    } catch (failure: ApiFailure) {
                        if (failure.status == 401) clear()
                        throw failure
                    }
                }
                requireSame(credentials, initial)
            }
            transport.request(path, method, body, contentType, accessToken = fresh.token)
        }
        requireSame(credentials, initial)
        return reply
    }

    suspend fun home(): List<Article> {
        val data = try { authenticated("/home", optional = true) }
        catch (error: ApiFailure) { if (error.status == 401 && credentials == null) transport.request("/home") else throw error }
        val hero = data.obj().getJSONObject("hero")
        return (listOfNotNull(hero.optJSONObject("primary")) + hero.optJSONArray("secondary").objects())
            .map(::article).distinctBy(Article::id)
    }

    suspend fun category(duration: ReadingDuration, page: Int): Page<Article> =
        transport.request("/categories/${duration.apiCode}/articles?page=$page&pageSize=20").obj().page(::article)

    suspend fun searchArticles(keyword: String, page: Int): Page<Article> =
        transport.request("/search?keyword=${segment(keyword)}&page=$page&pageSize=20").obj().page(::article)

    suspend fun searchAuthors(keyword: String, page: Int): Page<Author> =
        transport.request("/search/users?keyword=${segment(keyword)}&page=$page&pageSize=20").obj().page(::author)

    suspend fun articleDetail(id: String): Article = article(transport.request("/articles/${segment(id)}").obj())

    suspend fun drafts(owner: String): List<CloudDraftItem> =
        authenticated("/articles/drafts?includeRejected=true", owner = owner).array().objects().mapNotNull { item ->
            val id = item.id() ?: return@mapNotNull null
            CloudDraftItem(id, item.text("title").orEmpty(), item.text("status").orEmpty(),
                item.text("updatedAt").orEmpty(), item.optInt("wordCount"), item.text("latestReason"))
        }

    suspend fun editDraft(owner: String, id: String): CloudDraft {
        val data = authenticated("/articles/${segment(id)}", owner = owner).obj()
        if (data.id("authorId") != owner) throw ApiFailure(403, "不能打开其他账号的草稿")
        return cloudDraft(data)
    }

    suspend fun createDraft(owner: String): CloudDraft =
        cloudDraft(authenticated("/articles", "POST", owner = owner).obj())

    suspend fun saveDraft(owner: String, draft: CloudDraft, content: DraftContent): CloudDraft {
        val version = draft.version ?: throw ApiFailure("服务器尚未提供草稿版本，当前内容仅保存在本机")
        if (!draft.editable) throw ApiFailure(409, "当前文章不能编辑")
        val body = json("title" to content.title, "content" to content.body, "summary" to content.summary,
            "coverUrl" to content.coverUrl, "version" to version)
        val result = cloudDraft(authenticated("/articles/${segment(draft.id)}/draft", "PUT", body.bytes(), owner = owner).obj())
        history.record(owner, result, draft.content.body)
        markWrite()
        return result
    }

    suspend fun submitDraft(owner: String, draft: CloudDraft): CloudDraft {
        val version = draft.version ?: throw ApiFailure("请先保存草稿，再提交审核")
        val current = editDraft(owner, draft.id)
        if (!current.editable) throw ApiFailure(409, DRAFT_CHANGED)
        val currentVersion = current.version ?: throw ApiFailure("服务器尚未支持按草稿版本送审，草稿已保存")
        if (currentVersion != version) throw ApiFailure(409, DRAFT_CHANGED)
        val result = cloudDraft(authenticated("/articles/${segment(draft.id)}/submit?version=$version", "POST", owner = owner).obj())
        markWrite()
        return result
    }

    suspend fun cancelReview(owner: String, id: String): CloudDraft {
        val result = cloudDraft(authenticated("/articles/${segment(id)}/cancel-review", "POST", owner = owner).obj())
        markWrite()
        return result
    }

    suspend fun deleteDraft(owner: String, id: String) {
        authenticated("/articles/${segment(id)}", "DELETE", owner = owner)
        markWrite()
    }

    private fun cloudDraft(data: JSONObject): CloudDraft = CloudDraft(
        data.id() ?: throw ApiFailure("服务器未返回文章 ID"),
        if (data.isNull("version")) null else data.getLong("version"),
        data.text("status") ?: throw ApiFailure("服务器未返回文章状态"),
        DraftContent(data.text("title").orEmpty(), data.text("content").orEmpty(),
            data.text("summary").orEmpty(), mediaUrl(data.text("coverUrl")).orEmpty()),
        data.text("updatedAt").orEmpty(), data.text("latestReviewReason") ?: data.text("rejectReason"),
    )

    suspend fun updateCurrentProfile(owner: String): Account {
        val result = account(authenticated("/users/me", owner = owner).obj())
        if (session.value.account?.id == result.id) accountState.value = session.value.copy(account = result)
        return result
    }

    suspend fun saveProfile(owner: String, initial: PreviewProfile, edited: PreviewProfile) {
        suspend fun image(name: String?, previous: String?, type: String): String? {
            if (name == previous || name == null) return name
            val file = ProfileImages.uploadFile(context, name) ?: throw ApiFailure("本机图片已不可用，请重新选择")
            return uploadFile(owner, file, type).url
        }
        val avatar = image(edited.avatarPhoto, initial.avatarPhoto, "AVATAR")
        val cover = image(edited.coverPhoto, initial.coverPhoto, "COVER")
        val result = account(authenticated("/users/me", "PUT", json("nickname" to edited.name,
            "signature" to edited.bio, "clearSignature" to edited.bio.isBlank(),
            "avatarUrl" to avatar, "coverUrl" to cover.orEmpty()).bytes(), owner = owner).obj())
        if (edited.bio.isBlank() && result.profile.bio.isNotBlank()) throw ApiFailure("服务器尚不支持清空简介")
        val position = if (result.profile.coverPhoto == null) .5f else edited.coverPosition
        profileSettings.edit().putFloat("position:$owner", position)
            .putString("cover:$owner", result.profile.coverPhoto).apply()
        accountState.value = session.value.copy(account = result.copy(profile = result.profile.copy(coverPosition = position)))
        listOf(edited.avatarPhoto, edited.coverPhoto).forEach { ProfileImages.delete(context, it) }
        markWrite()
    }

    private suspend fun uploadFile(owner: String, file: File, type: String): UploadedImage {
        val mime = if (file.extension == "png") "image/png" else "image/jpeg"
        val bytes = withContext(Dispatchers.IO) {
            if (file.length() !in 1..(5L * 1024 * 1024)) throw ApiFailure("请选择小于 5 MB 的图片")
            file.readBytes()
        }
        return upload(owner, bytes, mime, type)
    }

    suspend fun upload(owner: String, bytes: ByteArray, mime: String, type: String): UploadedImage {
        if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) throw ApiFailure("请选择小于 5 MB 的图片")
        val suffix = when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw ApiFailure("支持 JPG、PNG、WebP 图片")
        }
        require(type in setOf("AVATAR", "COVER", "ARTICLE_COVER", "ARTICLE_CONTENT"))
        val boundary = "SemiOvert${UUID.randomUUID()}"
        val body = withContext(Dispatchers.Default) {
            ByteArrayOutputStream(bytes.size + 400).apply {
                write("--$boundary\r\nContent-Disposition: form-data; name=\"bizType\"\r\n\r\n$type\r\n".toByteArray())
                write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"image.$suffix\"\r\nContent-Type: $mime\r\n\r\n".toByteArray())
                write(bytes)
                write("\r\n--$boundary--\r\n".toByteArray())
            }.toByteArray()
        }
        val data = authenticated("/upload", "POST", body, "multipart/form-data; boundary=$boundary", owner = owner).obj()
        return UploadedImage(mediaUrl(data.text("url")) ?: throw ApiFailure("上传成功但服务器未返回图片地址"),
            data.text("dominantColor"))
    }

    suspend fun profile(id: String, page: Int, owner: Boolean): PublicProfile {
        val path = "/users/${segment(id)}/profile?tab=approved&page=$page&pageSize=20"
        val data = (if (owner) authenticated(path, owner = id) else transport.request(path)).obj()
        val profile = account(data.getJSONObject("user"))
        val calendar = data.optJSONArray("writingCalendar").objects().mapNotNull { item ->
            runCatching { LocalDate.parse(item.getString("date")) to item.getInt("wordCount") }.getOrNull()
        }.toMap()
        return PublicProfile(profile, data.page(::article), calendar)
    }

    private fun account(data: JSONObject): Account {
        val person = author(data)
        val cover = mediaUrl(data.text("coverUrl"))
        val position = if (cover != null && profileSettings.getString("cover:${person.id}", null) == cover)
            profileSettings.getFloat("position:${person.id}", .5f).coerceIn(0f, 1f) else .5f
        return Account(person.id, PreviewProfile(person.name, person.handle, person.bio,
            avatarPhoto = person.avatarUrl, coverPhoto = cover, coverPosition = position),
            data.text("email"), data.text("role"))
    }

    private fun author(data: JSONObject): Author {
        val id = data.id() ?: data.id("userId") ?: throw ApiFailure("用户缺少有效 ID")
        val handle = data.text("username").orEmpty()
        val person = Author(id, data.text("nickname") ?: handle.ifBlank { "作者" },
            data.text("signature").orEmpty(), mediaUrl(data.text("avatarUrl")), handle)
        authors.put(id, person)
        return person
    }

    private fun article(data: JSONObject): Article {
        val id = data.id() ?: data.id("articleId") ?: throw ApiFailure("文章缺少有效 ID")
        val person = data.optJSONObject("author")?.let(::author) ?: Author(
            data.id("authorId").orEmpty(), data.text("authorName") ?: "作者", "", mediaUrl(data.text("authorAvatar")),
        ).also { if (it.id.isNotBlank()) authors.put(it.id, it) }
        val value = Article(id, data.text("title") ?: "未命名文章", data.text("summary") ?: data.text("previewText"),
            person, data.optInt("readMinutes", 1), (data.text("publishedAt") ?: data.text("updatedAt")).orEmpty().take(10),
            null, if (id.toLong() % 2 == 0L) CardTone.MINT else CardTone.APRICOT, emptyList(),
            mediaUrl(data.text("coverUrl")), data.text("coverColor"), data.text("content") ?: knownArticle(id)?.markdown)
        cards.put(id, value)
        return value
    }

    private fun <T> JSONObject.page(map: (JSONObject) -> T) =
        Page(optJSONArray("list").objects().map(map), optInt("page", 1), optInt("pages"), optInt("total"))
}

internal fun JSONObject.bytes() = toString().toByteArray(Charsets.UTF_8)
