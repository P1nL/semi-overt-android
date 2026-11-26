package com.semiovert.app.core.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

internal class OfflineSnapshots(context: Context, endpoint: String) {
    private val root = File(context.noBackupFilesDir, "offline-v1/${Uri.encode(endpoint)}")
    private val lock = Mutex()

    suspend fun read(key: String, touch: Boolean = false): JSONObject? = disk {
        val file = file(key)
        readFile(file)?.also { if (touch) file.baseFile.setLastModified(System.currentTimeMillis()) }
    }

    suspend fun write(key: String, value: JSONObject, limit: Int? = null) = disk {
        val file = file(key)
        writeFile(file, value)
        if (limit != null) entries(checkNotNull(file.baseFile.parentFile))
            .sortedByDescending(File::lastModified).drop(limit).forEach { AtomicFile(it).delete() }
    }

    suspend fun update(key: String, change: (JSONObject?) -> JSONObject?) = disk {
        val file = file(key)
        change(readFile(file))?.also { writeFile(file, it) }
    }

    suspend fun remove(key: String) = disk { file(key).delete() }

    suspend fun retain(group: String, keys: Set<String>) = disk {
        val folder = checkNotNull(file("$group/index").baseFile.parentFile)
        entries(folder).filter { it.nameWithoutExtension !in keys }.forEach { AtomicFile(it).delete() }
    }

    private suspend fun <T> disk(block: () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }

    private fun file(key: String): AtomicFile {
        require(key.split('/').all { it.matches(component) })
        return AtomicFile(File(root, "$key.json"))
    }

    private fun entries(folder: File) = folder.listFiles { item -> item.isFile && item.extension == "json" }.orEmpty()

    private fun readFile(file: AtomicFile): JSONObject? = try {
        JSONObject(file.readFully().toString(Charsets.UTF_8))
    } catch (_: IOException) {
        null
    } catch (_: JSONException) {
        null
    }

    private fun writeFile(file: AtomicFile, value: JSONObject) {
        try {
            val stream = file.startWrite()
            try {
                stream.write(value.bytes())
                file.finishWrite(stream)
            } catch (error: IOException) {
                file.failWrite(stream)
            }
        } catch (_: IOException) {
            return
        }
    }

    private companion object {
        val component = Regex("[a-zA-Z0-9_-]+")
    }
}
