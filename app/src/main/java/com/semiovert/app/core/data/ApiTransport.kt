package com.semiovert.app.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

internal const val PRODUCTION_API = "https://api.semi-overt.com/api/v1"

internal class ApiFailure(val status: Int, message: String, val retryAfter: Long? = null,
    val disconnected: Boolean = false) : Exception(message) {
    constructor(message: String) : this(0, message)
}

internal val Throwable.canUseSnapshot get() = this is ApiFailure && (disconnected || status in 500..599 || status == 429)

internal fun Throwable.userMessage() = (this as? ApiFailure)?.message ?: "操作未完成，请重试"
internal fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
internal fun JSONObject.id(key: String = "id"): String? = text(key)?.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }
internal fun JSONArray?.objects() = if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
internal fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply {
    fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}
internal fun segment(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

internal data class ApiReply(val data: Any?, val cookies: List<String>) {
    fun obj(): JSONObject = data as? JSONObject ?: throw ApiFailure("服务器返回的数据格式不正确")
    fun array(): JSONArray = data as? JSONArray ?: throw ApiFailure("服务器返回的数据格式不正确")
}

internal class ApiTransport(baseUrl: String) {
    private val base = URI(baseUrl.trimEnd('/'))

    init {
        require(base.host != null && base.userInfo == null && base.fragment == null && base.query == null)
        require(base.scheme == "https" || (base.scheme == "http" && base.host in setOf("127.0.0.1", "10.0.2.2")))
    }

    fun mediaUrl(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val uri = base.resolve(value)
            uri.takeIf {
                it.host != null && it.userInfo == null &&
                    (it.scheme == "https" || (it.scheme == base.scheme && it.authority == base.authority))
            }?.toASCIIString()
        }.getOrNull()
    }

    suspend fun request(
        path: String,
        method: String = "GET",
        body: ByteArray? = null,
        contentType: String = "application/json",
        accessToken: String? = null,
        cookie: String? = null,
    ): ApiReply = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.startsWith("//"))
        val connection = URI("$base$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Semi-Overt-Android/1")
            if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer $accessToken")
            if (cookie != null) connection.setRequestProperty("Cookie", cookie)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val envelope = runCatching { JSONTokener(text).nextValue() as? JSONObject }.getOrNull()
            val code = envelope?.optInt("code", status) ?: status
            if (status !in 200..299 || code !in 200..299) {
                val effectiveStatus = if (status !in 200..299) status else code
                val message = envelope?.text("message")?.take(200) ?: when (effectiveStatus) {
                    401 -> "登录已过期，请重新登录"
                    403 -> "请求未获允许"
                    404 -> "找不到这项内容"
                    429 -> "操作过于频繁，请稍后再试"
                    else -> "服务器暂时无法完成请求"
                }
                throw ApiFailure(effectiveStatus, message, connection.getHeaderField("Retry-After")?.toLongOrNull())
            }
            if (envelope == null || !envelope.has("data")) throw ApiFailure("服务器返回的数据格式不正确")
            val cookies = connection.headerFields.entries
                .filter { it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }
            ApiReply(envelope.opt("data").takeUnless { it === JSONObject.NULL }, cookies)
        } catch (_: java.net.SocketTimeoutException) {
            throw ApiFailure(0, "连接超时，请稍后重试", disconnected = true)
        } catch (_: IOException) {
            throw ApiFailure(0, "连接失败，请检查网络", disconnected = true)
        } finally {
            connection.disconnect()
        }
    }
}
