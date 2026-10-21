package com.semiovert.app

import com.semiovert.app.core.designsystem.ReadingIconMorph
import com.semiovert.app.core.designsystem.SoSymbol
import com.semiovert.app.core.designsystem.IconFrame
import com.semiovert.app.core.designsystem.MainIconMorph
import org.junit.Assert.*
import org.junit.Test

class ReadingIconMorphTest {
    @Test fun allSixPagePairsMorphBothWaysAndRetargetWithoutRestartingAtAnEndpoint() {
        val symbols=listOf(SoSymbol.HOME,SoSymbol.CATEGORY,SoSymbol.SEARCH,SoSymbol.PERSON)
        for((index,from) in symbols.withIndex())for(to in symbols.drop(index+1)) {
            val a=IconFrame.endpoint(from)
            val b=IconFrame.endpoint(to)
            val forward=MainIconMorph(a,b)
            val reverse=MainIconMorph(b,a)
            assertEquals(a,forward.frame(0f))
            assertEquals(b,forward.frame(1f))
            val middle=forward.frame(.35f)
            val back=reverse.frame(.65f)
            assertNull(middle.symbol)
            for(i in middle.contours.indices) {
                val contour=middle.contours[i]
                assertEquals(contour.width,back.contours[i].width,.00001f)
                assertEquals(contour.fill,back.contours[i].fill,.00001f)
                contour.points.forEachIndexed {j,point->
                    assertEquals(point.x,back.contours[i].points[j].x,.00001f)
                    assertEquals(point.y,back.contours[i].points[j].y,.00001f)
                    assertTrue(point.x.isFinite()&&point.y.isFinite())
                }
            }
            val redirected=MainIconMorph(middle,IconFrame.endpoint(SoSymbol.CONTENTS))
            assertEquals("A new page must inherit the actual partially morphed shape",middle,redirected.frame(0f))
            assertEquals(SoSymbol.CONTENTS,redirected.frame(1f).symbol)
        }
    }
    @Test fun fourPageIconsHaveContinuousReversiblePathsAndIdenticalContentsEndpoints() {
        val symbols=listOf(SoSymbol.HOME,SoSymbol.CATEGORY,SoSymbol.SEARCH,SoSymbol.PERSON)
        val plans=symbols.map {ReadingIconMorph(it)}
        plans.forEach {plan ->
            assertEquals(6,plan.strokes.size)
            plan.strokes.forEachIndexed {index,stroke ->
                assertEquals(48,stroke.source.size)
                assertEquals(stroke.source.size,stroke.target.size)
                for(i in stroke.source.indices) {
                    assertEquals(stroke.source[i],stroke.pointAt(i,0f))
                    val endpoint=stroke.pointAt(i,1f)
                    assertEquals(stroke.target[i].x,endpoint.x,.00001f)
                    assertEquals(stroke.target[i].y,endpoint.y,.00001f)
                    val before=stroke.pointAt(i,.49f)
                    val after=stroke.pointAt(i,.51f)
                    assertTrue((after-before).getDistance()<.7f)
                    for(p in listOf(0f,.25f,.5f,.75f,1f)) {
                        val point=stroke.pointAt(i,p)
                        assertTrue(point.x.isFinite()&&point.y.isFinite())
                        assertTrue(point.x in 0f..24f&&point.y in 0f..24f)
                    }
                    // A controlled reverse hits exactly the same contour without a second timer.
                    val forward=stroke.pointAt(i,.4f)
                    val reverse=stroke.pointAt(i,1f-.6f)
                    assertEquals(forward.x,reverse.x,.00001f)
                    assertEquals(forward.y,reverse.y,.00001f)
                }
                if(index<3) {
                    assertEquals(1.65f,stroke.widthAt(1f),.00001f)
                    assertEquals(0f,stroke.fillAt(1f),0f)
                    assertEquals(9f,stroke.target.first().x,0f)
                    assertEquals(20f,stroke.target.last().x,0f)
                    assertEquals(6f+6f*index,stroke.target.first().y,0f)
                } else {
                    assertEquals(0f,stroke.widthAt(1f),.00001f)
                    assertEquals(1f,stroke.fillAt(1f),0f)
                }
            }
        }
        for(index in 0..5)plans.drop(1).forEach {plan ->
            assertEquals(plans.first().strokes[index].target,plan.strokes[index].target)
        }
    }
}
