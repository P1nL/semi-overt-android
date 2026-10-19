package com.semiovert.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.semiovert.app.core.model.Cover

/** Foreground for the light top edge of the two built-in cover illustrations. */
val SampleCoverInk=Color(0xFF202A25)

/** Original procedural illustrations, not downloaded photographs. */
@Composable
fun SampleCover(cover: Cover, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 372f, size.height / 176f, pivot = Offset.Zero) {
            if (cover == Cover.FOREST) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFCAD6D5), Color(0xFF617975)),0f,176f),size=androidx.compose.ui.geometry.Size(372f,176f))
                drawPath(Path().apply { moveTo(0f,110f); lineTo(65f,28f); lineTo(135f,102f); lineTo(207f,49f); lineTo(310f,120f); lineTo(372f,68f); lineTo(372f,176f); lineTo(0f,176f); close() },Color(0xFF809A96))
                for (layer in 0..2) {
                    val tone = listOf(Color(0xFF5F7C76),Color(0xFF36594E),Color(0xFF1D4035))[layer]
                    for (i in 0..17) {
                        val x = i * 25f - 12f + layer * 8
                        val ground = 138f + layer * 24
                        val height = 32f + ((i * 17 + layer * 23) % 49)
                        drawPath(Path().apply {
                            moveTo(x,ground-height); lineTo(x-13,ground-height*0.39f); lineTo(x-7,ground-height*0.42f)
                            lineTo(x-19,ground); lineTo(x+19,ground); lineTo(x+7,ground-height*0.42f)
                            lineTo(x+13,ground-height*0.39f); close()
                        },tone)
                    }
                    if (layer < 2) drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color(0x558EA6A0),Color.Transparent),55f+layer*28,135f+layer*22),size=androidx.compose.ui.geometry.Size(372f,176f))
                }
            } else {
                drawRect(Brush.linearGradient(listOf(Color(0xFFB7B2A5),Color(0xFF767C73))),size=androidx.compose.ui.geometry.Size(372f,176f))
                drawPath(Path().apply { moveTo(0f,0f); lineTo(225f,0f); lineTo(284f,176f); lineTo(0f,176f); close() },Color(0xFFB9B8A9))
                for (i in 0..6) drawLine(Color(0xFF969C8D),Offset(i*37f,0f),Offset(i*37f+59f,176f),1.3f)
                for (i in 1..4) drawLine(Color(0xFF9EA397),Offset(0f,i*38f),Offset(284f,i*38f+15f),1f)
                drawPath(Path().apply { moveTo(228f,0f); lineTo(302f,0f); lineTo(372f,176f); lineTo(285f,176f); close() },Color(0xFF343F39))
                drawLine(Color(0xFFD9D1B9),Offset(310f,0f),Offset(370f,159f),14f)
                drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color(0x55344034))),size=androidx.compose.ui.geometry.Size(372f,176f))
            }
        }
    }
}
