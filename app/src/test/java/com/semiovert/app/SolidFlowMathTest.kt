package com.semiovert.app

import com.semiovert.app.core.designsystem.solid.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SolidFlowMathTest {
    private data class Case(val index: Int,val scroll: Float,val progress: Float,val returnLine: Float,val layer: Float,val y: Float,val z: Float,val angle: Float)
    // Goldens evaluated from the user's original stacked-feed.mjs and loop-path.mjs.
    @Test fun matchesOriginalReferenceAtFlatBendAnd1774StackPose() {
        val cases=listOf(
        Case(0,0f,3784f,1834.0885142487145f,0f,362f,0f,6.283185307179586f),
        Case(4,0f,2312f,1834.0885142487145f,0f,1834f,0f,6.283185307179586f),
        Case(10,0f,295.304347826087f,1834.0885142487145f,-8f,93.52772029088247f,-55.45534766876939f,1.3614906832298141f),
        Case(0,220f,182.69565217391306f,1834.0885142487145f,-8f,179.304347826087f,0f,6.283185307179586f),
        Case(4,220f,2532f,1834.0885142487145f,0f,1614f,0f,6.283185307179586f),
        Case(10,220f,462.69565217391306f,1834.0885142487145f,-16f,204.7841664226275f,-140f,3.141592653589793f),
        Case(0,1774f,1510f,1834.0885142487145f,-1.6204070312499974f,1252.0885142487145f,-140f,3.141592653589793f),
        Case(4,1774f,245.08695652173913f,1834.0885142487145f,-8f,119.96650439350017f,-14.025137364184978f,0.6440993788819859f),
        Case(10,1774f,1878f,1834.0885142487145f,0f,1620.0885142487145f,-140f,3.141592653589793f)
        )
        cases.forEach {c ->
            val frame=SolidFlowMath.frame(c.index,c.scroll)
            assertEquals(c.progress,frame.progress,.001f);assertEquals(c.returnLine,frame.returnLine,.001f);assertEquals(c.layer,frame.layer,.001f)
            val point=SolidFlowMath.point(frame.progress,176f,70f,frame.returnLine)
            assertEquals(c.y,point.y,.001f);assertEquals(c.z,point.z,.001f);assertEquals(c.angle,point.angle,.0001f)
        }
    }
    @Test fun roundedGeometryHasSeparateClosedFrontBackAndRim() {
        val front=SolidGeometry.face(false);val back=SolidGeometry.face(true);val rim=SolidGeometry.rim()
        assertEquals(7257,front.vertices.size/8);assertEquals(7257,back.vertices.size/8);assertEquals(1728,rim.vertices.size/8)
        assertEquals(29024,(front.indices.size+back.indices.size+rim.indices.size)/3)
        assertEquals(.5f,front.vertices[2],0f);assertEquals(-.5f,back.vertices[2],0f)
        assertTrue(SolidFlowMath.halfWidth(0f)<SolidFlowMath.halfWidth(176f))
    }
    @Test fun thicknessStaysSixAlongTheDeformedSurfaceNormal() {
        val frame=SolidFlowMath.frame(0,220f)
        for(y in listOf(0f,70f,176f,352f)) {
            val front=SolidFlowMath.vertex(0f,y,.5f,frame);val back=SolidFlowMath.vertex(0f,y,-.5f,frame)
            assertEquals(6f,sqrt((front.x-back.x).pow(2)+(front.y-back.y).pow(2)+(front.z-back.z).pow(2)),.001f)
        }
    }
    @Test fun loopIsContinuousAtBothTurnsAndTheReturnSeam() {
        val r=70f;val end=900f;val arc=PI.toFloat()*r;val straight=end-162f
        for(distance in listOf(0f,arc,arc+straight,2*arc+straight,2*(arc+straight))) {
            val before=SolidFlowMath.point(24f+distance-.001f,0f,r,end);val after=SolidFlowMath.point(24f+distance+.001f,0f,r,end)
            assertEquals(before.y,after.y,.01f);assertEquals(before.z,after.z,.01f)
        }
    }
    @Test fun flatFrontIsPickableButTheRearDoesNotOpenAnArticle() {
        val flat=SolidFlowMath.frame(0,0f)
        assertEquals(0,SolidFlowMath.pick(listOf(flat),0f,300f))
        val back=SolidFlowMath.Frame(600f,900f,0f)
        assertNull(SolidFlowMath.pick(listOf(back),0f,300f))
    }
}
