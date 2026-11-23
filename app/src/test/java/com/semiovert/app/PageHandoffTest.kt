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
        assertEquals(1150,entering.totalMillis)
        val leaving=PageHandoff.between("author/{authorId}","home",profileGuest=true)!!
        assertEquals(SceneMotion.PROFILE_EXIT_MS,leaving.exitMillis)
        assertEquals(1250,leaving.totalMillis)
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
        assertEquals(900,leaving.enterMillis)
        assertEquals(450,leaving.enterDelayMillis)
        assertEquals(1350,leaving.totalMillis)
        assertEquals(400,PageHandoff(Destination.PROFILE,Destination.SEARCH,true).identityMillis)
    }

    @Test fun readerNavigationSlidesOutInFourHundredMillisecondsAndOverlapsEachPrimaryPage() {
        for(page in Destination.entries) {
            val handoff=PageHandoff.between("reader/{articleId}",page.route)!!
            assertNull(handoff.from)
            assertEquals(page,handoff.to)
            assertEquals(400,handoff.exitMillis)
            assertEquals(if(page==Destination.PROFILE)0 else 200,handoff.enterDelayMillis)
            assertEquals(if(page==Destination.PROFILE)400 else 200,handoff.overlapMillis)
            assertEquals(if(page==Destination.PROFILE)700 else 1100,handoff.totalMillis)
        }
        val guest=PageHandoff.between("reader/{articleId}",Destination.PROFILE.route,profileGuest=true)!!
        assertEquals(400,guest.totalMillis)
        assertEquals(400,guest.overlapMillis)
    }
    @Test fun homeExitKeepsItsDurationAndOverlapsCategoryEntrance() {
        val handoff=PageHandoff(Destination.HOME,Destination.CATEGORY)
        assertEquals(SceneMotion.HOME_EXIT_MS,handoff.exitMillis)
        assertEquals(SceneMotion.CATEGORY_ENTER_MS,handoff.enterMillis)
        assertEquals(450,handoff.enterDelayMillis)
        assertEquals(450,handoff.overlapMillis)
        assertEquals(1350,handoff.totalMillis)
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
        assertEquals(700,signedIn.enterMillis)
        assertEquals(600,signedIn.identityMillis)
        assertEquals(250,signedIn.enterDelayMillis)
        assertEquals(950,signedIn.totalMillis)
        val fromSearch=PageHandoff(Destination.SEARCH,Destination.PROFILE)
        assertEquals(250,fromSearch.enterDelayMillis)
        assertEquals(600,fromSearch.identityMillis)
        val guest=PageHandoff(Destination.HOME,Destination.PROFILE,profileGuest=true)
        assertEquals(400,guest.enterMillis)
        assertEquals(0,guest.enterDelayMillis)
        assertEquals(400,guest.overlapMillis)
        assertEquals(400,guest.identityMillis)
        assertEquals(900,guest.totalMillis)
        assertEquals(900,PageHandoff(Destination.CATEGORY,Destination.PROFILE,true).totalMillis)
        val guestExit=PageHandoff(Destination.PROFILE,Destination.HOME,true)
        assertEquals(400,guestExit.exitMillis)
        assertEquals(400,guestExit.identityMillis)
        assertEquals(1100,guestExit.totalMillis)
        val signedInExit=PageHandoff(Destination.PROFILE,Destination.HOME)
        assertEquals(700,signedInExit.exitMillis)
        assertEquals(700,signedInExit.identityMillis)
        assertEquals(1250,signedInExit.totalMillis)
    }
    @Test fun readerSecondaryAndSameRouteDoNotReplaySceneEntrances() {
        assertNull(PageHandoff.between("home","reader/{articleId}"))
        assertNull(PageHandoff.between("settings","profile"))
        assertNull(PageHandoff.between("profile","profile"))
    }
}
