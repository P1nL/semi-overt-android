package com.semiovert.app.core.model

enum class ReadingDuration(val label: String) {
    QUICK("<3 min"), MEDIUM("3–8 min"), LONG(">8 min");
    fun accepts(minutes: Int): Boolean = when (this) {
        QUICK -> minutes < 3
        MEDIUM -> minutes in 3..8
        LONG -> minutes > 8
    }
    fun next(): ReadingDuration = entries[(ordinal + 1) % entries.size]
}

enum class Cover { FOREST, ARCHITECTURE }
enum class CardTone { MINT, APRICOT }
enum class SearchScope(val label: String) { ARTICLES("文章"), AUTHORS("作者") }
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色");
    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

data class Author(val id: String, val name: String, val bio: String)
data class ArticleSection(val id: String, val title: String, val paragraphs: List<String>)
data class Article(
    val id: String,
    val title: String,
    val summary: String?,
    val author: Author,
    val minutes: Int,
    val date: String,
    val cover: Cover?,
    val tone: CardTone,
    val sections: List<ArticleSection>,
)
