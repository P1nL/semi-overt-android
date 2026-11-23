package com.semiovert.app

import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.arc.*
import androidx.compose.animation.core.*
import org.junit.Assert.*
import org.junit.Test

class DeckSceneMotionTest {
    @Test fun browseLabelAndCounterEnterAndExitInSixHundredMilliseconds() {
        assertEquals(600,SceneMotion.BROWSE_ANNOTATION_MS)
        assertEquals(1f,SceneMotion.browseAnnotationAlpha(0f,FlowPhase.IDLE,0),0f)
        for((enterMillis,exitMillis) in listOf(SceneMotion.CATEGORY_ENTER_MS to SceneMotion.CATEGORY_EXIT_MS,
            SceneMotion.SEARCH_ENTER_MS to SceneMotion.SEARCH_EXIT_MS)) {
            for(phase in listOf(FlowPhase.ENTER,FlowPhase.EXIT)) {
                val duration=if(phase==FlowPhase.ENTER)enterMillis else exitMillis
                fun alpha(elapsed: Int): Float {
                    val progress=elapsed.toFloat()/duration
                    return SceneMotion.browseAnnotationAlpha(if(phase==FlowPhase.ENTER)1f-progress else progress,phase,duration)
                }
                assertEquals(if(phase==FlowPhase.ENTER)0f else 1f,alpha(0),.001f)
                assertEquals(.5f,alpha(300),.001f)
                assertEquals(if(phase==FlowPhase.ENTER)1f else 0f,alpha(600),.001f)
                assertEquals(if(phase==FlowPhase.ENTER)1f else 0f,alpha(duration),.001f)
            }
        }
    }

    @Test fun categorySwapOnlyExtendsItsEntrance() {
        assertEquals(550,SceneMotion.CATEGORY_SWAP_MS)
        assertEquals(750,SceneMotion.CATEGORY_SWAP_ENTER_MS)
        assertEquals(84,SceneMotion.CATEGORY_SWAP_STAGGER_MS)
    }

    @Test fun categoryRouteAndLabelSwitchCarryMomentumPastRestBeforeSettling() {
        val travel=780f+24f-220f
        val windows=listOf(Triple(SceneMotion.CATEGORY_ENTER_MS,SceneMotion.DECK_STAGGER_MS,SceneMotion.categoryRouteEntranceEase),
            Triple(SceneMotion.CATEGORY_SWAP_ENTER_MS,SceneMotion.CATEGORY_SWAP_STAGGER_MS,SceneMotion.categorySwapEntranceEase))
        for((duration,stagger,entranceEase) in windows) {
            val window=duration-stagger
            val baseWindow=window-200
            fun offset(progress: Float,phase: FlowPhase=FlowPhase.ENTER): Float {
                val elapsed=progress*window/duration
                return SceneMotion.waveOffset(if(phase==FlowPhase.ENTER)1f-elapsed else elapsed,
                    phase,0,1,220f,780f,duration,stagger,entranceEase=entranceEase)
            }
            assertEquals(travel,offset(0f),.001f)
            assertEquals(travel*(1f-SoSceneEase.transform(.30f)),offset(.30f*baseWindow/window),.01f)
            assertEquals(0f,offset(1f),.001f)
            val samples=(0..100).map {offset(it/100f)}
            val topTurn=samples.indices.minBy {samples[it]}
            assertEquals("All extra time follows the primary lift",.64f*baseWindow,topTurn*window/100f,window/100f)
            assertTrue("The primary overshoot stays near 6% of travel",-samples[topTurn]/travel in .05f.. .07f)
            val bottomTurn=(topTurn+1..samples.lastIndex).maxBy {samples[it]}
            assertTrue("The return carries past rest with a smaller overshoot",samples[bottomTurn]/travel in .008f.. .018f)
            assertTrue("Descent and final settling each have room to slow down",
                bottomTurn-topTurn>=19&&samples.lastIndex-bottomTurn>=15)
            val fastestReturnStep=samples.subList(topTurn,bottomTurn+1).zipWithNext().maxOf {(previous,next)->next-previous}
            assertTrue("The return stays gradual at its fastest point",fastestReturnStep<travel*.0048f)
            val crossing=(topTurn+1..bottomTurn).first {samples[it]>0f}
            assertTrue("The card is still moving through the resting position",
                samples[crossing]-samples[crossing-1]>travel*.0025f)
            assertTrue("The final glide settles without another crossing",
                samples.drop(bottomTurn).zipWithNext().all {(previous,next)->next in 0f..previous})
            assertTrue("The final steps decelerate",samples[99]-samples[100]<samples[94]-samples[95])
            for(frame in 0..20) {
                val progress=frame/20f
                assertEquals(travel*SoSceneEase.transform(progress),offset(progress,FlowPhase.EXIT),.01f)
            }
        }
    }

    @Test fun readingOpenAndReturnLeaveTimeForVisibleStages() {
        val opening=TargetBasedAnimation(SceneMotion.readingOpenSpec(),Float.VectorConverter,0f,1f)
        assertEquals(1_200_000_000L,opening.durationNanos)
        assertEquals(.5f,opening.getValueFromNanos(600_000_000L),.001f)
        assertEquals(1f,opening.getValueFromNanos(opening.durationNanos),0f)
        assertEquals(1f,SceneMotion.readingOpeningCardAlpha(.55f,ArticleOrigin.STACK),0f)
        assertEquals(0f,SceneMotion.readingOpeningCardAlpha(.80f,ArticleOrigin.STACK),0f)
        assertEquals(1f,SceneMotion.readingOpeningText(.80f,ArticleOrigin.STACK),0f)
        val closing=TargetBasedAnimation(SceneMotion.readingCloseSpec(),Float.VectorConverter,1f,0f)
        assertEquals(1_200_000_000L,closing.durationNanos)
        assertTrue(SceneMotion.readingBody(closing.getValueFromNanos(200_000_000L),returning=true)>0f)
        assertTrue(closing.getValueFromNanos(611_000_000L) in .61f.. .63f)
        assertEquals(0f,closing.getValueFromNanos(closing.durationNanos),0f)
    }
    @Test fun readingReturnKeepsTheLeadAndLandingCurveOnTheLongerClock() {
        val original=TargetBasedAnimation(tween<Float>(1300,easing=SoSceneEase),Float.VectorConverter,1f,0f)
        val closing=TargetBasedAnimation(SceneMotion.readingCloseSpec(),Float.VectorConverter,1f,0f)
        fun progressAt(referenceMillis: Int)=closing.getValueFromNanos(referenceMillis*closing.durationNanos/1100L)
        for(time in 0..560 step 11) {
            assertEquals("The longer clock keeps the relative title and container timing",
                original.getValueFromNanos(time*1_000_000L),progressAt(time),.0001f)
        }
        assertTrue(progressAt(900)<original.getValueFromNanos(900_000_000L))
        var previous=1f
        for(time in 0..1100 step 10) {
            val progress=progressAt(time)
            assertTrue("A faster landing must remain monotone and never overshoot",progress in 0f..previous)
            previous=progress
        }
        assertEquals(0f,previous,0f)
    }
    @Test fun readingBodyGets480MsForBothClickAndReleasedDrag() {
        for(start in listOf(0f,SceneMotion.readingDrag(1f))) {
            val opening=TargetBasedAnimation(SceneMotion.readingOpenSpec(start),Float.VectorConverter,start,1f)
            fun bodyAt(millis: Int): Float {
                val progress=opening.getValueFromNanos(millis*1_000_000L)
                return SceneMotion.readingBody(progress)
            }
            val duration=(opening.durationNanos/1_000_000L).toInt()
            val bodyStart=(0..duration).first {bodyAt(it)>0f}
            assertTrue("The body receives the same visible clock after either trigger",duration-bodyStart in 478..480)
            assertEquals(0f,bodyAt(0),0f)
            assertEquals(1f,bodyAt(duration),0f)
            val peakStep=(1..duration).maxOf {bodyAt(it)-bodyAt(it-1)}
            assertTrue("A screen-height entrance must no longer be compressed into the old final 176ms",peakStep<.0055f)
        }
        assertEquals(0f,SceneMotion.readingBody(SceneMotion.READING_BODY_START),0f)
    }
    @Test fun categoryTapStartsTheCopyAlongsideTheLiftAndKeepsTheFullBodyWindow() {
        val opening=TargetBasedAnimation(SceneMotion.readingOpenSpec(openWithLift=true),Float.VectorConverter,0f,1f)
        assertEquals(540_000_000L,opening.durationNanos)
        val contentStart=SceneMotion.readingContentProgress(0f,true)
        assertEquals(0f,SceneMotion.readingOpeningText(contentStart,ArticleOrigin.STACK),0f)
        assertEquals(1f,SceneMotion.readingOpeningCardAlpha(contentStart,ArticleOrigin.STACK),0f)
        val firstMotion=opening.getValueFromNanos(16_000_000L)
        assertTrue(SceneMotion.readingOpeningLift(firstMotion,ArticleOrigin.STACK)>0f)
        assertTrue(SceneMotion.readingOpeningText(SceneMotion.readingContentProgress(firstMotion,true),ArticleOrigin.STACK)>0f)
        fun bodyAt(millis: Int)=SceneMotion.readingBody(
            SceneMotion.readingContentProgress(opening.getValueFromNanos(millis*1_000_000L),true))
        val bodyStart=(0..540).first {bodyAt(it)>0f}
        assertTrue(540-bodyStart in 478..480)
        assertEquals(1f,bodyAt(540),0f)
    }
    @Test fun readingBodyKeepsItsExtendedWindowOnTheLongerClosingClock() {
        val closing=TargetBasedAnimation(SceneMotion.readingCloseSpec(),Float.VectorConverter,1f,0f)
        fun bodyAt(millis: Int,previous: Boolean=false): Float {
            val progress=closing.getValueFromNanos(millis*closing.durationNanos/1100L)
            return if(previous)SoEase.transform(SceneMotion.segment(progress,.86f,1f))
                else SceneMotion.readingBody(progress,returning=true)
        }
        val previousEnd=(0..1100).first {bodyAt(it,previous=true)==0f}
        val newEnd=(0..1100).first {bodyAt(it)==0f}
        assertTrue(previousEnd in 320..350)
        assertTrue("The body leaves later, before the accelerated card landing",newEnd>previousEnd&&newEnd<560)
        assertTrue("The body is still moving after the previous exit finished",bodyAt(previousEnd)>0f)
        fun peakStep(previous: Boolean)=(1..1100).maxOf {bodyAt(it-1,previous)-bodyAt(it,previous)}
        assertTrue("Peak retreat speed must fall",peakStep(false)<peakStep(true))
        assertEquals(1_200_000_000L,closing.durationNanos)
        assertEquals(1f,bodyAt(0),0f)
        assertEquals(0f,bodyAt(1100),0f)
    }
    @Test fun returningChromeFadesInEarlierWhileTheCardLands() {
        val closing=TargetBasedAnimation(SceneMotion.readingCloseSpec(),Float.VectorConverter,1f,0f)
        fun alphaAt(millis: Int)=1f-SceneMotion.readingChrome(
            closing.getValueFromNanos(millis*closing.durationNanos/1100L),returning=true)
        assertEquals(0f,alphaAt(500),0f)
        assertTrue("Headers should already be visible during landing",alphaAt(700)>.2f)
        assertTrue(alphaAt(850)>.8f)
        assertEquals(1f,alphaAt(1100),0f)
        val oldProgress=TargetBasedAnimation(tween<Float>(1300,easing=SoSceneEase),Float.VectorConverter,1f,0f)
            .getValueFromNanos(700_000_000L)
        assertEquals("The previous fade still hid the header at this time",0f,1f-SceneMotion.readingChrome(oldProgress),0f)
        var previous=0f
        for(time in 0..1100 step 10) {
            val alpha=alphaAt(time)
            assertTrue(alpha in previous..1f)
            previous=alpha
        }
    }
    @Test fun homeCopyMovesUniformlyFor300MsAndItsCoverFadesImmediately() {
        val opening=TargetBasedAnimation(SceneMotion.readingOpenSpec(origin=ArticleOrigin.HOME),Float.VectorConverter,0f,1f)
        fun progressAt(millis: Int)=opening.getValueFromNanos(millis*1_000_000L)
        fun textAt(millis: Int)=SceneMotion.readingOpeningText(progressAt(millis),ArticleOrigin.HOME)
        for(time in 0..240 step 60) {
            assertEquals("Equal elapsed time must move the copy by equal distance",
                .2f,textAt(time+60)-textAt(time),.001f)
            assertEquals(0f,SceneMotion.readingOpeningLift(progressAt(time),ArticleOrigin.HOME),0f)
        }
        assertEquals(1f,SceneMotion.readingOpeningCardAlpha(0f,ArticleOrigin.HOME),0f)
        assertTrue(SceneMotion.readingOpeningCardAlpha(progressAt(16),ArticleOrigin.HOME)<1f)
        assertEquals(0f,SceneMotion.readingOpeningCardAlpha(progressAt(420),ArticleOrigin.HOME),.001f)
        assertEquals(1f,textAt(300),0f)
        assertEquals(1f,textAt(630),0f)
        assertEquals(0f,SceneMotion.readingOpeningAvatar(progressAt(300),ArticleOrigin.HOME),.0001f)
        assertEquals(1f,SceneMotion.readingOpeningAvatar(progressAt(480),ArticleOrigin.HOME),.0001f)
        assertEquals(630_000_000L,opening.durationNanos)
    }
    @Test fun homeBodyStarts150MsAfterTheTitleAndKeepsIts480MsEntrance() {
        val opening=TargetBasedAnimation(SceneMotion.readingOpenSpec(origin=ArticleOrigin.HOME),Float.VectorConverter,0f,1f)
        fun bodyAt(millis: Int)=SceneMotion.readingBody(opening.getValueFromNanos(millis*1_000_000L),origin=ArticleOrigin.HOME)
        assertEquals(0f,bodyAt(150),.0001f)
        assertTrue(bodyAt(151)>0f)
        assertEquals(.5f,bodyAt(390),.001f)
        assertTrue("Body and title move together before the title lands at 300ms",bodyAt(250)>0f&&bodyAt(250)<1f)
        assertEquals(1f,bodyAt(630),0f)
        assertEquals(1f,SceneMotion.readingOpeningAvatar(1f,ArticleOrigin.HOME),0f)
    }
    @Test fun homeReturnExitsTheBodyFor580MsWithItsOriginalRelativeStagger() {
        val closing=TargetBasedAnimation(SceneMotion.readingCloseSpec(ArticleOrigin.HOME),Float.VectorConverter,1f,0f)
        fun progressAt(millis: Int)=closing.getValueFromNanos(millis*1_000_000L)
        fun textAt(millis: Int)=SceneMotion.readingOpeningText(progressAt(millis),ArticleOrigin.HOME,returning=true)
        fun bodyAt(millis: Int)=SceneMotion.readingBody(progressAt(millis),returning=true,origin=ArticleOrigin.HOME)
        assertEquals(580_000_000L,closing.durationNanos)
        assertEquals(1f,textAt(181),.0001f)
        assertTrue(textAt(182)<1f)
        for(time in 203..435 step 58) {
            assertEquals("The returning title must cover equal distances in equal time",
                -.16f,textAt(time+58)-textAt(time),.001f)
        }
        assertEquals(1f,bodyAt(0),0f)
        assertTrue(bodyAt(16)<1f)
        assertEquals(.5f,bodyAt(290),.001f)
        assertTrue("The body keeps its full exit after the title has returned",bodyAt(544)>0f)
        assertEquals(0f,bodyAt(580),.0001f)
        assertEquals(0f,textAt(544),0f)
        assertEquals(0f,textAt(580),0f)
    }
    @Test fun homeReturnRestoresTheCoverAlongsideItsTitleAndFadesTheAvatarFirst() {
        val closing=TargetBasedAnimation(SceneMotion.readingCloseSpec(ArticleOrigin.HOME),Float.VectorConverter,1f,0f)
        fun progressAt(millis: Int)=closing.getValueFromNanos(millis*1_000_000L)
        fun coverAt(millis: Int)=SceneMotion.readingOpeningCardAlpha(progressAt(millis),ArticleOrigin.HOME,returning=true)
        fun avatarAt(millis: Int)=SceneMotion.readingOpeningAvatar(progressAt(millis),ArticleOrigin.HOME,returning=true)
        assertEquals(0f,coverAt(181),.0001f)
        assertTrue(coverAt(381) in .01f.. .99f)
        assertEquals(1f,coverAt(580),.0001f)
        assertEquals(1f,avatarAt(0),0f)
        assertEquals(0f,avatarAt(218),.0001f)
        for(time in 0..580 step 16) {
            assertEquals(0f,SceneMotion.readingOpeningLift(progressAt(time),ArticleOrigin.HOME),0f)
        }
    }
    @Test fun clickDragAndFlingKeepTheSameClockAfterTheLiftBoundary() {
        val speed=SceneMotion.readingReleaseVelocity(12_000f)
        val click=TargetBasedAnimation(SceneMotion.readingOpenSpec(),Float.VectorConverter,0f,1f)
        for(start in listOf(0f,SceneMotion.readingDrag(.1f),SceneMotion.readingDrag(20f))) {
            val opening=TargetBasedAnimation(SceneMotion.readingOpenSpec(start),Float.VectorConverter,start,1f)
            val flung=TargetBasedAnimation(SceneMotion.readingOpenSpec(start),Float.VectorConverter,start,1f,AnimationVector1D(speed))
            val offset=(start*SceneMotion.READING_OPEN_MS).toLong()*1_000_000L
            for(sample in 0..20) {
                val time=sample*opening.durationNanos/20
                assertEquals(opening.getValueFromNanos(time),flung.getValueFromNanos(time),0f)
                assertEquals(click.getValueFromNanos(time+offset),opening.getValueFromNanos(time),.001f)
            }
        }
    }
    @Test fun stackCopyAndCoverStayTogetherUntilTheClampedLiftBoundary() {
        val held=SceneMotion.readingDrag(10f)
        assertEquals(SceneMotion.READING_DRAG_STAGE,held,0f)
        assertEquals(1f,SceneMotion.readingOpeningLift(held,ArticleOrigin.STACK),0f)
        assertEquals(0f,SceneMotion.readingOpeningText(held,ArticleOrigin.STACK),0f)
        assertEquals(1f,SceneMotion.readingOpeningCardAlpha(held,ArticleOrigin.STACK),0f)
        assertEquals(0f,SceneMotion.readingOpeningAvatar(held,ArticleOrigin.STACK),0f)
        assertEquals(0f,SceneMotion.readingBody(held),0f)
        assertTrue(SceneMotion.readingChrome(SceneMotion.readingDrag(.2f))>0f)
        assertEquals(1f,SceneMotion.readingChrome(SceneMotion.readingDrag(1f)),0f)
        assertEquals(0f,SceneMotion.readingOpeningCardAlpha(.80f,ArticleOrigin.STACK),0f)
        assertTrue(SceneMotion.readingOpeningAvatar(.85f,ArticleOrigin.STACK)>0f)
        assertTrue(SceneMotion.readingBody(.96f)>0f)
        assertEquals(1f,SceneMotion.readingBody(1f),0f)
        var previous=0f
        var previousStep=Float.POSITIVE_INFINITY
        for(frame in 1..8) {
            val p=held+(SceneMotion.STACK_READING_TEXT_END-held)*frame/8f
            val text=SceneMotion.readingOpeningText(p,ArticleOrigin.STACK)
            val step=text-previous
            assertTrue("Copy keeps rising and decelerates after handoff",step>0f&&step<previousStep)
            assertEquals(1f,SceneMotion.readingOpeningLift(p,ArticleOrigin.STACK),0f)
            previous=text;previousStep=step
        }
    }
    @Test fun closingReturnsTheCopyBeforeTheFixedSizeStackCardLands() {
        for(origin in ArticleOrigin.entries) {
            var previousText=1f;var previousAlpha=0f
            for(frame in 100 downTo 0) {
                val p=frame/100f
                val text=SceneMotion.readingOpeningText(p,origin,returning=true)
                val alpha=SceneMotion.readingOpeningCardAlpha(p,origin,returning=true)
                assertTrue("Closing copy follows the same path back to its source",text in 0f..previousText)
                assertTrue("The cover fades in while the copy returns",alpha in previousAlpha..1f)
                assertTrue(SceneMotion.readingOpeningLift(p,origin) in 0f..1f)
                if(origin==ArticleOrigin.STACK&&p>=SceneMotion.READING_DRAG_STAGE)
                    assertEquals("The card stays at the lift boundary during copy return",
                        1f,SceneMotion.readingOpeningLift(p,origin),0f)
                previousText=text;previousAlpha=alpha
            }
            assertEquals(0f,previousText,0f)
            assertEquals(1f,previousAlpha,0f)
        }
        assertEquals(0f,SceneMotion.readingOpeningText(SceneMotion.READING_DRAG_STAGE,ArticleOrigin.STACK),0f)
        assertEquals(1f,SceneMotion.readingOpeningCardAlpha(SceneMotion.READING_DRAG_STAGE,ArticleOrigin.STACK),0f)
    }
    @Test fun readingSpringCarriesReleaseVelocityAndFollowersReboundInBothDirections() {
        val speed=SceneMotion.readingReleaseVelocity(640f)
        val release=TargetBasedAnimation(SceneMotion.readingSpring(),Float.VectorConverter,.25f,1f,AnimationVector1D(speed))
        assertEquals(speed,release.getVelocityVectorFromNanos(0L).value,.001f)
        assertTrue(SceneMotion.readingReleaseVelocity(-640f)<0f)
        val down=TargetBasedAnimation(SceneMotion.readingFollowerSpring(0),Float.VectorConverter,0f,100f)
        val up=TargetBasedAnimation(SceneMotion.readingFollowerSpring(0),Float.VectorConverter,100f,0f,AnimationVector1D(300f))
        val samples=(0..180).map {it*1_000_000_000L/120}
        assertTrue(samples.maxOf {down.getValueFromNanos(it)}>100f)
        assertTrue(up.getValueFromNanos(8_000_000L)>100f)
        assertTrue(samples.minOf {up.getValueFromNanos(it)}<0f)
        assertEquals(0f,up.getValueFromNanos(samples.last()),.01f)
    }
    @Test fun readingStackExitsInOrderAndUsesTheSameReversibleProgress() {
        assertTrue(SceneMotion.readingOffset(.04f,0,300f,900f)>0f)
        assertEquals(0f,SceneMotion.readingOffset(.04f,1,300f,900f),0f)
        for(rank in 0..5) {
            assertEquals(0f,SceneMotion.readingOffset(0f,rank,300f,900f),0f)
            assertEquals(624f,SceneMotion.readingOffset(1f,rank,300f,900f),.001f)
        }
        assertTrue(SceneMotion.CATEGORY_SWAP_MS<SceneMotion.CATEGORY_EXIT_MS)
    }
    @Test fun homeExitCompletesInNineHundredMillisecondsWithoutASeparateLeadStage(){
        assertEquals(475,SceneMotion.HOME_EXIT_STAGGER_MS)
        assertEquals(425,SceneMotion.HOME_EXIT_CARD_MS)
        assertEquals(900,SceneMotion.HOME_EXIT_MS)
    }
    @Test fun pageClocksAreIndependentOfTheSharedAvatarClock() {
        assertEquals(1000,SceneMotion.PRIMARY_ELEMENT_MS)
        assertEquals(900,SceneMotion.HOME_ENTER_MS)
        assertEquals(700,SceneMotion.PROFILE_ENTER_MS);assertEquals(700,SceneMotion.PROFILE_EXIT_MS)
        assertEquals(400,SceneMotion.PROFILE_GUEST_ENTER_MS);assertEquals(400,SceneMotion.PROFILE_GUEST_EXIT_MS)
        assertEquals(900,SceneMotion.SEARCH_ENTER_MS);assertEquals(900,SceneMotion.SEARCH_EXIT_MS)
    }
    @Test fun guestSettingsFadeInDuringTheLastOneHundredTwentyMillisOfEntry() {
        fun alpha(elapsedMillis: Int)=SceneMotion.profileGuestSettingsAlpha(
            SoSceneEase.transform(elapsedMillis.toFloat()/SceneMotion.PROFILE_GUEST_ENTER_MS))
        assertEquals(0f,alpha(280),0f)
        assertTrue(alpha(340)>0f&&alpha(340)<1f)
        assertEquals(1f,alpha(400),0f)
    }
    @Test fun profileWindowsEndTwoHundredMillisecondsEarlierOnlyOnEntry() {
        for((start,end) in listOf(100f to 450f,120f to 480f,140f to 540f,150f to 500f,150f to 400f)) {
            val duration=SceneMotion.PROFILE_ENTER_MS.toFloat()
            assertEquals(0f,SceneMotion.profileSegment(start/duration,start,end,exiting=false),.001f)
            assertEquals(.5f,SceneMotion.profileSegment((start+end-200f)/(2f*duration),start,end,exiting=false),.001f)
            assertEquals(1f,SceneMotion.profileSegment((end-200f)/duration,start,end,exiting=false),.001f)
            assertEquals(.5f,SceneMotion.profileSegment((start+end)/2000f,start,end,exiting=true),.001f)
        }
    }
    @Test fun profileDeckEntersInSevenHundredMillisecondsWithoutAnotherStagger() {
        val travel=780f+24f-220f
        for(rank in 0..4) {
            assertEquals(travel*.5f,SceneMotion.waveOffset(.5f,FlowPhase.ENTER,rank,5,220f,780f,SceneMotion.PROFILE_ENTER_MS,0),.01f)
            assertEquals(0f,SceneMotion.waveOffset(0f,FlowPhase.ENTER,rank,5,220f,780f,SceneMotion.PROFILE_ENTER_MS,0),0f)
        }
    }
    @Test fun sceneTravelDoesNotFinishMostOfItsDistanceInTheFirstThird() {
        assertTrue(SoSceneEase.transform(.3f)<.3f)
        assertEquals(.5f,SoSceneEase.transform(.5f),.001f)
        assertTrue(SoSceneEase.transform(.8f)<.95f)
        assertEquals(.5f,SceneMotion.searchHeader(375f/SceneMotion.SEARCH_ENTER_MS),.001f)
    }
    @Test fun searchEntranceAndExitFitTheSameFasterHeaderAndCardWindows() {
        val duration=SceneMotion.SEARCH_ENTER_MS
        val stagger=SceneMotion.SEARCH_STAGGER_MS
        val primary=SceneMotion.SEARCH_PRIMARY_MS
        assertEquals(750,primary);assertEquals(150,stagger)
        assertEquals(1f,SceneMotion.searchHeader(primary.toFloat()/duration),.001f)
        assertEquals(duration,SceneMotion.SEARCH_EXIT_MS)
        assertEquals(.5f,SceneMotion.searchHeader(375f/SceneMotion.SEARCH_EXIT_MS),.001f)
        val count=7;val top=220f;val height=780f;val travel=height+24f-top
        for(rank in 0 until count) {
            val delay=(count-1-rank).toFloat()/(count-1)*stagger
            val halfway=1f-(delay+primary/2f)/duration
            assertEquals(travel*.5f,SceneMotion.waveOffset(halfway,FlowPhase.ENTER,rank,count,top,height,duration,stagger),.01f)
            val finished=1f-(delay+primary)/duration
            assertEquals(0f,SceneMotion.waveOffset(finished,FlowPhase.ENTER,rank,count,top,height,duration,stagger),.001f)
            val exitDelay=rank.toFloat()/(count-1)*stagger
            val exitHalfway=(exitDelay+primary/2f)/duration
            assertEquals(travel*.5f,SceneMotion.waveOffset(exitHalfway,FlowPhase.EXIT,rank,count,top,height,duration,stagger),.01f)
            val exitFinished=(exitDelay+primary)/duration
            assertEquals(travel,SceneMotion.waveOffset(exitFinished,FlowPhase.EXIT,rank,count,top,height,duration,stagger),.001f)
        }
    }
    @Test fun homeEntranceKeepsArticleOneAtTheRealFocusAndStartsOutsideTheUpperLeft() {
        val g=ArcDeckMath.geometry(411f,780f)
        val poses=ArcDeckMath.frames(11,0f,g)
        for(pose in poses) {
            val entering=ArcDeckMath.scenePose(pose,g,1f,FlowPhase.ENTER)
            assertTrue(entering.centerX+g.cardWidth/2f<0f)
            assertTrue(entering.centerY in 0f..g.focusY)
        }
        val focus=ArcDeckMath.scenePose(poses.single {it.article==0},g,0f,FlowPhase.ENTER)
        assertEquals(0,focus.article);assertEquals(g.focusX,focus.centerX,.001f);assertEquals(g.focusY,focus.centerY,.001f)
    }
    @Test fun homeExitStartsEveryCardImmediately() {
        val g=ArcDeckMath.geometry(411f,780f)
        val elapsed=32f
        for(pose in ArcDeckMath.frames(11,0f,g)) {
            val next=ArcDeckMath.scenePose(pose,g,elapsed/SceneMotion.HOME_EXIT_MS,FlowPhase.EXIT)
            assertTrue("Every card starts moving without a wait",next.centerY>pose.centerY)
        }
    }

    @Test fun homeExitHasNoStationaryHandoffInTheMiddleOfItsPath() {
        val g=ArcDeckMath.geometry(411f,780f)
        val poses=ArcDeckMath.frames(11,0f,g)
        for(pose in poses) {
            val order=((ArcDeckMath.VISIBLE_DISTANCE-pose.distance)/(2f*ArcDeckMath.VISIBLE_DISTANCE)).coerceIn(0f,1f)
            val duration=SceneMotion.HOME_EXIT_CARD_MS+order*SceneMotion.HOME_EXIT_STAGGER_MS
            var previous=pose.centerY
            for(step in 1..19) {
                val next=ArcDeckMath.scenePose(pose,g,duration*(step/20f)/SceneMotion.HOME_EXIT_MS,FlowPhase.EXIT)
                assertTrue("There must be no pause before the final stop",next.centerY>previous)
                previous=next.centerY
            }
        }
    }

    @Test fun homeExitUsesTheScrollingPathForEveryCardAndFractionalQueuePosition() {
        for((w,h) in listOf(320f to 620f,411f to 780f,800f to 1200f)) {
            val g=ArcDeckMath.geometry(w,h)
            for(position in listOf(-.5f,0f,2.25f)) {
                for(pose in ArcDeckMath.frames(11,position,g)) {
                    val order=((ArcDeckMath.VISIBLE_DISTANCE-pose.distance)/(2f*ArcDeckMath.VISIBLE_DISTANCE)).coerceIn(0f,1f)
                    for(frame in 0..40) {
                        val hidden=frame/40f
                        val elapsed=hidden*SceneMotion.HOME_EXIT_MS
                        val duration=SceneMotion.HOME_EXIT_CARD_MS+order*SceneMotion.HOME_EXIT_STAGGER_MS
                        val departure=SoSceneEase.transform(SceneMotion.segment(elapsed,0f,duration))
                        val distance=pose.distance+
                            (ArcDeckMath.EDGE_CLEAR_DISTANCE-pose.distance).coerceAtLeast(0f)*departure
                        val wrapped=ArcDeckMath.frames(11,-distance,g,includeOffscreen=true).single {it.article==0}
                        val scrolling=ArcDeckMath.continuePose(wrapped,distance,11,g)
                        val exiting=ArcDeckMath.scenePose(pose,g,hidden,FlowPhase.EXIT)
                        assertEquals("Exit must share the scrolling path horizontally",scrolling.centerX,exiting.centerX,.001f)
                        assertEquals("Exit must share the scrolling path vertically",scrolling.centerY,exiting.centerY,.001f)
                        assertEquals(pose.article,exiting.article)
                        assertEquals(pose.layer,exiting.layer,0f)
                    }
                }
            }
        }
    }

    @Test fun homeExitFollowsTheArcUntilTheWholeCardClearsTheLowerLeftEdge() {
        for((w,h) in listOf(360f to 760f,411f to 780f,800f to 1200f)) {
            val g=ArcDeckMath.geometry(w,h)
            for(pose in ArcDeckMath.frames(11,2.25f,g)) {
                var previous=pose.centerY
                var previousX=pose.centerX
                for(frame in 0..30) {
                    val next=ArcDeckMath.scenePose(pose,g,frame/30f,FlowPhase.EXIT)
                    assertTrue(next.centerY>=previous-.001f)
                    if(pose.distance>=0f)assertTrue(next.centerX<=previousX+.001f)
                    assertEquals(pose.scale,next.scale,0f)
                    assertEquals(pose.alpha,next.alpha,0f)
                    assertEquals(pose.layer,next.layer,0f)
                    previous=next.centerY
                    previousX=next.centerX
                }
                val end=ArcDeckMath.scenePose(pose,g,1f,FlowPhase.EXIT)
                assertTrue("The whole card must clear the left edge",end.centerX+g.cardWidth*end.scale/2f<0f)
                assertTrue("Every card leaves through the lower end of the scrolling queue",end.centerY>g.focusY)
                assertEquals(pose.article,end.article)
            }
        }
    }
    @Test fun categoryExitStaysForegroundFirstAndEntranceStaysBackFirst() {
        val count=6;val top=220f;val height=780f
        assertEquals(900,SceneMotion.CATEGORY_ENTER_MS)
        assertEquals(900,SceneMotion.CATEGORY_EXIT_MS)
        assertEquals(180,SceneMotion.DECK_STAGGER_MS)
        fun offset(hidden: Float,phase: FlowPhase,rank: Int)=
            SceneMotion.waveOffset(hidden,phase,rank,count,top,height,entranceEase=SceneMotion.categoryRouteEntranceEase)
        assertTrue(offset(.15f,FlowPhase.EXIT,0)>0f)
        assertEquals(0f,offset(.15f,FlowPhase.EXIT,count-1),.001f)
        val hidden=height+24f-top
        assertEquals(hidden,offset(.85f,FlowPhase.ENTER,0),.001f)
        assertTrue(offset(.85f,FlowPhase.ENTER,count-1)<hidden)
        assertEquals(hidden,offset(.8f,FlowPhase.EXIT,0),.001f)
        val backSettled=SceneMotion.DECK_STAGGER_MS.toFloat()/SceneMotion.CATEGORY_ENTER_MS
        assertEquals(0f,offset(backSettled,FlowPhase.ENTER,count-1),.001f)
        for(rank in 0 until count) {
            assertEquals(0f,offset(0f,FlowPhase.ENTER,rank),0f)
            assertEquals(hidden,offset(1f,FlowPhase.EXIT,rank),.001f)
        }
    }
    @Test fun sharedLiftCommitsOnlyAtTheDistanceOrEffectiveFlingThreshold() {
        assertFalse(SceneMotion.commits(.44f,0f));assertTrue(SceneMotion.commits(.45f,0f))
        assertFalse(SceneMotion.commits(.07f,1200f));assertTrue(SceneMotion.commits(.1f,900f));assertFalse(SceneMotion.commits(.1f,-900f))
    }
}
