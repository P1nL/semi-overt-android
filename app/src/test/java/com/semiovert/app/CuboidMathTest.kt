package com.semiovert.app

import com.semiovert.app.core.designsystem.cuboid.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class CuboidMathTest {
    @Test fun roundedEdgesAndCornersFormAClosedShellWithTheSixArticlePlanes() {
        val inner=.5f-CuboidMath.EDGE_RADIUS
        fun key(p: CubeVector)=listOf(p.x,p.y,p.z).joinToString(",") {(it*100000f).roundToInt().toString()}
        val edges=mutableMapOf<String,Int>()
        val polygons=CuboidMath.faces.map {it.corners(CuboidMath.EDGE_RADIUS)}+CuboidMath.bevels.map {it.corners}
        for(polygon in polygons)for(i in polygon.indices) {
            val a=key(polygon[i]);val b=key(polygon[(i+1)%polygon.size])
            if(a!=b) {val edge=listOf(a,b).sorted().joinToString("|");edges[edge]=(edges[edge]?:0)+1}
        }
        assertTrue("No open seams between faces, curved edges and spherical corners",edges.values.all {it==2})
        for(p in CuboidMath.bevels.flatMap {it.corners}) {
            val dx=(abs(p.x)-inner).coerceAtLeast(0f);val dy=(abs(p.y)-inner).coerceAtLeast(0f);val dz=(abs(p.z)-inner).coerceAtLeast(0f)
            assertEquals(CuboidMath.EDGE_RADIUS,sqrt(dx*dx+dy*dy+dz*dz),.00001f)
        }
    }
    @Test fun roundedShellProjectsInsideTheSameViewportWithoutChangingArticleTargets() {
        for(rotation in listOf(CubeRotation(),CubeRotation().drag(.8f,.6f),CubeRotation().drag(3.2f,1.3f))) {
            val shell=CuboidMath.projectBevels(rotation,1080f,1700f)
            assertTrue(shell.isNotEmpty())
            for(p in shell.flatMap {it.corners})assertTrue(p.x.isFinite()&&p.y.isFinite()&&p.x in 0f..1080f&&p.y in 0f..1700f)
        }
    }
    @Test fun screenDragMovesTheFrontFaceInTheSameDirectionAsTheFinger() {
        for((dx,dy) in listOf(0f to -24f,0f to 24f,-24f to 0f,24f to 0f,24f to -24f)) {
            val turn=CuboidTurn(6,0)
            turn.dragScreen(dx,dy,1080f)
            val face=CuboidMath.project(turn.frame().rotation,1080f,1700f).first {it.face==0}
            val centerX=face.corners.map {it.x}.average().toFloat()
            val centerY=face.corners.map {it.y}.average().toFloat()
            if(dx!=0f)assertTrue("Horizontal movement must follow the finger",(centerX-540f)*dx>0f)
            if(dy!=0f)assertTrue("Vertical movement must follow the finger",(centerY-850f)*dy>0f)
            assertEquals("A small drag keeps the current article",0,turn.settle().article)
        }
    }
    @Test fun anyDirectionAndManyTurnsSelectTheArticleAlreadyOnTheNearestFace() {
        for((x,y) in listOf(1f to 0f,-1f to 0f,0f to 1f,0f to -1f,.7f to .7f)) {
            val turn=CuboidTurn(11,7)
            repeat(240) {turn.drag(x*8f*PI.toFloat()/240f,y*8f*PI.toFloat()/240f)}
            assertEquals(7,turn.selected)
            val before=turn.frame()
            val plan=turn.settle()
            assertEquals(before.articles[plan.face],plan.article)
            assertEquals("Release does not abruptly replace a visible preview",before.articles,turn.frame().articles)
            val closest=CuboidMath.faces.indices.maxBy {before.rotation.rotate(CuboidMath.faces[it].normal).z}
            val nearestFacing=before.rotation.rotate(CuboidMath.faces[closest].normal).z
            val chosenFacing=before.rotation.rotate(CuboidMath.faces[plan.face].normal).z
            assertTrue("Only near-ties may retain the stable face",nearestFacing-chosenFacing<=CuboidMath.FACE_HYSTERESIS+.0001f)
            assertTrue(CuboidMath.visible(before.rotation).contains(plan.face))
            assertEquals(1f,plan.to.rotate(CuboidMath.faces[plan.face].normal).z,.0001f)
            assertEquals("Repeated release cannot queue another article",plan,turn.settle())
            val landed=turn.finish()
            assertEquals(plan.article,turn.selected)
            assertEquals(plan.article,landed.articles[plan.face])
            assertEquals(plan.to,landed.rotation)
        }
    }
    @Test fun previewTexturesOnlyRecycleOnCompletelyHiddenPlanes() {
        val turn=CuboidTurn(11,0)
        val seen=mutableSetOf<Int>()
        repeat(180) {
            val before=turn.frame()
            turn.drag(.11f,.035f)
            val after=turn.frame()
            val visible=CuboidMath.visible(after.rotation)
            for(face in visible) {assertEquals(before.articles[face],after.articles[face]);seen+=after.articles[face]}
            assertEquals("The six faces do not duplicate articles",6,after.articles.distinct().size)
        }
        assertEquals("All eleven articles circulate through the six physical faces",(0 until 11).toSet(),seen)
        val before=turn.frame();turn.settle();val after=turn.frame()
        for(face in CuboidMath.visible(before.rotation))assertEquals(before.articles[face],after.articles[face])
    }
    @Test fun everyFaceRestsUprightAndRotationStaysInsideTheStage() {
        for(face in CuboidMath.faces) {
            assertEquals(1f,face.resting.rotate(face.normal).z,.0001f)
            assertEquals(1f,face.resting.rotate(face.up).y,.0001f)
        }
        var rotation=CubeRotation()
        repeat(1000) {index ->
            rotation=rotation.drag(.071f,if(index%2==0).047f else -.019f)
            val norm=rotation.w*rotation.w+rotation.x*rotation.x+rotation.y*rotation.y+rotation.z*rotation.z
            assertEquals(1f,norm,.0001f)
            for(face in CuboidMath.project(rotation,1080f,1700f))for(p in face.corners) {
                assertTrue(p.x.isFinite()&&p.y.isFinite())
                assertTrue(p.x>=0f&&p.x<=1080f&&p.y>=0f&&p.y<=1700f)
            }
        }
        val front=CuboidMath.project(CubeRotation(),1080f,1700f).single()
        assertEquals(1080f*.89f,abs(front.corners[1].x-front.corners[0].x),.01f)
    }
    @Test fun cancellationAndReleaseWithoutAnotherFaceKeepTheCurrentArticle() {
        val turn=CuboidTurn(6,5);turn.drag(3f,2f);turn.cancel()
        assertEquals(CubeRotation(),turn.frame().rotation)
        assertEquals(5,turn.frame().articles[0])
        assertEquals(5,turn.settle().article)
        assertEquals(0,CuboidTurn(1,0).settle().article)
    }
    @Test fun smallDragsStayOnTheSameArticleAndBackFaceKeepsItsOriginalTexture() {
        for((horizontal,vertical) in listOf(.12f to 0f,-.12f to 0f,0f to .12f,0f to -.12f,0f to (2f*PI.toFloat()))) {
            val turn=CuboidTurn(6,0);turn.drag(horizontal,vertical)
            val plan=turn.settle()
            assertEquals("A short drag or full revolution keeps the user's original face",0,plan.face)
            assertEquals(0,plan.article)
            val normalBefore=plan.from.rotate(CuboidMath.faces[plan.face].normal)
            for(step in 0..10) {
                val normal=plan.from.interpolate(plan.to,step/10f).rotate(CuboidMath.faces[plan.face].normal)
                assertTrue("The picked face never rotates away to find another article",normal.z>=normalBefore.z-.0001f)
            }
        }
        val upsideDown=CuboidTurn(6,0);upsideDown.drag(0f,PI.toFloat())
        val original=upsideDown.frame().articles
        val plan=upsideDown.settle()
        assertEquals(2,plan.face)
        assertEquals(original[2],plan.article)
        assertEquals(original,upsideDown.frame().articles)
        assertEquals(1f,plan.to.rotate(CuboidMath.faces[2].up).y,.0001f)
    }
    @Test fun nearestFaceStaysFacingForwardAndTheExistingTextureFinishesUpright() {
        var rotation=CubeRotation()
        repeat(160) {
            rotation=rotation.drag(.29f,.17f)
            val turn=CuboidTurn(11,4)
            repeat(it+1) {turn.drag(.29f,.17f)}
            val plan=turn.settle()
            val face=CuboidMath.faces[plan.face]
            val nearest=CuboidMath.faces.indices.maxBy {rotation.rotate(CuboidMath.faces[it].normal).z}
            val nearestFacing=rotation.rotate(CuboidMath.faces[nearest].normal).z
            val chosenFacing=rotation.rotate(CuboidMath.faces[plan.face].normal).z
            assertTrue(nearestFacing-chosenFacing<=CuboidMath.FACE_HYSTERESIS+.0001f)
            val before=plan.from.rotate(face.normal).z
            for(step in 0..12)assertTrue("No detour away from the picked face",plan.from.interpolate(plan.to,step/12f).rotate(face.normal).z>=before-.0001f)
            val corners=face.corners().map(plan.to::rotate)
            val topLeft=corners[0]
            val topRight=corners[1]
            assertEquals(-.5f,topLeft.x,.0001f);assertEquals(.5f,topLeft.y,.0001f)
            assertEquals(.5f,topRight.x,.0001f);assertEquals(.5f,topRight.y,.0001f)
            assertEquals(turn.frame().articles[plan.face],plan.article)
        }
    }
    @Test fun consecutiveLandingsKeepThePhysicalFaceBindingsAndSaveableCheckpoint() {
        val turn=CuboidTurn(11,0)
        turn.drag(0f,-PI.toFloat()/2f)
        assertEquals("An upward quarter turn lands on article six, not forced article two",5,turn.settle().article)
        turn.finish()
        val checkpoint=turn.snapshot()
        val resting=turn.frame()
        val restored=CuboidTurn(11,checkpoint.selected,checkpoint)
        assertEquals(resting,restored.frame())

        turn.drag(.8f,.3f)
        assertEquals("Interrupted gestures persist only the last stable landing",checkpoint,turn.snapshot())
        turn.cancel()
        assertEquals(resting,turn.frame())
        assertEquals(5,turn.selected)

        for(model in listOf(turn,restored))model.drag(PI.toFloat()/2f,0f)
        assertEquals(turn.frame(),restored.frame())
        assertEquals(turn.settle(),restored.settle())
        val before=turn.frame()
        val plan=turn.settle()
        assertEquals(3,plan.article)
        val after=turn.finish()
        for(face in CuboidMath.visible(after.rotation))assertEquals(before.articles[face],after.articles[face])
    }
}
