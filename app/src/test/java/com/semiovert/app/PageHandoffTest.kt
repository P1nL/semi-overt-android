package com.semiovert.app

import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.PageHandoff
import org.junit.Assert.*
import org.junit.Test

class PageHandoffTest {
    @Test fun authorUsesSignedInProfileMotionEvenWhenTheViewerIsAGuest() {
        val entering=PageHandoff.between("search","author/{authorId}",profileGuest=true)!!
        assertEquals(Destination.PROFILE,entering.to)
        assertEquals(SceneMotion.PROFILE_ENTER_MS,entering.enterMillis)
        val leaving=PageHandoff.between("author/{authorId}","home",profileGuest=true)!!
        assertEquals(SceneMotion.PROFILE_EXIT_MS,leaving.exitMillis)
        assertNotNull(PageHandoff.between("author/{authorId}","profile",profileGuest=true))
    }
    @Test fun searchEntranceExitAndSharedAvatarUseTheSameFasterClock() {
        val entering=PageHandoff(Destination.HOME,Destination.SEARCH)
        assertEquals(900,entering.enterMillis)
        assertEquals(750,entering.identityMillis)
        assertEquals(450,entering.enterDelayMillis)
        assertEquals(1350,entering.totalMillis)
        val leaving=PageHandoff(Destination.SEARCH,Destination.CATEGORY)
        assertEquals(900,leaving.exitMillis)
        assertEquals(750,leaving.identityMillis)
        assertEquals(1180,leaving.enterMillis)
        assertEquals(450,leaving.enterDelayMillis)
        assertEquals(1630,leaving.totalMillis)
        assertEquals(450,PageHandoff(Destination.PROFILE,Destination.SEARCH,true).identityMillis)
    }

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

    @Test fun guestAndSignedInProfilesHaveSeparateCompleteClocks() {
        val signedIn=PageHandoff(Destination.HOME,Destination.PROFILE)
        assertEquals(1550,signedIn.totalMillis)
        val guest=PageHandoff(Destination.HOME,Destination.PROFILE,profileGuest=true)
        assertEquals(450,guest.enterMillis)
        assertEquals(900,guest.totalMillis)
        assertEquals(1180,PageHandoff(Destination.CATEGORY,Destination.PROFILE,true).totalMillis)
        assertEquals(450,PageHandoff(Destination.PROFILE,Destination.HOME,true).exitMillis)
        assertEquals(1680,PageHandoff(Destination.PROFILE,Destination.HOME).totalMillis)
    }
    @Test fun readerSecondaryAndSameRouteDoNotReplaySceneEntrances() {
        assertNull(PageHandoff.between("home","reader/{articleId}"))
        assertNull(PageHandoff.between("settings","profile"))
        assertNull(PageHandoff.between("profile","profile"))
    }
}
