package com.semiovert.app.feature.editor

import org.json.JSONObject
import com.semiovert.app.core.data.CloudDraftItem
import com.semiovert.app.core.data.id
import com.semiovert.app.core.data.text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class EditorDraft(
    val title: String,
    val preview: String,
    val wordCount: Int,
    val updatedAt: String,
    val key: String = "",
    val remoteId: String? = null,
    val status: String? = null,
    val localChanges: Boolean = false,
    val reason: String? = null,
) {
    val displayTitle get() = title.ifBlank { "未命名草稿" }
    val editable get() = status == null || status == "DRAFT" || status == "RETURNED"
    val deletable get() = status != "PENDING"
    val statusLabel get() = when (status) {
        "PENDING" -> "待审核"
        "RETURNED" -> "退回修改"
        "REJECTED" -> "未通过"
        "APPROVED" -> "已发布"
        else -> when {
            localChanges -> "待同步"
            remoteId != null -> "云端草稿"
            else -> null
        }
    }
    val modifiedLabel: String
        get() = runCatching {
            Instant.parse(updatedAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        }.getOrDefault("已自动保存")

    companion object {
        fun fromJson(value: JSONObject, key: String = ""): EditorDraft? {
            val title = value.optString("title").trim()
            val body = value.optString("body").trim()
            val summary = value.optString("summary").trim()
            if (title.isBlank() && body.isBlank() && summary.isBlank() && value.optString("coverUrl").isBlank()) return null
            val text = body.replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
                .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("[#*_>`~|]"), "")
                .replace(Regex("\\s+"), " ").trim()
            return EditorDraft(
                title = title,
                preview = summary.ifBlank { text }.take(140),
                wordCount = text.count { !it.isWhitespace() },
                updatedAt = value.optString("updatedAt"),
                key = key,
                remoteId = value.id("remoteId"),
                status = value.text("status"),
                localChanges = value.optBoolean("dirty"),
                reason = value.text("reason"),
            )
        }

        fun fromCloud(item: CloudDraftItem, key: String) = EditorDraft(item.title, "", item.words,
            item.updatedAt, key, item.id, item.status, reason = item.reason)
    }
}
