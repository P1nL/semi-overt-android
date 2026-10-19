package com.semiovert.app.core.data

import com.semiovert.app.core.model.*

/** Original, fictional content for the offline preview. No API or personal data. */
object SampleLibrary {
    val authors = listOf(
        Author("lin", "林间来信", "记录散步、天气与生活中的小事。"),
        Author("zhou", "街角观察员", "从建筑与街道里，读懂一座城市。"),
        Author("ye", "纸页之间", "关于阅读，也关于愿意慢下来的时刻。"),
    )

    private fun sections(subject: String) = listOf(
        ArticleSection("opening", "留意那些细小的时刻", listOf(
            "这是一篇为 Semi-Overt 离线预览原创的示例文章，不来自线上账号，也不代表真实发布内容。你可以在这里体验阅读、目录与原位返回。",
            "关于“" + subject + "”，我们想从一个很小的片段说起。早晨的光落在窗台上，声音从街道那头慢慢传来。没有什么需要立刻完成，先把注意力还给眼前。",
            "有时候，一次短暂的停留，就能改变看待日常的方式。习以为常的风景里，藏着值得重新发现的细节。"
        )),
        ArticleSection("walking", "把脚步放慢一点", listOf(
            "走出门时，不必马上决定终点。沿着熟悉的方向多走一小段，看看树影怎样越过石阶，看看店门前新摆出的花。",
            "我们常常用效率衡量一天，却很少记得那些不赶路的时刻。给自己留一点空白，并不是浪费时间，而是让生活有机会被看见。",
            "在街角坐一会儿，看人们各自走向不同的地方。风吹过时，叶子的背面亮起来。此刻不需要结论，也不需要一张证明来过的照片。"
        )),
        ArticleSection("details", "一页纸能够留下什么", listOf(
            "回到桌前，写下今天遇见的一件小事。它可以只是一句话：一扇窗、一阵雨，或者陌生人递来的一次善意。",
            "不用把每一段经历都整理成道理。文字可以只是记录，记录当时的温度、颜色与心情。等到某一天重新翻开，就会认出那个曾经认真生活的自己。",
            "这份记录也不必分享给谁。能够被自己再次读到，就已经有了意义。"
        )),
        ArticleSection("ending", "回到自己的节奏", listOf(
            "读到这里，可以把手机放下一会儿，也可以打开目录，重新回到刚才喜欢的那一节。",
            "离开阅读页后，应用会回到你选择文章的位置，而不是把你送回列表开头。我们希望阅读是连贯的，探索也一样。",
            "愿你在下一次出门时，仍然对微小的事物保留好奇。"
        )),
    )

    val articles = listOf(
        Article("forest", "林间雾起的时候", null, authors[0], 2, "2026年10月16日", Cover.FOREST, CardTone.MINT, sections("林间雾起的时候")),
        Article("street", "把一天留给一条不熟悉的街道，走到阳光从建筑缝隙里落下的地方", "一段没有目的地的散步，也可以成为一天里最值得记住的部分。", authors[1], 5, "2026年10月15日", Cover.ARCHITECTURE, CardTone.APRICOT, sections("散步与城市")),
        Article("pages", "在纸页之间，找到自己的片刻安静", "一本书不必一次读完。停下来的地方，也是一种回答。", authors[2], 9, "2026年10月14日", null, CardTone.MINT, sections("阅读与安静")),
        Article("window", "雨停之后，我们把窗子打开", "空气里还有潮湿的味道，街道已经开始了新的一天。", authors[1], 4, "2026年10月13日", Cover.ARCHITECTURE, CardTone.APRICOT, sections("窗外的风景")),
        Article("tea", "一杯茶的时间", null, authors[0], 1, "2026年10月12日", null, CardTone.APRICOT, sections("一杯茶的时间")),
        Article("slow", "慢一点，才听得见", "从一条林间小路出发，重新认识安静。", authors[0], 12, "2026年10月11日", Cover.FOREST, CardTone.MINT, sections("自己的节奏")),
        Article("morning", "把清晨留给一段没有安排的路", "不用赶在谁的前面，看看城市刚刚醒来的样子。", authors[1], 3, "2026年10月10日", Cover.ARCHITECTURE, CardTone.APRICOT, sections("清晨的散步")),
        Article("bookmark", "书签停住的地方，故事还在继续", null, authors[2], 6, "2026年10月9日", null, CardTone.MINT, sections("书签与重读")),
        Article("shade", "午后的树影，替我们留了一会儿白", "坐在树下，让风把那些着急的念头慢慢吹散。", authors[0], 2, "2026年10月8日", Cover.FOREST, CardTone.MINT, sections("午后的树影")),
        Article("lamplight", "傍晚亮起的第一盏灯", "街道渐渐安静下来，每一扇窗里都有自己的日常。", authors[1], 7, "2026年10月7日", Cover.ARCHITECTURE, CardTone.APRICOT, sections("街灯与归途")),
        Article("letter", "写一封不急着寄出的信", "把今天记在纸上，留给以后某个愿意停下来的自己。", authors[2], 4, "2026年10月6日", null, CardTone.APRICOT, sections("一封写给自己的信")),
    )

    fun article(id: String): Article? = articles.find { it.id == id }
    fun inDuration(duration: ReadingDuration): List<Article> = articles.filter { duration.accepts(it.minutes) }
    fun searchArticles(query: String): List<Article> {
        val term = query.trim()
        if (term.isBlank()) return articles
        return articles.filter { article ->
            listOfNotNull(article.title, article.summary, article.author.name)
                .any { it.contains(term, ignoreCase = true) }
        }
    }
    fun searchAuthors(query: String): List<Author> {
        val term = query.trim()
        return authors.filter { term.isBlank() || it.name.contains(term, true) || it.bio.contains(term, true) }
    }
}
