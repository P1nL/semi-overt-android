package com.semiovert.app.core.data

import org.json.JSONObject

internal const val DRAFT_CHANGED = "文章已被其他操作修改，请刷新后重试"
internal val ApiFailure.isDraftConflict get() = status == 404 || (status == 409 && message == DRAFT_CHANGED)

internal data class DraftContent(val title: String, val body: String, val summary: String, val coverUrl: String) {
    val empty get() = title.isBlank() && body.isBlank() && summary.isBlank() && coverUrl.isBlank()
    fun editorJson() = json("title" to title, "body" to body, "summary" to summary, "coverUrl" to coverUrl)

    companion object {
        fun fromEditor(value: JSONObject) = DraftContent(value.optString("title"), value.optString("body"),
            value.optString("summary"), value.optString("coverUrl"))
    }
}

internal data class CloudDraft(val id: String, val version: Long?, val status: String, val content: DraftContent,
    val updatedAt: String, val reason: String? = null) {
    val editable get() = status == "DRAFT" || status == "RETURNED"

    fun editorJson() = content.editorJson().apply {
        put("remoteId", id)
        put("remoteVersion", version?.toString() ?: JSONObject.NULL)
        put("status", status)
        put("reason", reason ?: JSONObject.NULL)
        put("updatedAt", updatedAt)
    }
}

internal data class CloudDraftItem(val id: String, val title: String, val status: String, val updatedAt: String,
    val words: Int, val reason: String?)
