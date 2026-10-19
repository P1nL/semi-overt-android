package com.semiovert.app.core.data

import com.semiovert.app.core.model.DemoRecord
import java.time.LocalDate

/** Original fixture history, not a record of real saves or daily additions. */
object DemoRecords {
    val entries=SampleLibrary.articles.mapIndexed {index,article -> DemoRecord(article.id,LocalDate.of(2026,10,16-index/2),360+index*45,
        "让内容被更多人看到。","让内容被更多人看到，也让每一次表达被认真对待。")}
    fun forAuthor(authorId: String)=entries.filter {record -> SampleLibrary.article(record.articleId)?.author?.id==authorId}
}
