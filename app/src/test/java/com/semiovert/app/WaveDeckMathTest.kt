package com.semiovert.app

import com.semiovert.app.core.designsystem.wave.*
import com.semiovert.app.core.designsystem.ArticleCardMetrics
import org.junit.Assert.*
import org.junit.Test

class WaveDeckMathTest {
    @Test fun leftStaggerUsesThePageLimitRatherThanTheLoadedArticleCount() {
        for(limit in listOf(WaveDeckMath.CATEGORY_VISIBLE,WaveDeckMath.SEARCH_VISIBLE,WaveDeckMath.PROFILE_VISIBLE)) {
            val g=WaveDeckMath.embeddedGeometry(411f,750f,11,900f,visibleLimit=limit)
            val full=WaveDeckMath.frames(11,0f,g,limit)
            val short=WaveDeckMath.frames(3,0f,g,limit)
            assertEquals(g.width/2f,full.first().centerX,.001f)
            for(i in short.indices)assertEquals(full[i].centerX,short[i].centerX,.001f)
            for(i in 1 until full.size) {
                val step=full[i-1].centerX-full[i].centerX
                assertTrue("Rear cards expose a slim left edge",step>0f&&step<=3.001f)
            }
        }
    }

    @Test fun staggerStaysInsideTheMarginAndContinuousAcrossQueueChanges() {
        for(width in listOf(320f,411f))for(count in listOf(1,4,11))for(limit in listOf(5,6,7)) {
            val g=WaveDeckMath.embeddedGeometry(width,750f,count,900f,visibleLimit=limit)
            for(position in listOf(-.05f,0f,.5f,.95f,1f)) {
                val poses=WaveDeckMath.frames(count,position,g,limit)
                assertTrue("Keep at least 8dp of left inset",poses.all {it.centerX-g.cardSize/2f>=7.999f})
                assertTrue("Outgoing cards retain their centered path",poses.filter {it.outgoing}.all {it.centerX==width/2f})
            }
            val before=WaveDeckMath.frames(count,.9999f,g,limit)
            val after=WaveDeckMath.frames(count,1f,g,limit)
            for(pose in after) {
                val previous=before.single {it.article==pose.article}
                assertEquals("No horizontal jump at the queue seam",previous.centerX,pose.centerX,.01f)
            }
        }
    }

    @Test fun categoryAndSearchCounterKeepTheSameScreenHeight() {
        for(statusTop in listOf(24f,32f)) {
            val category=DeckCounterLayout.localTop(statusTop,false)
            val searchViewport=statusTop+DeckCounterLayout.SEARCH_HEADER_TOP+DeckCounterLayout.SEARCH_SCOPE_HEIGHT
            assertEquals(category,searchViewport+DeckCounterLayout.localTop(statusTop,true),.001f)
        }
    }
    @Test fun windowOutsideArticleReplacesDepartingFrontInsteadOfRecyclingIt() {
        val count=11;val limit=WaveDeckMath.SEARCH_VISIBLE
        assertEquals(6,limit)
        val g=WaveDeckMath.embeddedGeometry(411f,750f,count,900f,visibleLimit=limit)
        val leaving=WaveDeckMath.frames(count,.5f,g,limit)
        assertTrue(leaving.any {it.article==0&&it.outgoing})
        val arriving=WaveDeckMath.frames(count,.95f,g,limit)
        assertFalse(arriving.any {it.article==0})
        assertTrue(arriving.any {it.article==6&&it.alpha>0f})
        assertEquals((1..6).toSet(),WaveDeckMath.frames(count,1f,g,limit).map {it.article}.toSet())
        assertEquals((0..5).toSet(),WaveDeckMath.frames(count,0f,g,limit).map {it.article}.toSet())
    }
    @Test fun smallDeckStillRecyclesTheSameArticleAtItsRear() {
        val g=WaveDeckMath.embeddedGeometry(411f,750f,4,900f,visibleLimit=6)
        val recycling=WaveDeckMath.frames(4,.95f,g,6).single {it.article==0}
        assertTrue(recycling.returning)
        val next=WaveDeckMath.frames(4,1f,g,6)
        assertEquals(1,next.maxBy {it.layer}.article)
        assertEquals(0,next.minBy {it.layer}.article)
    }
    @Test fun categoryAndSearchShowSeventyPercentOfTheSameFullHeightCardAtTheScreenBottom() {
        val reference=WaveDeckMath.geometry(411f,900f,3)
        for((viewport,limit) in listOf(900f to WaveDeckMath.CATEGORY_VISIBLE,750f to WaveDeckMath.SEARCH_VISIBLE)) {
            val top=WaveDeckMath.restingFrontTop(411f,viewport,900f)
            val deck=WaveDeckMath.embeddedGeometry(411f,viewport,11,900f,top,limit)
            assertEquals(reference.frontHeight,deck.frontHeight,.001f)
            assertEquals(deck.frontHeight*.7f,viewport-deck.frontTop,.001f)
            assertTrue(deck.height>viewport)
            assertEquals(limit,WaveDeckMath.frames(11,0f,deck,limit).size)
        }
    }
    @Test fun personalScrollEndsAtSeventyPercentOfTheFrontCardAndExcludesElasticReserve() {
        for(count in listOf(1,4,11)) {
            val height=WaveDeckMath.profileViewportHeight(411f,900f,count)
            val deck=WaveDeckMath.embeddedGeometry(411f,height,count,900f,76f,WaveDeckMath.PROFILE_VISIBLE)
            assertEquals(deck.frontHeight*.7f,height-deck.frontTop,.001f)
            assertTrue(height<deck.height)
        }
    }
    @Test fun embeddedDecksKeepCategoryCardDimensionsInsteadOfUsingTheirClippedHeight() {
        val category=WaveDeckMath.geometry(411f,900f,4)
        val profile=WaveDeckMath.embeddedGeometry(411f,300f,4,900f,76f)
        val search=WaveDeckMath.embeddedGeometry(411f,750f,4,900f)
        for(deck in listOf(profile,search)) {
            assertEquals(category.cardSize,deck.cardSize,.001f)
            assertEquals(category.frontHeight,deck.frontHeight,.001f)
        }
        assertTrue("Personal cards extend below the fixed crop",profile.height>300f)
        assertEquals("Search preserves the category's screen-space top",category.frontTop,search.frontTop+150f,.001f)
    }
    @Test fun allStacksExposeSeventyPercentOfTheFirstTitleLine() {
        fun top(pose: WaveCardPose)=pose.centerY-pose.height/2f
        for(fontScale in listOf(1f,1.2f))for((count,height,frontTop) in listOf(Triple(3,900f,null),Triple(4,300f,76f),Triple(11,750f,null))) {
            val expected=34f+29f*fontScale*.7f
            assertEquals(expected,ArticleCardMetrics.stackGap(fontScale),.001f)
            val geometry=WaveDeckMath.embeddedGeometry(411f,height,count,900f,frontTop,fontScale=fontScale)
            val poses=WaveDeckMath.frames(count,0f,geometry)
            for(index in 1 until count)assertEquals("Top gaps must not shrink with result count or viewport",expected,top(poses[index-1])-top(poses[index]),.001f)
            assertTrue("The backmost visible top is not hidden by a counter strip",top(poses.last())>=WaveDeckMath.TOP_INSET-.001f)
        }
    }
    @Test fun sceneWindowsLimitRenderingWithoutDiscardingArticles() {
        assertEquals(6,WaveDeckMath.SEARCH_VISIBLE)
        assertEquals(7,WaveDeckMath.CATEGORY_VISIBLE)
        assertEquals(5,WaveDeckMath.PROFILE_VISIBLE)
        for(limit in listOf(WaveDeckMath.SEARCH_VISIBLE,WaveDeckMath.CATEGORY_VISIBLE,WaveDeckMath.PROFILE_VISIBLE)) {
            val g=WaveDeckMath.embeddedGeometry(411f,750f,11,900f,visibleLimit=limit)
            for(page in -11..11) {
                val rest=WaveDeckMath.frames(11,page.toFloat(),g,limit)
                assertEquals(limit,rest.size)
                assertEquals(WaveDeckMath.index(page,11),rest.maxBy {it.layer}.article)
                assertTrue(rest.all {it.centerY-it.height/2f>=WaveDeckMath.TOP_INSET-.001f})
                for(fraction in listOf(.02f,.5f,.85f,.87f,.99f)) {
                    val moving=WaveDeckMath.frames(11,page+fraction,g,limit)
                    assertTrue(moving.size<=limit)
                    assertEquals(moving.size,moving.map {it.article}.distinct().size)
                }
            }
        }
        assertEquals(4,WaveDeckMath.frames(4,0f,WaveDeckMath.geometry(411f,900f,4),WaveDeckMath.PROFILE_VISIBLE).size)
    }
    @Test fun cappedRearReplacementArrivesWithoutFlashingOrAnExtraCard() {
        val g=WaveDeckMath.embeddedGeometry(411f,750f,11,900f,visibleLimit=4)
        val before=WaveDeckMath.frames(11,.85f,g,4)
        assertTrue(before.any {it.article==0&&it.outgoing})
        assertFalse(before.any {it.article==4})
        val arriving=WaveDeckMath.frames(11,.8601f,g,4).single {it.article==4}
        assertTrue(arriving.alpha<.001f)
        assertEquals(1f,WaveDeckMath.frames(11,1f,g,4).single {it.article==4}.alpha,0f)
    }
    @Test fun longUpwardPullIsResistedAndTheExtendedBottomStillCoversTheViewport() {
        assertEquals(0f,WaveDeckMath.elasticPull(-200f,800f),0f)
        val pull=WaveDeckMath.elasticPull(-800f,800f)
        assertTrue(pull<0f&&pull> -WaveDeckMath.MAX_ELASTIC_PULL)
        assertEquals(0f,WaveDeckMath.elasticPull(800f,800f),0f)
        val g=WaveDeckMath.embeddedGeometry(411f,730f,11,900f,76f,5)
        for(pose in WaveDeckMath.frames(11,0f,g,5)) {
            val bottom=pose.centerY+pose.height/2f+WaveDeckMath.BOTTOM_OVERFLOW-WaveDeckMath.MAX_ELASTIC_PULL
            assertTrue("No blank bottom even at maximum elastic pull",bottom>730f)
        }
    }
    @Test fun restingStackIsBackHighFrontLowAndTheFrontCardIsComplete() {
        for(count in listOf(1,2,3,6,11)) {
            val g=WaveDeckMath.geometry(411f,780f,count)
            val poses=WaveDeckMath.frames(count,0f,g)
            val front=poses.first()
            assertEquals(g.frontY,front.centerY,.001f)
            assertEquals("Widening must preserve the original top edge",299.49f,front.centerY-front.height/2f,.001f)
            assertEquals("Cards use ninety percent of this viewport width",369.9f,g.cardSize,.001f)
            assertTrue(front.centerY-front.height/2f>=0f)
            for(pose in poses)assertEquals("Every resting card extends to the bottom",g.height,pose.centerY+pose.height*pose.scale/2f,.001f)
            for(i in 1 until count) {
                assertTrue(poses[i].centerY<poses[i-1].centerY)
                assertTrue(poses[i].layer<poses[i-1].layer)
                assertEquals("Rear cards keep the same width and type size",1f,poses[i].scale,0f)
            }
        }
    }

    @Test fun everyFollowerRespondsImmediatelyAndDepthFormsAContinuousWave() {
        val g=WaveDeckMath.geometry(411f,780f,6)
        val start=WaveDeckMath.frames(6,0f,g)
        val beginning=WaveDeckMath.frames(6,.02f,g)
        assertEquals((g.outsideY-g.frontY)*.02f/WaveDeckMath.EXIT_FRACTION,beginning[0].centerY-start[0].centerY,.001f)
        for(i in 1..5)assertTrue("No rear card waits for a travel threshold",beginning[i].centerY>start[i].centerY+.1f)
        val wave=WaveDeckMath.frames(6,.25f,g)
        for(i in 1..4)assertTrue("Different depth responses form a wave, not a rigid translation",
            wave[i].centerY-start[i].centerY>wave[i+1].centerY-start[i+1].centerY)
        val reverse=WaveDeckMath.frames(6,-.02f,g)
        for(i in 0..4)assertTrue("Reversing the gesture responds immediately too",reverse[i].centerY<start[i].centerY)
        assertTrue(reverse.last().centerY>start.last().centerY)
        val crest=WaveDeckMath.frames(6,.5f,g)
        assertTrue("The first neighbour has a visible web-style crest",crest[1].waveOffset>40f)
        assertEquals("Web neighbour amplitudes use 100 / 40 falloff",2.5f,crest[1].waveOffset/crest[2].waveOffset,.001f)
        assertEquals(2.5f,crest[2].waveOffset/crest[3].waveOffset,.001f)
        for(pose in start)assertEquals("No idle wave or replay at rest",0f,pose.waveOffset,.001f)
    }

    @Test fun recyclingChangesDepthOnlyAfterTheWholeForegroundCardLeavesTheBottom() {
        val g=WaveDeckMath.geometry(411f,780f,6)
        val before=WaveDeckMath.frames(6,WaveDeckMath.EXIT_FRACTION-.002f,g)[0]
        assertTrue(before.outgoing)
        assertTrue("The complete card must be outside before recycling",before.centerY-before.height*before.scale/2f>g.height)
        val after=WaveDeckMath.frames(6,WaveDeckMath.EXIT_FRACTION+.0001f,g)[0]
        assertTrue(after.returning)
        assertTrue("The hidden transfer must not flash a new card",after.alpha<.001f)
        assertTrue(after.layer<before.layer)
    }

    @Test fun reverseTravelRaisesTheFrontWhileTheBackFallsAndTheSamePathIsReversible() {
        val g=WaveDeckMath.geometry(411f,780f,6)
        val rest=WaveDeckMath.frames(6,0f,g)
        val up=WaveDeckMath.frames(6,-.08f,g)
        assertTrue(up[0].centerY<rest[0].centerY)
        assertTrue(up.last().centerY>rest.last().centerY)
        assertTrue(up.last().returning)
    }

    @Test fun everyArticleKeepsItsIdentityAcrossBothLoopSeamsAndRebasing() {
        for(count in listOf(1,2,3,6,11)) {
            val g=WaveDeckMath.geometry(411f,780f,count)
            for(step in -200..200) {
                val poses=WaveDeckMath.frames(count,step*.071f,g)
                assertEquals(count,poses.map {it.article}.distinct().size)
                assertTrue(poses.all {it.centerY.isFinite()&&it.scale==1f&&it.alpha in 0f..1f})
            }
            for(page in -count*3..count*3) {
                assertEquals(WaveDeckMath.frames(count,page.toFloat(),g),WaveDeckMath.frames(count,WaveDeckMath.index(page,count).toFloat(),g))
            }
        }
        assertTrue(WaveDeckMath.frames(0,0f,WaveDeckMath.geometry(411f,780f,0)).isEmpty())
    }

    @Test fun gestureDirectionAndNearestCardSelectionFollowTheQueueWithoutSkippingOnNoise() {
        val step=WaveDeckMath.geometry(411f,780f,6).scrollStep
        assertEquals(1,WaveDeckMath.target(1f,300f,step))
        assertEquals(-1,WaveDeckMath.target(-1f,-300f,step))
        assertEquals(0,WaveDeckMath.target(.08f,0f,step))
        assertEquals(0,WaveDeckMath.focus(.54f,0))
        assertEquals(1,WaveDeckMath.focus(.58f,0))
        assertEquals(-1,WaveDeckMath.nearestPage(2,0f,3))
        assertEquals(4,WaveDeckMath.nearestPage(1,3f,3))
    }

    @Test fun downwardGainShortensTheGestureByTwentyPercentWithoutChangingUpwardTravel() {
        val g=WaveDeckMath.geometry(411f,780f,6)
        assertEquals(1f,WaveDeckMath.dragDelta(g.scrollStep*.8f,g.scrollStep),.0001f)
        assertEquals(-.8f,WaveDeckMath.dragDelta(-g.scrollStep*.8f,g.scrollStep),.0001f)
        assertEquals(0f,WaveDeckMath.dragDelta(0f,g.scrollStep),0f)
    }
}
