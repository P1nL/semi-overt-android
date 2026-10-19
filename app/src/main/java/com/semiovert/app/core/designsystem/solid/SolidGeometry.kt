package com.semiovert.app.core.designsystem.solid

import java.nio.*
import kotlin.math.hypot

/** Shared rounded front, back and real closed sidewall, matching NX=40 / NY=176. */
class SolidGeometry(val vertices: FloatArray,val indices: ShortArray) {
    val vertexBuffer: FloatBuffer=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {put(vertices);position(0)}
    val indexBuffer: ShortBuffer=ByteBuffer.allocateDirect(indices.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {put(indices);position(0)}

    companion object {
        const val NX=40;const val NY=176
        private fun append(data: MutableList<Float>,x: Float,y: Float,z: Float,nx: Float,ny: Float,nz: Float) {
            data.addAll(listOf(x,y,z,nx,ny,nz,x/SolidFlowMath.WIDTH+.5f,1-y/SolidFlowMath.HEIGHT))
        }
        fun face(back: Boolean): SolidGeometry {
            val data=ArrayList<Float>();val triangles=ArrayList<Short>()
            for(row in 0..NY) {
                val y=SolidFlowMath.HEIGHT*row/NY;val half=SolidFlowMath.halfWidth(y)
                for(column in 0..NX)append(data,-half+2*half*column/NX,y,if(back)-.5f else .5f,0f,0f,if(back)-1f else 1f)
            }
            for(row in 0 until NY)for(column in 0 until NX) {
                val a=row*(NX+1)+column;val b=a+1;val c=a+NX+1;val d=c+1
                triangles.addAll((if(back)listOf(a,b,c,b,d,c) else listOf(a,c,b,b,c,d)).map {it.toShort()})
            }
            return SolidGeometry(data.toFloatArray(),triangles.toShortArray())
        }
        fun rim(): SolidGeometry {
            val perimeter=ArrayList<Pair<Float,Float>>()
            for(i in 0..NX)perimeter+=(-SolidFlowMath.halfWidth(0f)+2*SolidFlowMath.halfWidth(0f)*i/NX) to 0f
            for(j in 1..NY) {val y=SolidFlowMath.HEIGHT*j/NY;perimeter+=SolidFlowMath.halfWidth(y) to y}
            for(i in NX-1 downTo 0)perimeter+=(-SolidFlowMath.halfWidth(SolidFlowMath.HEIGHT)+2*SolidFlowMath.halfWidth(SolidFlowMath.HEIGHT)*i/NX) to SolidFlowMath.HEIGHT
            for(j in NY-1 downTo 1) {val y=SolidFlowMath.HEIGHT*j/NY;perimeter+=-SolidFlowMath.halfWidth(y) to y}
            val data=ArrayList<Float>();val triangles=ArrayList<Short>()
            perimeter.forEachIndexed {index,a ->
                val b=perimeter[(index+1)%perimeter.size];val dx=b.first-a.first;val dy=b.second-a.second;val length=hypot(dx,dy);val at=data.size/8
                for((point,z) in listOf(a to .5f,b to .5f,a to -.5f,b to -.5f))append(data,point.first,point.second,z,dy/length,dx/length,0f)
                triangles.addAll(listOf(at,at+1,at+2,at+1,at+3,at+2).map {it.toShort()})
            }
            return SolidGeometry(data.toFloatArray(),triangles.toShortArray())
        }
        fun floor()=SolidGeometry(floatArrayOf(-1600f,-2400f,-280f,0f,0f,1f,0f,0f,1600f,-2400f,-280f,0f,0f,1f,1f,0f,-1600f,700f,-280f,0f,0f,1f,0f,1f,1600f,700f,-280f,0f,0f,1f,1f,1f),shortArrayOf(0,1,2,1,3,2))
    }
}
