package com.semiovert.app.core.designsystem.cuboid

import kotlin.math.*

data class CubeVector(val x: Float, val y: Float, val z: Float) {
    operator fun plus(b: CubeVector) = CubeVector(x+b.x,y+b.y,z+b.z)
    operator fun times(s: Float) = CubeVector(x*s,y*s,z*s)
    fun dot(b: CubeVector) = x*b.x+y*b.y+z*b.z
    fun cross(b: CubeVector) = CubeVector(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
}

/** Unit quaternions keep repeated, mixed-axis full turns free of Euler-angle locks. */
data class CubeRotation(val w: Float=1f, val x: Float=0f, val y: Float=0f, val z: Float=0f) {
    fun normalized(): CubeRotation {
        val length=sqrt(w*w+x*x+y*y+z*z)
        return if(length<.00001f) CubeRotation() else CubeRotation(w/length,x/length,y/length,z/length)
    }
    operator fun times(b: CubeRotation) = CubeRotation(
        w*b.w-x*b.x-y*b.y-z*b.z, w*b.x+x*b.w+y*b.z-z*b.y,
        w*b.y-x*b.z+y*b.w+z*b.x, w*b.z+x*b.y-y*b.x+z*b.w).normalized()
    fun rotate(p: CubeVector): CubeVector {
        val v=CubeVector(x,y,z); val t=v.cross(p)*2f
        return p+t*w+v.cross(t)
    }
    fun drag(horizontal: Float, vertical: Float): CubeRotation {
        val angle=hypot(horizontal,vertical)
        if(angle<.000001f) return this
        return axis(CubeVector(vertical/angle,horizontal/angle,0f),angle)*this
    }
    fun angleTo(to: CubeRotation)=2f*acos(abs(w*to.w+x*to.x+y*to.y+z*to.z).coerceIn(0f,1f))
    fun interpolate(to: CubeRotation, progress: Float): CubeRotation {
        var end=to
        var dot=w*end.w+x*end.x+y*end.y+z*end.z
        if(dot<0f) {end=CubeRotation(-end.w,-end.x,-end.y,-end.z);dot=-dot}
        val p=progress.coerceIn(0f,1f)
        val angle=acos(dot.coerceIn(-1f,1f))
        val a: Float; val b: Float
        if(angle<.001f) {a=1f-p;b=p} else {a=sin((1f-p)*angle)/sin(angle);b=sin(p*angle)/sin(angle)}
        return CubeRotation(w*a+end.w*b,x*a+end.x*b,y*a+end.y*b,z*a+end.z*b).normalized()
    }
    companion object {
        fun axis(axis: CubeVector, radians: Float): CubeRotation {
            val s=sin(radians/2f)
            return CubeRotation(cos(radians/2f),axis.x*s,axis.y*s,axis.z*s).normalized()
        }
    }
}

data class CubeFace(val normal: CubeVector, val right: CubeVector, val up: CubeVector, val resting: CubeRotation) {
    // Texture order: top-left, top-right, bottom-right, bottom-left.
    fun corners(inset: Float=0f): List<CubeVector> {
        val half=.5f-inset
        return listOf(normal*.5f+right*-half+up*half,normal*.5f+right*half+up*half,
            normal*.5f+right*half+up*-half,normal*.5f+right*-half+up*-half)
    }
}

data class CubePoint(val x: Float, val y: Float)
data class ProjectedCubeFace(val face: Int, val corners: List<CubePoint>, val depth: Float, val shade: Float, val outline: List<CubePoint>)
internal data class CubeBevel(val corners: List<CubeVector>, val normal: CubeVector, val faces: List<Int>)
data class ProjectedCubeBevel(val corners: List<CubePoint>, val depth: Float, val light: Float, val faces: List<Int>)
data class CuboidFrame(val rotation: CubeRotation, val articles: List<Int>)
data class CuboidSettlement(val from: CubeRotation, val to: CubeRotation, val face: Int, val article: Int)
data class CuboidSnapshot(val rotation: CubeRotation, val articles: List<Int>, val cursor: Int, val selected: Int)

object CuboidMath {
    const val CAMERA=5f
    const val REST_WIDTH=.89f
    // About 2.4 degrees of hysteresis either side of a two-face boundary.
    const val FACE_HYSTERESIS=.06f
    // About 10dp at the resting phone size: softened, not a pill-shaped box.
    const val EDGE_RADIUS=.028f
    private const val ARC_STEPS=6
    val faces: List<CubeFace> = run {
        val x=CubeVector(1f,0f,0f);val y=CubeVector(0f,1f,0f);val z=CubeVector(0f,0f,1f)
        val quarter=PI.toFloat()/2f
        listOf(CubeFace(z,x,y,CubeRotation()),
            CubeFace(x,z*-1f,y,CubeRotation.axis(y,-quarter)),
            CubeFace(z*-1f,x*-1f,y,CubeRotation.axis(y,2*quarter)),
            CubeFace(x*-1f,z,y,CubeRotation.axis(y,quarter)),
            CubeFace(y,x,z*-1f,CubeRotation.axis(x,quarter)),
            CubeFace(y*-1f,x,z,CubeRotation.axis(x,-quarter)))
    }
    fun visible(rotation: CubeRotation): Set<Int> = faces.indices.filter {
        val normal=rotation.rotate(faces[it].normal)
        // Perspective visibility, not just normal.z: side planes are hidden at rest.
        normal.z*CAMERA>.5001f
    }.toSet()

    /** Closed rounded shell: 12 quarter-cylinder edges and 8 spherical corners. */
    internal val bevels: List<CubeBevel> by lazy {
        val inner=.5f-EDGE_RADIUS
        val quarter=PI.toFloat()/2f
        fun vector(values: FloatArray)=CubeVector(values[0],values[1],values[2])
        fun face(axis: Int,sign: Float)=when(axis) {0->if(sign>0)1 else 3;1->if(sign>0)4 else 5;else->if(sign>0)0 else 2}
        buildList {
            for(axis in 0..2) {
                val other=(0..2).filter {it!=axis}
                for(a in listOf(-1f,1f))for(b in listOf(-1f,1f)) {
                    fun normal(angle: Float)=vector(FloatArray(3).also {it[other[0]]=a*cos(angle);it[other[1]]=b*sin(angle)})
                    fun point(angle: Float,end: Float)=vector(FloatArray(3).also {
                        it[axis]=end;it[other[0]]=a*(inner+EDGE_RADIUS*cos(angle));it[other[1]]=b*(inner+EDGE_RADIUS*sin(angle))
                    })
                    for(step in 0 until ARC_STEPS) {
                        val from=quarter*step/ARC_STEPS;val to=quarter*(step+1)/ARC_STEPS
                        add(CubeBevel(listOf(point(from,-inner),point(from,inner),point(to,inner),point(to,-inner)),
                            normal((from+to)/2f),listOf(face(other[0],a),face(other[1],b))))
                    }
                }
            }
            for(x in listOf(-1f,1f))for(y in listOf(-1f,1f))for(z in listOf(-1f,1f)) {
                val center=CubeVector(x*inner,y*inner,z*inner)
                fun normal(theta: Float,phi: Float)=CubeVector(x*sin(theta)*cos(phi),y*cos(theta),z*sin(theta)*sin(phi))
                fun point(theta: Float,phi: Float)=center+normal(theta,phi)*EDGE_RADIUS
                for(row in 0 until ARC_STEPS)for(column in 0 until ARC_STEPS) {
                    val top=quarter*row/ARC_STEPS;val bottom=quarter*(row+1)/ARC_STEPS
                    val left=quarter*column/ARC_STEPS;val right=quarter*(column+1)/ARC_STEPS
                    add(CubeBevel(listOf(point(top,left),point(top,right),point(bottom,right),point(bottom,left)),
                        normal((top+bottom)/2f,(left+right)/2f),listOf(face(0,x),face(1,y),face(2,z))))
                }
            }
        }
    }

    private fun projector(rotation: CubeRotation,width: Float,height: Float): (CubeVector)->CubePoint {
        val focal=width*REST_WIDTH*(CAMERA-.5f)
        fun raw(p: CubeVector)=CubePoint(p.x*focal/(CAMERA-p.z),-p.y*focal/(CAMERA-p.z))
        val all=faces.flatMap {it.corners()}.map {raw(rotation.rotate(it))}
        val extentX=all.maxOf {abs(it.x)}*2f
        val extentY=all.maxOf {abs(it.y)}*2f
        val fit=minOf(1f,width*.94f/extentX,height*.92f/extentY)
        return {point ->raw(rotation.rotate(point)).let {CubePoint(width/2f+it.x*fit,height/2f+it.y*fit)}}
    }

    fun project(rotation: CubeRotation, width: Float, height: Float): List<ProjectedCubeFace> {
        val project=projector(rotation,width,height)
        return visible(rotation).map {index ->
            val normal=rotation.rotate(faces[index].normal)
            ProjectedCubeFace(index,faces[index].corners().map(project),
                normal.z*.5f,(1f-normal.z.coerceIn(0f,1f))*.23f,faces[index].corners(EDGE_RADIUS).map(project))
        }.sortedBy {it.depth}
    }

    fun projectBevels(rotation: CubeRotation,width: Float,height: Float): List<ProjectedCubeBevel> {
        val project=projector(rotation,width,height)
        return bevels.mapNotNull {patch ->
            val normal=rotation.rotate(patch.normal)
            val center=rotation.rotate(patch.corners.reduce {a,b->a+b}*.25f)
            if(normal.z*CAMERA<=normal.dot(center)) null
            else ProjectedCubeBevel(patch.corners.map(project),center.z,.86f+.14f*normal.z.coerceIn(0f,1f),patch.faces)
        }.sortedBy {it.depth}
    }
}

/** Six reusable faces retain their articles until completely hidden. Release selects the actual face. */
class CuboidTurn(val count: Int, selected: Int, restored: CuboidSnapshot?=null) {
    init {require(count>0 && selected in 0 until count)}
    private var resting=restored ?: CuboidSnapshot(CubeRotation(),List(6) {(selected+it)%count},selected+6,selected)
    var selected=resting.selected; private set
    var rotation=resting.rotation; private set
    private var contents=resting.articles
    private var visible=CuboidMath.visible(rotation)
    var focusedFace=CuboidMath.faces.indices.maxBy {rotation.rotate(CuboidMath.faces[it].normal).z}; private set
    private var cursor=resting.cursor
    private var settlement: CuboidSettlement?=null
    fun frame()=CuboidFrame(rotation,contents.toList())
    // Persist only a completed landing, not an interrupted gesture or animation.
    fun snapshot()=resting
    /** Screen Y points down; positive pitch moves the front face down on screen too. */
    fun dragScreen(horizontal: Float, vertical: Float, width: Float) {
        val radians=2f*PI.toFloat()/width
        drag(horizontal*radians,vertical*radians)
    }
    fun drag(horizontal: Float, vertical: Float) {
        if(settlement!=null) return
        moveTo(rotation.drag(horizontal,vertical))
    }
    private fun moveTo(next: CubeRotation) {
        rotation=next
        val now=CuboidMath.visible(rotation)
        val updated=contents.toMutableList()
        // A texture may change only after its entire plane has left the view.
        for(face in visible-now) {
            val used=updated.filterIndexed {index,_->index!=face}.toSet()
            var candidate=cursor++%count
            repeat(count) {if(candidate in used) candidate=cursor++%count}
            updated[face]=candidate
        }
        contents=updated;visible=now
        val candidate=CuboidMath.faces.indices.maxBy {rotation.rotate(CuboidMath.faces[it].normal).z}
        val currentFacing=rotation.rotate(CuboidMath.faces[focusedFace].normal).z
        val candidateFacing=rotation.rotate(CuboidMath.faces[candidate].normal).z
        if(candidateFacing-currentFacing>CuboidMath.FACE_HYSTERESIS)focusedFace=candidate
    }
    fun settle(): CuboidSettlement {
        settlement?.let {return it}
        // Near a boundary, keep the stable face instead of letting finger jitter
        // change the article on the final pointer sample.
        val face=focusedFace
        // Rotate the existing card upright as part of the geometry animation. Its UVs
        // and content remain fixed, including when a vertical turn exposed it upside down.
        return CuboidSettlement(rotation,CuboidMath.faces[face].resting,face,contents[face]).also {settlement=it}
    }
    fun finish(): CuboidFrame {
        val plan=settlement ?: return frame()
        moveTo(plan.to)
        selected=plan.article
        resting=CuboidSnapshot(rotation,contents.toList(),cursor,selected)
        settlement=null
        return frame()
    }
    fun cancel() {
        rotation=resting.rotation;contents=resting.articles;visible=CuboidMath.visible(rotation)
        focusedFace=CuboidMath.faces.indices.maxBy {rotation.rotate(CuboidMath.faces[it].normal).z}
        cursor=resting.cursor;settlement=null
    }
}
