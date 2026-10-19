package com.semiovert.app

import android.graphics.*
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.designsystem.solid.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

internal fun View.solidScene(): SolidSceneView? {
    if(this is SolidSceneView)return this
    if(this is ViewGroup)for(i in 0 until childCount)getChildAt(i).solidScene()?.let {return it}
    return null
}

@RunWith(AndroidJUnit4::class)
class SolidReferenceRenderTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()

    private fun scene()=app.runOnIdle {app.activity.window.decorView.solidScene()}
    private fun awaitPose(scroll: Float,yaw: Float=5f) {
        app.waitUntil(15_000) {scene()?.presentedSpec?.let {kotlin.math.abs(it.scroll-scroll)<.1f&&it.roll<.001f&&it.yaw==yaw&&it.textures.all {texture->texture!=null}}==true}
    }
    private fun save(name: String,bitmap: Bitmap) {
        val directory=File(app.activity.getExternalFilesDir(null),"solid-reference").apply {mkdirs()}
        File(directory,name+".png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }

    @Test fun originalElevenCardPose1774RendersWithDepthBackAndRealRim() {
        val samples=listOf(fixtureTexture(0),fixtureTexture(1))
        val textures=(0..10).map {index->samples[index%2].copy(id="reference-$index")}
        val spec=mutableStateOf(SolidRenderSpec(textures,0f,0f,0f,Color.rgb(240,238,232)))
        var failure: Throwable?=null
        app.activity.runOnUiThread {app.activity.setContent {
            AndroidView(factory={context->SolidSceneView(context).apply {onFailure={failure=it}}},modifier=Modifier.fillMaxSize(),update={it.submit(spec.value)},onRelease={it.dispose()})
        }}
        for(scroll in listOf(0f,220f,1774f)) {
            app.runOnIdle {spec.value=spec.value.copy(scroll=scroll)}
            awaitPose(scroll)
            assertNull("Native shaders must run, not fall back to a flat list",failure)
            val bitmap=scene()!!.let {view->app.runOnIdle {view.bitmap!!}}
            save("reference-${scroll.toInt()}-yaw5-radius70-thickness6-blur0",bitmap)
            assertTrue(bitmap.width>0&&bitmap.height>0)
        }
        app.runOnIdle {spec.value=spec.value.copy(scroll=430f,yaw=50f,pitch=15f)}
        awaitPose(430f,50f)
        assertNull(failure)
        save("reference-oblique-back-rim",scene()!!.let {view->app.runOnIdle {view.bitmap!!}})
    }

    @Test fun productionHomeKeepsItsArcSelectionAfterReading() {
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        app.onNodeWithTag("feed-home").performScrollToIndex(3)
        app.onNodeWithTag("feed-home").assertHomeResting(3)
        save("production-home-arc",app.onRoot().captureToImage().asAndroidBitmap())
        app.onNodeWithTag("article-window").performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack();app.waitForIdle()
        app.onNodeWithTag("feed-home").assertHomeResting(3)
        app.onNodeWithTag("article-window").assertIsDisplayed()
    }

    /** Same two QA textures as card-bend.js; rasterized natively, not through a WebView. */
    private fun fixtureTexture(index: Int): SolidTexture {
        val context=InstrumentationRegistry.getInstrumentation().context
        val photo=context.assets.open("solid-reference/"+if(index==0)"forest.jpg" else "architecture.jpg").use {BitmapFactory.decodeStream(it)}
        val bitmap=Bitmap.createBitmap(1116,1056,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.scale(3f,3f)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(Color.parseColor(if(index==0)"#DFF3E6" else "#FFE0C6"))
        canvas.drawBitmap(photo,null,RectF(0f,0f,372f,176f),paint)
        paint.color=Color.rgb(32,42,35);paint.typeface=Typeface.create("serif",Typeface.BOLD);paint.textSize=21f
        val title=if(index==0)"QA 无摘要文章：使用正文预览文本作为卡片摘要" else "QA 长摘要展示文章：观察文章卡片是否展示足够的信息密度"
        val lines=ArrayList<String>();var line=""
        title.forEach {char->if(paint.measureText(line+char)>336&&line.isNotEmpty()) {lines+=line;line=char.toString()} else line+=char};if(line.isNotEmpty())lines+=line
        lines.forEachIndexed {i,text->canvas.drawText(text,18f,214f+29*i,paint)}
        paint.color=Color.rgb(69,99,77);paint.style=Paint.Style.STROKE;paint.strokeWidth=1.3f
        canvas.drawCircle(24f,286f,5.3f,paint);canvas.drawLine(24f,282.7f,24f,286f,paint);canvas.drawLine(24f,286f,26.5f,287.3f,paint)
        paint.style=Paint.Style.FILL;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=12f
        canvas.drawText("不足 1 分钟",36f,290f,paint)
        paint.color=Color.rgb(189,220,197);canvas.drawCircle(31f,323f,13f,paint)
        paint.color=Color.rgb(36,88,60);paint.textSize=13f;canvas.drawText("Q",25.8f,327.5f,paint)
        paint.color=Color.rgb(41,70,51);paint.textSize=12f;canvas.drawText("QA 普通作者",52f,319f,paint)
        paint.color=Color.rgb(79,104,87);paint.textSize=10f;canvas.drawText("@qa_reader_user",52f,335f,paint)
        paint.color=Color.rgb(41,70,51);paint.strokeWidth=1.7f;canvas.drawLine(334f,319f,340f,325f,paint);canvas.drawLine(340f,325f,334f,331f,paint)
        val back=if(index==0)Color.rgb(189,219,196) else Color.rgb(224,194,166)
        return SolidTexture("reference-source-$index",bitmap,back)
    }
}
