package com.semiovert.app.core.designsystem

/** The stack gap exposes exactly 70% of the first title line after its top padding. */
object ArticleCardMetrics {
    const val TITLE_FONT_SP=23f
    const val TITLE_LINE_SP=29f
    const val STACK_TITLE_TOP_DP=34f
    const val STACK_TITLE_REVEAL=.7f
    fun stackGap(fontScale: Float=1f)=STACK_TITLE_TOP_DP+TITLE_LINE_SP*fontScale*STACK_TITLE_REVEAL
}
