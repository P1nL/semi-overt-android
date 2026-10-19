package com.semiovert.app.core.designsystem.solid

import kotlin.math.*

/** Direct port of loop-path.mjs / stacked-feed.mjs, not a bitmap fold approximation. */
object SolidFlowMath {
    const val WIDTH=372f
    const val HEIGHT=352f
    const val COVER=176f
    const val CORNER=26f
    const val REST_TOP=186f
    const val BEND_LINE=162f
    const val STRIDE=368f
    const val STACK_STRIDE=280f
    const val EASE=80f
    private const val STACK_END=3*STRIDE
    private const val LAYER_RELEASE=STACK_END+EASE+STRIDE
    private const val COMPRESSION=1f-STACK_STRIDE/STRIDE

    data class Frame(val progress: Float,val returnLine: Float,val layer: Float)
    data class Point(val y: Float,val z: Float,val angle: Float)
    data class Vertex(val x: Float,val y: Float,val z: Float)
    data class Projection(val x: Float,val y: Float,val depth: Float)
    data class HitBounds(val left: Float,val top: Float,val right: Float,val bottom: Float)

    fun modulo(value: Double,period: Double)=((value%period)+period)%period
    fun ease(value: Double): Double {val t=value.coerceIn(0.0,1.0);return t*t*t*(t*(t*6-15)+10)}
    fun integral(value: Double): Double {val d=max(value,0.0);val t=min(d/EASE,1.0);return EASE*t*t*t*(1-.5*t)+max(d-EASE,0.0)}

    /** Empty route space is not rendered as duplicate articles. Real feeds have finite scroll. */
    fun frame(index: Int,progress: Float,radius: Float=70f,thickness: Float=6f,slots: Int=11): Frame {
        val period=max(slots,11)*STRIDE.toDouble()
        val d=modulo(progress-24.0-index*STRIDE,period)
        val rank=floor(d/STRIDE)+ease((d%STRIDE)/EASE)
        val layer=-(thickness+2)*rank*(1-ease((d-LAYER_RELEASE)/(3*EASE)))
        val compressed=d-COMPRESSION*(integral(d)-integral(d-STACK_END))
        return Frame((compressed+24).toFloat(),(BEND_LINE+(period-COMPRESSION*STACK_END)/2-PI*radius).toFloat(),layer.toFloat())
    }

    fun point(progress: Float,localY: Float,radius: Float=70f,returnLine: Float=900f): Point {
        val straight=returnLine-BEND_LINE.toDouble();val arc=PI*radius
        val d=modulo(progress-24.0-localY,2*(straight+arc))
        return when {
            d<arc ->{val a=d/radius;Point((BEND_LINE-radius*sin(a)).toFloat(),(-radius*(1-cos(a))).toFloat(),a.toFloat())}
            d<arc+straight ->Point((BEND_LINE+d-arc).toFloat(),-2*radius,PI.toFloat())
            d<2*arc+straight ->{val a=(d-arc-straight)/radius;Point((returnLine+radius*sin(a)).toFloat(),(-radius*(1+cos(a))).toFloat(),(PI+a).toFloat())}
            else ->Point((returnLine-(d-2*arc-straight)).toFloat(),0f,(2*PI).toFloat())
        }
    }

    fun halfWidth(y: Float): Float {
        val d=when {y<CORNER->CORNER-y;y>HEIGHT-CORNER->y-HEIGHT+CORNER;else->0f}
        return WIDTH/2-if(d>0)CORNER-sqrt(max(0f,CORNER*CORNER-d*d)) else 0f
    }

    fun vertex(x: Float,y: Float,z: Float,frame: Frame,radius: Float=70f,thickness: Float=6f): Vertex {
        val route=point(frame.progress,y,radius,frame.returnLine)
        val normalOffset=z*thickness+frame.layer
        return Vertex(x,-route.y+sin(route.angle)*normalOffset,route.z+cos(route.angle)*normalOffset)
    }

    /** Production camera is orthographic, yaw 5°, pitch 0°, as in the supplied URL. */
    fun project(v: Vertex,yaw: Float=5f): Projection {
        val a=(yaw*PI/180).toFloat()
        return Projection(v.x*cos(a)-(v.z+40)*sin(a),-v.y,v.x*sin(a)+(v.z+40)*cos(a))
    }

    fun frontBounds(frame: Frame,radius: Float=70f,thickness: Float=6f): HitBounds? {
        val points=ArrayList<Projection>()
        for(row in 0..176) {
            val y=HEIGHT*row/176
            if(cos(point(frame.progress,y,radius,frame.returnLine).angle)<=0f)continue
            val half=halfWidth(y)
            points+=project(vertex(-half,y,.5f,frame,radius,thickness))
            points+=project(vertex(half,y,.5f,frame,radius,thickness))
        }
        return if(points.isEmpty())null else HitBounds(points.minOf {it.x},points.minOf {it.y},points.maxOf {it.x},points.maxOf {it.y})
    }

    /** Ray against the same sampled front/back surfaces; a back face occludes clicks. */
    fun pick(frames: List<Frame>,x: Float,y: Float,radius: Float=70f,thickness: Float=6f): Int? {
        var closest=Float.NEGATIVE_INFINITY;var selected: Int?=null
        frames.forEachIndexed {index,frame ->
            for(back in listOf(false,true)) {
                var previous: Pair<Projection,Projection>?=null
                for(row in 0..176) {
                    val materialY=HEIGHT*row/176;val half=halfWidth(materialY)
                    val z=if(back)-.5f else .5f
                    val left=project(vertex(-half,materialY,z,frame,radius,thickness))
                    val right=project(vertex(half,materialY,z,frame,radius,thickness))
                    previous?.let {prev ->
                        val (a,b)=prev
                        if(y>=min(a.y,left.y)&&y<=max(a.y,left.y)&&abs(left.y-a.y)>.0001f) {
                            val t=(y-a.y)/(left.y-a.y)
                            val lx=a.x+(left.x-a.x)*t;val rx=b.x+(right.x-b.x)*t
                            if(x in min(lx,rx)..max(lx,rx)&&abs(rx-lx)>.0001f) {
                                val u=(x-lx)/(rx-lx)
                                val lz=a.depth+(left.depth-a.depth)*t;val rz=b.depth+(right.depth-b.depth)*t
                                val depth=lz+(rz-lz)*u
                                if(depth>closest) {closest=depth;selected=if(back)null else index}
                            }
                        }
                    }
                    previous=left to right
                }
            }
        }
        return selected
    }
}
