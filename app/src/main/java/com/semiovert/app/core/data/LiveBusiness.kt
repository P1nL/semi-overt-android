package com.semiovert.app.core.data

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import com.semiovert.app.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import java.io.File
import java.io.ByteArrayOutputStream

internal val LocalBusiness = staticCompositionLocalOf<LiveBusiness?> { null }

internal data class Account(val id: String, val profile: PreviewProfile, val email: String?, val role: String?)
internal data class AccountState(val account: Account? = null, val restoring: Boolean = true, val error: String? = null,
    val authenticated: Boolean = false)
internal data class PublicProfile(val account: Account, val articles: Page<Article>, val calendar: Map<LocalDate, Int>)
internal data class OwnProfileState(val owner: String, val request: RequestState<PublicProfile>)
internal data class DraftIndexState(val owner: String, val request: RequestState<List<CloudDraftItem>>)
internal data class ReadingPosition(val index: Int, val offset: Int)
internal data class UploadedImage(val url: String, val color: String?)

internal class LiveBusiness(
    private val context: Context,
    baseUrl: String = PRODUCTION_API,
    val challengeSiteKey: String = "",
) {
    private val transport = ApiTransport(baseUrl)
    private val store = SessionStore(context, if (baseUrl == PRODUCTION_API) "device-session"
        else "fixture-device-session-${Uri.encode(baseUrl)}")
    private val snapshots = OfflineSnapshots(context, baseUrl)
    val media = CachedMedia(context, transport::mediaUrl)
    private val lock = Mutex()
    private val profileRefresh = Mutex()
    private val draftIndexLock = Mutex()
    private val cards = LruCache<String, Article>(300)
    private val authors = LruCache<String, Author>(240)
    private val profileSettings = context.getSharedPreferences("account_display", Context.MODE_PRIVATE)
    private val accountState = MutableStateFlow(AccountState())
    val session = accountState.asStateFlow()
    private val ownProfileState = MutableStateFlow<OwnProfileState?>(null)
    val ownProfile = ownProfileState.asStateFlow()
    private val draftIndexState = MutableStateFlow<DraftIndexState?>(null)
    val draftIndex = draftIndexState.asStateFlow()
    private var syncedProfileRevision: Pair<String, Int>? = null
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
    private fun markWrite() { updates.value++ }
    fun ownsLocalLibrary(owner: String) = session.value.account?.id == owner

    suspend fun restore() = lock.withLock {
        if (restored) return@withLock
        snapshots.retain("category", emptySet())
        val saved = store.read()
        val cached = saved?.owner?.let { snapshots.read("owner/$it/account") }
            ?.let { runCatching { account(it) }.getOrNull() }?.takeIf { it.id == saved?.owner }
        accountState.value = AccountState(cached)
        if (cached != null && currentDraftIndex(cached.id) == null) {
            val index = cachedDrafts(cached.id)
            if (currentDraftIndex(cached.id) == null) publishDraftIndex(cached.id, RequestState(index))
        }
        try {
            val cookie = pendingCookie ?: saved?.cookie
            if (cookie != null) rotate(cookie, UUID.randomUUID())
            else accountState.value = AccountState(restoring = false)
            restored = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if ((error as? ApiFailure)?.status in setOf(401, 403)) {
                clear()
                restored = true
            } else accountState.value = AccountState(cached.takeIf { error.canUseSnapshot },
                restoring = false, error = error.userMessage())
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
            val cookie = credentials?.cookie
            if (cookie != null) {
                try {
                    transport.request("/auth/logout", "POST", cookie = cookie.header)
                } catch (error: ApiFailure) {
                    if (!error.canUseSnapshot && error.status !in setOf(401, 403)) throw error
                }
            }
            clear()
            restored = true
        }
    }

    private suspend fun startSession(path: String, payload: JSONObject) = lock.withLock {
        withContext(NonCancellable) {
            val previous = credentials?.cookie ?: pendingCookie ?: store.read()?.cookie
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
        val persisted = runCatching { store.write(cookie, basic.id) }.isSuccess
        if (!persisted) runCatching { store.write(null) }
        val existing = accountState.value.account?.takeIf { it.id == basic.id }
        val current = basic.copy(profile = existing?.profile ?: basic.profile)
        if (existing == null) {
            ownProfileState.value = null
            draftIndexState.value = null
        }
        saveAccountSnapshot(current)
        accountState.value = AccountState(current, restoring = false,
            error = if (!persisted) "无法保存登录状态，重新打开应用后需再登录" else null, authenticated = true)
    }

    private suspend fun clear() {
        credentials = null
        pendingCookie = null
        store.write(null)
        ownProfileState.value = null
        draftIndexState.value = null
        syncedProfileRevision = null
        accountState.value = AccountState(restoring = false)
    }

    private fun requireSame(current: Credentials?, original: Credentials): Credentials =
        current?.takeIf { it.generation == original.generation } ?: throw ApiFailure(401, "登录已变更，请重新操作")

    private suspend fun authenticated(path: String, method: String = "GET", body: ByteArray? = null,
        contentType: String = "application/json", owner: String? = null, optional: Boolean = false): ApiReply {
        val initial = credentials ?: if (owner != null && ownsLocalLibrary(owner)) reconnect(owner) else null
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

    private suspend fun reconnect(owner: String): Credentials? = lock.withLock {
        if (!ownsLocalLibrary(owner)) return@withLock null
        if (credentials == null) {
            val cookie = pendingCookie ?: return@withLock null
            try {
                rotate(cookie, UUID.randomUUID())
                restored = true
            } catch (error: ApiFailure) {
                if (error.status in setOf(401, 403)) clear()
                throw error
            }
        }
        credentials
    }

    private suspend fun saveAccountSnapshot(value: Account) {
        val profile = value.profile
        snapshots.write("owner/${value.id}/account", json("id" to value.id, "nickname" to profile.name,
            "username" to profile.handle, "signature" to profile.bio, "avatarUrl" to profile.avatarPhoto,
            "coverUrl" to profile.coverPhoto))
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

    suspend fun cachedArticle(id: String): Article? {
        if (!id.isRemoteId()) return null
        val owner = session.value.account?.id
        val data = owner?.let { snapshots.read("owner/$it/published/$id") } ?: snapshots.read("recent/$id", touch = true)
        return data?.let { article(it, detail = true) }
    }

    suspend fun articleDetail(id: String): Article {
        val owner = session.value.account?.id
        val data = try {
            fetchPublicArticle(id)
        } catch (error: ApiFailure) {
            if (id.isRemoteId() && error.status in setOf(403, 404)) {
                owner?.let { snapshots.remove("owner/$it/published/$id") }
                snapshots.remove("recent/$id")
                cards.remove(id)
            }
            throw error
        }
        val result = article(data, detail = true)
        if (owner != null && owner == result.author.id && ownsLocalLibrary(owner)) {
            snapshots.write("owner/$owner/published/$id", data)
        } else snapshots.write("recent/$id", data, limit = 40)
        return result
    }

    private suspend fun fetchPublicArticle(id: String): JSONObject {
        if (!id.isRemoteId()) throw ApiFailure(404, "未找到文章")
        val data = transport.request("/articles/${segment(id)}").obj()
        if (data.id() != id) throw ApiFailure("返回的文章不匹配")
        return data
    }

    suspend fun readingPosition(id: String, owner: String?): ReadingPosition? {
        if (!id.isRemoteId() || session.value.account?.id != owner) return null
        return snapshots.read("position/${owner ?: "guest"}/$id", touch = true)?.let {
            ReadingPosition(it.optInt("index").coerceAtLeast(0), it.optInt("offset").coerceAtLeast(0))
        }
    }

    suspend fun saveReadingPosition(id: String, owner: String?, position: ReadingPosition) {
        if (!id.isRemoteId() || session.value.account?.id != owner) return
        snapshots.write("position/${owner ?: "guest"}/$id", json("index" to position.index, "offset" to position.offset), limit = 100)
    }

    private fun draftItems(items: JSONArray?) = items.objects().mapNotNull { item ->
            val id = item.id() ?: return@mapNotNull null
            CloudDraftItem(id, item.text("title").orEmpty(), item.text("status").orEmpty(),
                item.text("updatedAt").orEmpty(), item.optInt("wordCount"), item.text("latestReason"))
    }

    suspend fun cachedDrafts(owner: String): List<CloudDraftItem>? {
        if (!ownsLocalLibrary(owner)) return null
        return snapshots.read("owner/$owner/draft-index")?.takeIf { ownsLocalLibrary(owner) }
            ?.let { draftItems(it.optJSONArray("items")) }
    }

    private fun currentDraftIndex(owner: String) = draftIndexState.value?.takeIf { it.owner == owner }?.request

    private fun publishDraftIndex(owner: String, request: RequestState<List<CloudDraftItem>>) {
        if (ownsLocalLibrary(owner)) draftIndexState.value = DraftIndexState(owner, request)
    }

    suspend fun drafts(owner: String): List<CloudDraftItem> = draftIndexLock.withLock {
        val current = currentDraftIndex(owner)?.value ?: cachedDrafts(owner)
        publishDraftIndex(owner, RequestState(current))
        try {
            val items = authenticated("/articles/drafts?includeRejected=true", owner = owner).array()
            val result = draftItems(items)
            snapshots.write("owner/$owner/draft-index", json("items" to items))
            if (result.size < 100) snapshots.retain("owner/$owner/drafts", result.map(CloudDraftItem::id).toSet())
            publishDraftIndex(owner, RequestState(result, loading = false))
            result
        } catch (error: CancellationException) {
            publishDraftIndex(owner, RequestState(current, loading = false))
            throw error
        } catch (error: Exception) {
            publishDraftIndex(owner, RequestState(current.takeIf { error.canUseSnapshot }, loading = false, error = error.userMessage()))
            throw error
        }
    }

    suspend fun cachedDraft(owner: String, id: String): CloudDraft? {
        if (!ownsLocalLibrary(owner) || !id.isRemoteId()) return null
        return snapshots.read("owner/$owner/drafts/$id")?.takeIf { ownsLocalLibrary(owner) }
            ?.let { runCatching { cloudDraft(it) }.getOrNull() }
    }

    suspend fun refreshDraftBox(owner: String, reconnect: Boolean = true) {
        if (!ownsLocalLibrary(owner)) return
        if (!reconnect && credentials?.owner != owner) {
            draftIndexLock.withLock {
                val current = currentDraftIndex(owner)?.value ?: cachedDrafts(owner)
                publishDraftIndex(owner, RequestState(current, loading = false, error = session.value.error ?: "联网后更新草稿箱"))
            }
            return
        }
        val items = try { drafts(owner) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { return }
        val covers = mutableSetOf<String>()
        for (item in items) {
            if (!ownsLocalLibrary(owner)) return
            val cached = cachedDraft(owner, item.id)
            val draft = if (cached != null && cached.matches(item)) cached
            else try { editDraft(owner, item.id) } catch (error: ApiFailure) {
                if (error.canUseSnapshot || error.status == 401) return
                continue
            }
            if (draft.content.coverUrl.isNotBlank()) covers += draft.content.coverUrl
        }
        for (cover in covers) if (ownsLocalLibrary(owner)) media.prefetch(cover)
    }

    suspend fun openDraft(owner: String, id: String): CloudDraft {
        val cached = cachedDraft(owner, id)
        val latest = currentDraftIndex(owner)?.value?.firstOrNull { it.id == id }
        if (cached != null && (latest == null || cached.matches(latest))) return cached
        return try { editDraft(owner, id) } catch (error: ApiFailure) {
            cached?.takeIf { error.canUseSnapshot && ownsLocalLibrary(owner) } ?: throw error
        }
    }

    private fun CloudDraft.matches(item: CloudDraftItem) = updatedAt == item.updatedAt && status == item.status

    suspend fun editDraft(owner: String, id: String): CloudDraft {
        if (!id.isRemoteId()) throw ApiFailure(404, "未找到草稿")
        val data = try { authenticated("/articles/${segment(id)}", owner = owner).obj() }
        catch (error: ApiFailure) {
            if (error.status in setOf(403, 404)) snapshots.remove("owner/$owner/drafts/$id")
            throw error
        }
        if (data.id("authorId") != owner) throw ApiFailure(403, "不能打开其他账号的草稿")
        if (data.id() != id) throw ApiFailure("返回的草稿不匹配")
        return persistDraft(owner, data)
    }

    suspend fun createDraft(owner: String): CloudDraft =
        persistDraft(owner, authenticated("/articles", "POST", owner = owner).obj(), updateIndex = true)

    suspend fun saveDraft(owner: String, draft: CloudDraft, content: DraftContent): CloudDraft {
        val version = draft.version ?: throw ApiFailure("服务器尚未提供草稿版本，当前内容仅保存在本机")
        if (!draft.editable) throw ApiFailure(409, "当前文章不能编辑")
        val body = json("title" to content.title, "content" to content.body, "summary" to content.summary,
            "coverUrl" to content.coverUrl, "version" to version)
        val result = persistDraft(owner, authenticated("/articles/${segment(draft.id)}/draft", "PUT", body.bytes(),
            owner = owner).obj(), updateIndex = true)
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
        val result = persistDraft(owner, authenticated("/articles/${segment(draft.id)}/submit?version=$version", "POST",
            owner = owner).obj(), updateIndex = true)
        markWrite()
        return result
    }

    suspend fun cancelReview(owner: String, id: String): CloudDraft {
        val result = persistDraft(owner, authenticated("/articles/${segment(id)}/cancel-review", "POST",
            owner = owner).obj(), updateIndex = true)
        markWrite()
        return result
    }

    suspend fun deleteDraft(owner: String, id: String) = draftIndexLock.withLock {
        authenticated("/articles/${segment(id)}", "DELETE", owner = owner)
        snapshots.remove("owner/$owner/drafts/$id")
        updateDraftIndex(owner, id, null)
        markWrite()
    }

    private suspend fun persistDraft(owner: String, data: JSONObject, updateIndex: Boolean = false): CloudDraft {
        val draft = cloudDraft(data)
        draftIndexLock.withLock {
            val existing = snapshots.read("owner/$owner/drafts/${draft.id}")
            val newer = existing != null && !existing.isNull("version") && draft.version != null &&
                existing.getLong("version") > draft.version
            if (!newer) {
                val indexed = snapshots.read("owner/$owner/draft-index")?.optJSONArray("items")
                val listed = indexed == null || indexed.objects().any { it.id() == draft.id }
                if (draft.status == "APPROVED") {
                    snapshots.remove("owner/$owner/drafts/${draft.id}")
                    updateDraftIndex(owner, draft.id, null)
                } else if (listed || updateIndex) {
                    snapshots.write("owner/$owner/drafts/${draft.id}", data)
                    if (updateIndex) updateDraftIndex(owner, draft.id, data)
                }
            }
        }
        return draft
    }

    private suspend fun updateDraftIndex(owner: String, id: String, draft: JSONObject?) {
        val updated = snapshots.update("owner/$owner/draft-index") { previous ->
            val items = previous?.optJSONArray("items").objects().filter { it.id() != id }
            val summary = draft?.let {
                json("id" to id, "title" to it.text("title"), "status" to it.text("status"),
                    "updatedAt" to it.text("updatedAt"), "wordCount" to it.optInt("wordCount"),
                    "latestReason" to (it.text("latestReviewReason") ?: it.text("rejectReason")))
            }
            if (previous == null && summary == null) null
            else json("items" to JSONArray((listOfNotNull(summary) + items).take(100)))
        }
        if (updated != null) publishDraftIndex(owner, RequestState(draftItems(updated.optJSONArray("items")),
            loading = false, error = currentDraftIndex(owner)?.error))
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
        if (result.id == owner && ownsLocalLibrary(owner)) {
            saveAccountSnapshot(result)
            if (ownsLocalLibrary(owner)) accountState.value = session.value.copy(account = result)
        }
        return result
    }

    suspend fun saveProfile(owner: String, initial: PreviewProfile, edited: PreviewProfile) {
        val uploaded = mutableMapOf<String, File>()
        suspend fun image(name: String?, previous: String?, type: String): String? {
            if (name == previous || name == null) return name
            val file = ProfileImages.uploadFile(context, name) ?: throw ApiFailure("本机图片已不可用，请重新选择")
            return uploadFile(owner, file, type).url.also { uploaded[it] = file }
        }
        val avatar = image(edited.avatarPhoto, initial.avatarPhoto, "AVATAR")
        val cover = image(edited.coverPhoto, initial.coverPhoto, "COVER")
        val result = account(authenticated("/users/me", "PUT", json("nickname" to edited.name,
            "signature" to edited.bio, "clearSignature" to edited.bio.isBlank(),
            "avatarUrl" to avatar, "coverUrl" to cover.orEmpty()).bytes(), owner = owner).obj())
        if (edited.bio.isBlank() && result.profile.bio.isNotBlank()) throw ApiFailure("服务器尚不支持清空简介")
        for (url in listOfNotNull(result.profile.avatarPhoto, result.profile.coverPhoto).distinct()) {
            uploaded[url]?.let { media.seedFromUpload(url, it) }
        }
        val position = if (result.profile.coverPhoto == null) .5f else edited.coverPosition
        profileSettings.edit().putFloat("position:$owner", position)
            .putString("cover:$owner", result.profile.coverPhoto).apply()
        val updated = result.copy(profile = result.profile.copy(coverPosition = position))
        saveAccountSnapshot(updated)
        if (ownsLocalLibrary(owner)) accountState.value = session.value.copy(account = updated)
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
        return publicProfile(profileData(id, page, owner))
    }

    suspend fun showLocalProfile(owner: String) {
        if (!ownsLocalLibrary(owner) || ownProfileState.value?.owner == owner) return
        val cached = readOwnProfile(owner)
        if (ownsLocalLibrary(owner) && ownProfileState.value?.owner != owner)
            ownProfileState.value = OwnProfileState(owner, RequestState(cached))
    }

    private suspend fun readOwnProfile(owner: String) =
        snapshots.read("owner/$owner/profile")?.let { runCatching { publicProfile(it) }.getOrNull() }

    suspend fun syncAtOpen(owner: String) = coroutineScope {
        val online = credentials?.owner == owner
        launch { refreshOwnProfile(owner, reconnect = online) }
        launch { refreshDraftBox(owner, reconnect = online) }
        if (online) {
            launch {
                val account = try { updateCurrentProfile(owner) } catch (_: ApiFailure) { return@launch }
                if (!ownsLocalLibrary(owner)) return@launch
                media.prefetch(account.profile.avatarPhoto)
                media.prefetch(account.profile.coverPhoto)
            }
        }
    }

    suspend fun refreshOwnProfile(owner: String, reconnect: Boolean = true, afterWrite: Boolean = false) = profileRefresh.withLock {
        if (!ownsLocalLibrary(owner)) return@withLock
        val revision = owner to writes.value
        if (afterWrite && (credentials?.owner != owner || syncedProfileRevision == revision)) return@withLock
        val key = "owner/$owner/profile"
        var current = ownProfileState.value?.takeIf { it.owner == owner }?.request?.value ?: readOwnProfile(owner)
        fun publish(loading: Boolean, error: String? = null) {
            if (ownsLocalLibrary(owner)) ownProfileState.value = OwnProfileState(owner, RequestState(current, loading, error))
        }
        if (!reconnect && credentials?.owner != owner) {
            publish(false, session.value.error ?: "联网后更新公开文章")
            return@withLock
        }
        publish(true)
        val items = try {
            val first = profileData(owner, 1, true)
            val firstPage = publicProfile(first)
            if (firstPage.account.id != owner || firstPage.articles.page != 1)
                throw ApiFailure("返回的个人主页不匹配")
            val cards = first.optJSONArray("list").objects().toMutableList()
            if (current == null) {
                current = firstPage
                snapshots.write(key, first)
                publish(true)
            }
            for (page in 2..firstPage.articles.pages) {
                val next = profileData(owner, page, true)
                if (next.optInt("page") != page) throw ApiFailure("返回的页码不正确")
                cards += next.optJSONArray("list").objects()
            }
            val unique = cards.distinctBy { it.id() }
            first.put("list", JSONArray(unique))
            val complete = publicProfile(first)
            if (!ownsLocalLibrary(owner)) return@withLock
            snapshots.write(key, first)
            snapshots.retain("owner/$owner/published", complete.articles.items.map(Article::id).toSet())
            current = complete
            syncedProfileRevision = revision
            publish(false)
            unique
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if ((error as? ApiFailure)?.status in setOf(403, 404)) {
                snapshots.remove(key)
                snapshots.retain("owner/$owner/published", emptySet())
                current = null
            }
            publish(false, error.userMessage())
            return@withLock
        }
        for (item in items) {
            if (!ownsLocalLibrary(owner)) return@withLock
            val id = item.id() ?: continue
            val path = "owner/$owner/published/$id"
            val cached = snapshots.read(path)
            if (cached == null || cached.text("updatedAt") != item.text("updatedAt")) {
                try { snapshots.write(path, fetchPublicArticle(id)) }
                catch (error: ApiFailure) {
                    if (error.status in setOf(403, 404)) snapshots.remove(path)
                    else if (error.canUseSnapshot) return@withLock
                }
            }
        }
        for (cover in items.mapNotNull { it.text("coverUrl") }.distinct())
            if (ownsLocalLibrary(owner)) media.prefetch(cover)
    }

    private suspend fun profileData(id: String, page: Int, owner: Boolean): JSONObject {
        val path = "/users/${segment(id)}/profile?tab=approved&page=$page&pageSize=20"
        return (if (owner) authenticated(path, owner = id) else transport.request(path)).obj()
    }

    private fun publicProfile(data: JSONObject): PublicProfile {
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

    private fun article(data: JSONObject): Article = article(data, detail = false)

    private fun article(data: JSONObject, detail: Boolean): Article {
        val id = data.id() ?: data.id("articleId") ?: throw ApiFailure("文章缺少有效 ID")
        val person = data.optJSONObject("author")?.let(::author) ?: Author(
            data.id("authorId").orEmpty(), data.text("authorName") ?: "作者", "", mediaUrl(data.text("authorAvatar")),
        ).also { if (it.id.isNotBlank()) authors.put(it.id, it) }
        val value = Article(id, data.text("title") ?: "未命名文章", data.text("summary") ?: data.text("previewText"),
            person, data.optInt("readMinutes", 1), (data.text("publishedAt") ?: data.text("updatedAt")).orEmpty().take(10),
            null, if (id.toLong() % 2 == 0L) CardTone.MINT else CardTone.APRICOT, emptyList(),
            mediaUrl(data.text("coverUrl")), data.text("coverColor"),
            data.text("content") ?: if (detail) "" else knownArticle(id)?.markdown)
        cards.put(id, value)
        return value
    }

    private fun <T> JSONObject.page(map: (JSONObject) -> T) =
        Page(optJSONArray("list").objects().map(map), optInt("page", 1), optInt("pages"), optInt("total"))
}

internal fun JSONObject.bytes() = toString().toByteArray(Charsets.UTF_8)
private fun String.isRemoteId() = toLongOrNull()?.let { it > 0 && it.toString() == this } == true
