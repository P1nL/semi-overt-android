package com.semiovert.app.core.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal data class LocalRevision(val id: String, val title: String, val date: LocalDate, val before: String, val after: String) {
    val added get() = after.codePointCount(0, after.length)
    val removed get() = before.codePointCount(0, before.length)
}

internal class WritingHistory(context: Context) {
    private val prefs = context.getSharedPreferences("writing_history", Context.MODE_PRIVATE)
    private val folder = context.noBackupFilesDir
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()

    fun enabled(owner: String) = prefs.getBoolean(owner, false)

    fun enable(owner: String, enabled: Boolean) {
        prefs.edit().putBoolean(owner, enabled).apply()
        revision.value++
    }

    suspend fun list(owner: String): List<LocalRevision> = mutex.withLock {
        withContext(Dispatchers.IO) { read(file(owner)) }
    }

    suspend fun clear(owner: String) = mutex.withLock {
        withContext(Dispatchers.IO) { file(owner).delete() }
        revision.value++
    }

    suspend fun record(owner: String, result: CloudDraft, previous: String) {
        if (!enabled(owner) || previous == result.content.body) return
        val date = runCatching { Instant.parse(result.updatedAt).atOffset(ZoneOffset.UTC).toLocalDate() }
            .getOrElse { LocalDate.now(ZoneOffset.UTC) }
        val (before, after) = changedExcerpt(previous, result.content.body)
        val snapshot = LocalRevision("${result.id}:${result.version}", result.content.title, date, before, after)
        mutex.withLock {
            if (!enabled(owner)) return
            try {
                withContext(Dispatchers.IO) {
                    val file = file(owner)
                    val queue = (read(file) + snapshot).distinctBy(LocalRevision::id).takeLast(100)
                    val value = JSONArray(queue.map { json("id" to it.id, "title" to it.title, "date" to it.date.toString(),
                        "before" to it.before, "after" to it.after) })
                    val stream = file.startWrite()
                    try {
                        stream.write(value.toString().toByteArray(Charsets.UTF_8))
                        file.finishWrite(stream)
                    } catch (error: IOException) {
                        file.failWrite(stream)
                        throw error
                    }
                }
                revision.value++
            } catch (_: IOException) {
                return
            }
        }
    }

    private fun file(owner: String): AtomicFile {
        require(owner.toLongOrNull()?.let { it > 0 } == true)
        return AtomicFile(File(folder, "writing-history-$owner.json"))
    }

    private fun read(file: AtomicFile): List<LocalRevision> = try {
        JSONArray(file.readFully().toString(Charsets.UTF_8)).objects().mapNotNull {
            runCatching { LocalRevision(it.getString("id"), it.getString("title"), LocalDate.parse(it.getString("date")),
                it.getString("before"), it.getString("after")) }.getOrNull()
        }
    } catch (_: java.io.FileNotFoundException) {
        emptyList()
    } catch (_: org.json.JSONException) {
        emptyList()
    }
}

internal fun changedExcerpt(before: String, after: String): Pair<String, String> {
    var start = 0
    while (start < minOf(before.length, after.length) && before.codePointAt(start) == after.codePointAt(start)) {
        start += Character.charCount(before.codePointAt(start))
    }
    var left = before.length
    var right = after.length
    while (left > start && right > start && before.codePointBefore(left) == after.codePointBefore(right)) {
        val count = Character.charCount(before.codePointBefore(left))
        left -= count
        right -= count
    }
    return before.substring(start, left) to after.substring(start, right)
}
