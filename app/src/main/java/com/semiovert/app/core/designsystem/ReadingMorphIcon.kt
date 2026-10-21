package com.semiovert.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A controlled native morph for the four page symbols <-> reading contents.
 * It has no independent timer: dragging, cancelling and returning use the reading progress.
 */
@Composable
fun ReadingMorphIcon(pageSymbol: SoSymbol,progress: Float,modifier: Modifier=Modifier,
    tint: Color=MaterialTheme.colorScheme.onSurface) {
    val morph=remember(pageSymbol) {ReadingIconMorph(pageSymbol)}
    MorphIconCanvas(morph.strokes,pageSymbol,SoSymbol.CONTENTS,progress,modifier,tint)
}

/** Also used by the persistent cross-page icon; all resting endpoints use the original renderer. */
@Composable
internal fun MorphIconCanvas(strokes: List<ReadingIconMorph.MorphStroke>,from: SoSymbol?,to: SoSymbol,
    progress: Float,modifier: Modifier=Modifier,tint: Color=MaterialTheme.colorScheme.onSurface) {
    val paths=remember(strokes) {List(strokes.size) {Path()}}
    Canvas(modifier.size(24.dp)) {
        val p=progress.coerceIn(0f,1f)
        if(p==1f||(p==0f&&from!=null))drawSoIcon(if(p==0f)from!! else to,tint)
        else scale(size.width/24f,size.height/24f,pivot=Offset.Zero) {
            strokes.forEachIndexed {index,stroke ->
                val path=paths[index]
                path.reset()
                for(i in stroke.source.indices) {
                    val point=stroke.pointAt(i,p)
                    if(i==0)path.moveTo(point.x,point.y) else path.lineTo(point.x,point.y)
                }
                val width=stroke.widthAt(p)
                if(width>0f)drawPath(path,tint,style=Stroke(width,cap=StrokeCap.Round,join=StrokeJoin.Round))
                val fill=stroke.fillAt(p)
                if(fill>0f)drawPath(path,tint.copy(alpha=tint.alpha*fill))
            }
        }
    }
}

/** Six corresponding contours: three rules and three bullets. Paths are sampled only once. */
internal class ReadingIconMorph(symbol: SoSymbol,targetSymbol: SoSymbol=SoSymbol.CONTENTS) {
    internal class Contour(val points: List<Offset>,val outlined: Boolean=true,val filled: Boolean=false)
    internal class MorphStroke(val source: List<Offset>,val target: List<Offset>,
        private val sourceWidth: Float,private val targetWidth: Float,
        private val sourceFill: Float,private val targetFill: Float) {
        fun pointAt(index: Int,p: Float)=lerp(source[index],target[index],p)
        fun widthAt(p: Float)=sourceWidth+(targetWidth-sourceWidth)*p
        fun fillAt(p: Float)=sourceFill+(targetFill-sourceFill)*p
    }
    val strokes: List<MorphStroke>
    init {
        val source=contours(symbol)
        val target=contours(targetSymbol)
        strokes=source.zip(target).map {(from,to)->MorphStroke(
            sample(from.points),sample(to.points),if(from.outlined)1.65f else 0f,if(to.outlined)1.65f else 0f,
            if(from.filled)1f else 0f,if(to.filled)1f else 0f)}
    }

    private fun contours(symbol: SoSymbol): List<Contour> = when(symbol) {
            SoSymbol.HOME->listOf(
                line(3f to 11f,12f to 3f,21f to 11f),
                line(5f to 10f,5f to 21f,10f to 21f,10f to 14f),
                line(10f to 14f,14f to 14f,14f to 21f,19f to 21f,19f to 10f),
                point(5f,10f),point(10f,14f),point(19f,10f))
            SoSymbol.CATEGORY->listOf(
                square(14f,4f),point(14f,12f),square(14f,14f),
                square(4f,4f),square(4f,14f),point(4f,18f))
            SoSymbol.SEARCH->listOf(
                ellipse(10.5f,10.5f,6.5f,6.5f),point(10.5f,10.5f),line(15.5f to 15.5f,21f to 21f),
                point(4f,10.5f),point(10.5f,4f),point(15.5f,15.5f))
            SoSymbol.PERSON->listOf(
                ellipse(12f,7f,3.5f,3.5f),ellipse(12f,20.5f,8f,6.5f,180f,180f),point(12f,20.5f),
                point(8.5f,7f),point(4f,20.5f),point(20f,20.5f))
            SoSymbol.CONTENTS->listOf(6f,12f,18f).map {y->line(9f to y,20f to y)}+
                listOf(6f,12f,18f).map {y->Contour(ellipse(4f,y,1f,1f).points,outlined=false,filled=true)}
            else->error("Icon morph supports primary-page and contents symbols only")
    }

    private fun line(vararg points: Pair<Float,Float>)=Contour(points.map {Offset(it.first,it.second)})
    private fun point(x: Float,y: Float)=Contour(listOf(Offset(x,y)),outlined=false)
    private fun ellipse(cx: Float,cy: Float,rx: Float,ry: Float,start: Float=0f,sweep: Float=360f)=
        Contour((0..48).map {i->
            val a=(start+sweep*i/48f)*PI.toFloat()/180f
            Offset(cx+rx*cos(a),cy+ry*sin(a))
        })
    private fun square(x: Float,y: Float): Contour {
        val radius=1.3f
        val corners=listOf(Offset(x+6f-radius,y+radius),Offset(x+6f-radius,y+6f-radius),
            Offset(x+radius,y+6f-radius),Offset(x+radius,y+radius))
        val points=corners.flatMapIndexed {index,center->(0..8).map {step->
            val a=(-90f+90f*index+90f*step/8f)*PI.toFloat()/180f
            Offset(center.x+radius*cos(a),center.y+radius*sin(a))
        }}
        return Contour(points+points.first())
    }
    private fun sample(points: List<Offset>): List<Offset> {
        val lengths=FloatArray(points.size)
        for(i in 1 until points.size)lengths[i]=lengths[i-1]+(points[i]-points[i-1]).getDistance()
        val total=lengths.last()
        if(total==0f)return List(48) {points.first()}
        var edge=1
        return List(48) {i->
            val distance=total*i/47f
            while(edge<points.lastIndex&&lengths[edge]<distance)edge++
            val span=lengths[edge]-lengths[edge-1]
            lerp(points[edge-1],points[edge],if(span==0f)0f else ((distance-lengths[edge-1])/span).coerceIn(0f,1f))
        }
    }
}
