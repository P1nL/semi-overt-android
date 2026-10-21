package com.semiovert.app

import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.PageHandoff
import org.junit.Assert.*
import org.junit.Test

class PageHandoffTest {
    @Test fun readerNavigationSlidesOutInFourHundredMillisecondsAndOverlapsEachPrimaryPage() {
        for(page in Destination.entries) {
            val handoff=PageHandoff.between("reader/{articleId}",page.route)!!
            assertNull(handoff.from)
            assertEquals(page,handoff.to)
            assertEquals(400,handoff.exitMillis)
            assertEquals(200,handoff.enterDelayMillis)
            assertEquals(200,handoff.overlapMillis)
            assertTrue(handoff.totalMillis>handoff.exitMillis)
        }
    }
    @Test fun homeExitKeepsItsDurationAndOverlapsCategoryEntrance() {
        val handoff=PageHandoff(Destination.HOME,Destination.CATEGORY)
        assertEquals(SceneMotion.HOME_EXIT_MS,handoff.exitMillis)
        assertEquals(SceneMotion.CATEGORY_ENTER_MS,handoff.enterMillis)
        assertEquals(450,handoff.enterDelayMillis)
        assertEquals(450,handoff.overlapMillis)
        assertEquals(1630,handoff.totalMillis)
    }

    @Test fun everyPrimaryPairHasAnOverlapWithoutShorteningEitherScene() {
        for(from in Destination.entries)for(to in Destination.entries)if(from!=to) {
            val handoff=PageHandoff.between(from.route,to.route)!!
            assertTrue(handoff.enterDelayMillis>=0)
            assertTrue(handoff.enterDelayMillis<handoff.exitMillis)
            assertTrue(handoff.totalMillis>=handoff.exitMillis)
            assertEquals(handoff.exitMillis+handoff.enterMillis-handoff.overlapMillis,handoff.totalMillis)
        }
    }

    @Test fun readerSecondaryAndSameRouteDoNotReplaySceneEntrances() {
        assertNull(PageHandoff.between("home","reader/{articleId}"))
        assertNull(PageHandoff.between("settings","profile"))
        assertNull(PageHandoff.between("profile","profile"))
    }
}
