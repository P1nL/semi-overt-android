package com.semiovert.app.core.data

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class RequestState<T>(val value: T? = null, val loading: Boolean = true, val error: String? = null)

@Composable
internal fun rememberOwnProfile(business: LiveBusiness, owner: String): RequestState<PublicProfile>? {
    val state = business.ownProfile.collectAsStateWithLifecycle().value?.takeIf { it.owner == owner }?.request
    val revision = business.writes.collectAsStateWithLifecycle().value
    LaunchedEffect(business, owner, revision) {
        if (revision > 0) business.refreshOwnProfile(owner, afterWrite = true)
    }
    return state
}

@Composable
internal fun <T> rememberRequest(vararg keys: Any?, readLocal: (suspend () -> T?)? = null,
    load: suspend () -> T): Pair<RequestState<T>, () -> Unit> {
    var attempt by remember(*keys) { mutableIntStateOf(0) }
    val status = remember(*keys, attempt) { mutableStateOf(RequestState<T>()) }
    LaunchedEffect(status) {
        val cached = readLocal?.let { loadSnapshot(it) }
        if (cached != null) status.value = RequestState(cached)
        try {
            status.value = RequestState(load(), loading = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            status.value = RequestState(cached.takeIf { error.canUseSnapshot }, loading = false, error = error.userMessage())
        }
    }
    return status.value to { attempt++ }
}

internal data class Page<T>(val items: List<T>, val page: Int, val pages: Int, val total: Int)

internal suspend fun <T> loadSnapshot(read: suspend () -> T?): T? = try {
    read()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

internal class PagedRequest<T>(private val load: suspend (Int) -> Page<T>, private val merge: (List<T>) -> List<T>) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var total by mutableIntStateOf(0)
        private set
    private var page = 0
    private var pages by mutableIntStateOf(1)
    val hasNext get() = page < pages

    suspend fun next() {
        if (page >= pages || (page != 0 && loading)) return
        loading = true
        error = null
        try {
            val result = load(page + 1)
            if (result.page != page + 1) throw ApiFailure("返回的页码不正确")
            items = merge(if (page == 0) result.items else items + result.items)
            total = result.total
            page = result.page
            pages = result.pages
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            this.error = error.userMessage()
        } finally {
            loading = false
        }
    }
}

@Composable
internal fun <T> rememberPaged(vararg keys: Any?, id: (T) -> String, load: suspend (Int) -> Page<T>): Pair<PagedRequest<T>, () -> Unit> {
    val request = remember(*keys) { PagedRequest(load) { items -> items.distinctBy(id) } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(request) { request.next() }
    return request to { if (!request.loading) scope.launch { request.next() }; Unit }
}
