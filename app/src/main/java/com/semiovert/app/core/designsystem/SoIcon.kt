package com.semiovert.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

enum class SoSymbol { HOME, CATEGORY, SEARCH, PERSON, ARROW, CLOSE, CONTENTS, CLOCK, APPEARANCE, CHEVRON, BOOK, SETTINGS, CHECK }

/** Original 24-unit stroke icons; no font glyphs or remote icon dependency. */
@Composable
fun SoIcon(symbol: SoSymbol, description: String? = null, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier.size(24.dp).then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        drawSoIcon(symbol,tint)
    }
}

/** Shared endpoint drawing keeps the morph's resting icons identical to ordinary icons. */
internal fun DrawScope.drawSoIcon(symbol: SoSymbol,tint: Color) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.65f, StrokeCap.Round)
            fun path(vararg points: Pair<Float, Float>, close: Boolean = false) {
                drawPath(Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.first, p.second) else lineTo(p.first, p.second) }; if (close) close() }, tint, style = stroke)
            }
            when (symbol) {
                SoSymbol.HOME -> { path(3f to 11f, 12f to 3f, 21f to 11f); path(5f to 10f, 5f to 21f, 10f to 21f, 10f to 14f, 14f to 14f, 14f to 21f, 19f to 21f, 19f to 10f) }
                SoSymbol.CATEGORY -> for (x in listOf(4f, 14f)) for (y in listOf(4f, 14f)) drawRoundRect(tint, Offset(x, y), Size(6f, 6f), CornerRadius(1.3f), style = stroke)
                SoSymbol.SEARCH -> { drawCircle(tint, 6.5f, Offset(10.5f,10.5f), style = stroke); line(15.5f,15.5f,21f,21f) }
                SoSymbol.PERSON -> { drawCircle(tint,3.5f,Offset(12f,7f),style=stroke); drawArc(tint,180f,180f,false,Offset(4f,14f),Size(16f,13f),style=stroke) }
                SoSymbol.ARROW -> { line(5f,12f,19f,12f); path(14f to 7f,19f to 12f,14f to 17f) }
                SoSymbol.CLOSE -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
                SoSymbol.CHECK -> path(5f to 12f,10f to 17f,19f to 7f)
                SoSymbol.CONTENTS -> { for (y in listOf(6f,12f,18f)) { drawCircle(tint,1f,Offset(4f,y)); line(9f,y,20f,y) } }
                SoSymbol.CLOCK -> { drawCircle(tint,8.5f,Offset(12f,12f),style=stroke); path(12f to 7f,12f to 12f,15f to 14f) }
                SoSymbol.APPEARANCE -> { drawCircle(tint,4.3f,Offset(12f,12f),style=stroke); for (i in 0..7) { val angle = i * Math.PI / 4; line(12f+7f*kotlin.math.cos(angle).toFloat(),12f+7f*kotlin.math.sin(angle).toFloat(),12f+9f*kotlin.math.cos(angle).toFloat(),12f+9f*kotlin.math.sin(angle).toFloat()) } }
                SoSymbol.CHEVRON -> path(7f to 10f,12f to 15f,17f to 10f)
                SoSymbol.SETTINGS -> { val gear=Path(); for(i in 0..31) {val a=i*Math.PI/16;val r=if(i%4 in 1..2)10f else 8f;val x=12f+r*kotlin.math.cos(a).toFloat();val y=12f+r*kotlin.math.sin(a).toFloat();if(i==0)gear.moveTo(x,y) else gear.lineTo(x,y)};gear.close();drawPath(gear,tint,style=stroke);drawCircle(tint,3f,Offset(12f,12f),style=stroke) }
                SoSymbol.BOOK -> { path(12f to 5f,4f to 3f,4f to 19f,12f to 21f,20f to 19f,20f to 3f,12f to 5f,12f to 21f) }
            }
        }
}
