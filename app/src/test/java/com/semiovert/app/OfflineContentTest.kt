package com.semiovert.app

import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.model.ReadingDuration
import org.junit.Assert.*
import org.junit.Test

class OfflineContentTest {
    @Test fun durationBoundariesDoNotOverlap() {
        for(minutes in listOf(1,2,3,8,9,12)) assertEquals(1,ReadingDuration.entries.count {it.accepts(minutes)})
        assertTrue(ReadingDuration.QUICK.accepts(2))
        assertTrue(ReadingDuration.MEDIUM.accepts(3))
        assertTrue(ReadingDuration.MEDIUM.accepts(8))
        assertTrue(ReadingDuration.LONG.accepts(9))
    }
    @Test fun durationCyclesToStart() {
        assertEquals(ReadingDuration.QUICK,ReadingDuration.QUICK.next().next().next())
    }
    @Test fun articlesAreUniqueAndHaveReadableSections() {
        val articles=SampleLibrary.articles
        assertEquals(11,articles.size)
        assertEquals(articles.size,articles.map {it.id}.distinct().size)
        assertTrue(articles.all {it.sections.isNotEmpty() && it.sections.all {section -> section.paragraphs.isNotEmpty()}})
        assertTrue(articles.any {it.cover==null})
        assertTrue(articles.any {it.summary==null})
    }
    @Test fun articleSearchTrimsWhitespaceAndFindsAuthor() {
        assertEquals(listOf("forest"),SampleLibrary.searchArticles("  林间雾起  ").map {it.id})
        assertEquals(4,SampleLibrary.searchArticles("林间来信").size)
        assertTrue(SampleLibrary.searchArticles("不存在的关键词").isEmpty())
    }
    @Test fun authorSearchDoesNotReturnArticleTitles() {
        assertEquals(listOf("zhou"),SampleLibrary.searchAuthors("街角").map {it.id})
        assertTrue(SampleLibrary.searchAuthors("林间雾起的时候").isEmpty())
    }
    @Test fun filteringDoesNotDuplicateOrInventArticles() {
        val filtered=ReadingDuration.entries.flatMap {SampleLibrary.inDuration(it)}
        assertEquals(SampleLibrary.articles.map {it.id}.toSet(),filtered.map {it.id}.toSet())
        assertEquals(SampleLibrary.articles.size,filtered.size)
    }
}
