package com.semiovert.app.feature.search

/** Leading-edge submission throttle: ignored requests never queue another animation. */
internal class SearchSubmissionGate {
    companion object {
        const val MIN_SUBMISSION_INTERVAL_MS=1200L
        const val SCOPE_INTERVAL_MS=350L
    }
    private var nextSubmissionAt=Long.MIN_VALUE
    private var nextScopeAt=Long.MIN_VALUE
    var busy=false
        private set

    fun begin(draft: String,committed: String,now: Long,explicitClear: Boolean=false): String? {
        val query=draft.trim()
        val reset=explicitClear&&query.isEmpty()
        if(busy||(!reset&&now<nextSubmissionAt)||now<nextScopeAt||query.equals(committed,ignoreCase=true))return null
        busy=true
        nextSubmissionAt=now+MIN_SUBMISSION_INTERVAL_MS
        return query
    }

    fun finish() {busy=false}

    fun switchScope(now: Long): Boolean {
        if(busy||now<nextScopeAt)return false
        nextScopeAt=now+SCOPE_INTERVAL_MS
        return true
    }
}
