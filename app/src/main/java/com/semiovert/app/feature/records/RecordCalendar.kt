package com.semiovert.app.feature.records

import androidx.compose.animation.*
import androidx.compose.animation.core.*

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.data.*
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCalendar(authorId: String,owner: Boolean,snapshots: Boolean,cleared: Boolean,initialExpanded: Boolean=false,textProgress: Float=1f) {
    var expanded by rememberSaveable {mutableStateOf(initialExpanded)}
    var monthValue by rememberSaveable {mutableStateOf("2026-10")}
    var selectedDay by rememberSaveable {mutableStateOf(0)}
    var monthPicker by remember {mutableStateOf(false)}
    var diffId by rememberSaveable {mutableStateOf<String?>(null)}
    val month=YearMonth.parse(monthValue)
    val records=remember(authorId) {DemoRecords.forAuthor(authorId)}
    val motion=LocalMotionEnabled.current
    val duration=motionMillis(SceneMotion.DRAWER_MS)
    val arrow by animateFloatAsState(if(expanded)180f else 0f,tween(duration,easing=SoSceneEase),label="calendar-arrow")
    val dateBounds=remember {mutableStateMapOf<Int,Rect>()}
    var gridOrigin by remember {mutableStateOf(Offset.Zero)}
    var highlightTarget by remember {mutableStateOf(Rect.Zero)}
    var displayedDate by remember {mutableStateOf(if(selectedDay>0)month.atDay(selectedDay) else null)}
    val highlightAlpha by animateFloatAsState(if(selectedDay>0)1f else 0f,tween(duration,easing=SoSceneEase),label="date-highlight-alpha")
    LaunchedEffect(selectedDay,month) {
        if(selectedDay>0)displayedDate=month.atDay(selectedDay)
    }
    LaunchedEffect(selectedDay,dateBounds[selectedDay],gridOrigin) {
        if(selectedDay>0)dateBounds[selectedDay]?.let {highlightTarget=it.translate(-gridOrigin)}
    }
    Row(Modifier.fillMaxWidth().padding(top=24.dp,bottom=12.dp).drawWithContent {clipRect(right=size.width*textProgress.coerceIn(0f,1f)){this@drawWithContent.drawContent()}},verticalAlignment=Alignment.CenterVertically) {
        val expandSpark=rememberSparkControl();val monthSpark=rememberSparkControl()
        TextButton(onClick={expanded=!expanded},interactionSource=expandSpark.source,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp).then(expandSpark.modifier).testTag("records-expand")) {
            Text("记录",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurface)
            SoIcon(SoSymbol.CHEVRON,modifier=Modifier.padding(start=4.dp).size(16.dp).graphicsLayer {rotationZ=arrow})
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick={monthPicker=true},interactionSource=monthSpark.source,modifier=monthSpark.modifier.testTag("records-month")) {Text(month.toString().replace('-','.'),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    AnimatedVisibility(expanded,enter=expandVertically(tween(duration,easing=SoSceneEase))+fadeIn(tween(duration)),exit=shrinkVertically(tween(duration,easing=SoSceneEase))+fadeOut(tween(duration))) {
    Column {
        Row(Modifier.fillMaxWidth()) {listOf("一","二","三","四","五","六","日").forEach {Text(it,modifier=Modifier.weight(1f).padding(vertical=10.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        val start=month.atDay(1).dayOfWeek.value-1
        Box(Modifier.fillMaxWidth().onGloballyPositioned {gridOrigin=it.positionInRoot()}) {
        if(highlightTarget!=Rect.Zero) {
            val highlight by animateRectAsState(highlightTarget,if(motion)spring(dampingRatio=.78f,stiffness=150f) else snap(),label="date-highlight-position")
            val color=MaterialTheme.colorScheme.primaryContainer
            Canvas(Modifier.matchParentSize().testTag("record-selection")) {
                drawRoundRect(color.copy(alpha=highlightAlpha),highlight.topLeft,highlight.size,CornerRadius(highlight.height/2f))
            }
        }
        Column {
        for(week in 0 until (start+month.lengthOfMonth()+6)/7) {
            Row(Modifier.fillMaxWidth()) {
                for(column in 0..6) {
                    val day=week*7+column-start+1
                    if(day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                    else {
                        val hasRecords=records.any {it.date==month.atDay(day)}
                        Box(Modifier.weight(1f).height(48.dp)
                            .clickable(remember {MutableInteractionSource()},indication=if(LocalSparkFeedbackEnabled.current)LocalIndication.current else null) {selectedDay=if(selectedDay==day)0 else day}.semantics {contentDescription=month.year.toString()+"年"+month.monthValue+"月"+day+"日";selected=day==selectedDay}
                            .onGloballyPositioned {dateBounds[day]=it.boundsInRoot()}.testTag("record-day-"+day),contentAlignment=Alignment.Center) {
                            Text(day.toString(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurface)
                            if(hasRecords) Box(Modifier.align(Alignment.BottomCenter).padding(bottom=5.dp).size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
        }
        }
        val detailDate=if(selectedDay>0)month.atDay(selectedDay) else displayedDate
        AnimatedVisibility(selectedDay>0,enter=expandVertically(tween(duration,easing=SoSceneEase),expandFrom=Alignment.Top)+fadeIn(tween(duration)),
            exit=shrinkVertically(tween(duration,easing=SoSceneEase),shrinkTowards=Alignment.Top)+fadeOut(tween(duration))) {
            Box(Modifier.fillMaxWidth().padding(top=12.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer).testTag("record-detail")) {
                AnimatedContent(detailDate,transitionSpec={
                    (slideInHorizontally(tween(duration,easing=SoSceneEase)) {it} togetherWith
                        slideOutHorizontally(tween(duration,easing=SoSceneEase)) {-it})
                        .using(SizeTransform(clip=true) {_,_->tween(duration,easing=SoSceneEase)})
                },label="record-date-content") {date ->
                if(date!=null)Column(Modifier.fillMaxWidth().padding(16.dp).testTag("record-content-"+date.dayOfMonth)) {
                    val selectedRecords=records.filter {it.date==date}
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(date.monthValue.toString()+" 月 "+date.dayOfMonth+" 日",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        Box(Modifier.size(48.dp).clickable(remember {MutableInteractionSource()},indication=if(LocalSparkFeedbackEnabled.current)LocalIndication.current else null) {selectedDay=0},contentAlignment=Alignment.Center) {SoIcon(SoSymbol.CLOSE,"关闭日期详情")}
                    }
                    if(selectedRecords.isEmpty())Text("这一天没有示例记录",style=MaterialTheme.typography.bodyMedium)
                    else if(owner&&snapshots&&!cleared) {
                        selectedRecords.forEach {record ->
                            val spark=rememberSparkControl()
                            TextButton(onClick={diffId=record.articleId},interactionSource=spark.source,modifier=Modifier.fillMaxWidth().then(spark.modifier).testTag("record-diff-"+record.articleId)) {
                                Column(Modifier.weight(1f)) {
                                    Text(SampleLibrary.article(record.articleId)!!.title,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface)
                                    Text("示例新增 +"+record.added+" · 删减 −"+record.removed,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
                                }
                                SoIcon(SoSymbol.ARROW,tint=MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Text("示例每日汇总 · "+selectedRecords.sumOf {it.summaryWords}+" 字",style=MaterialTheme.typography.bodyLarge)
                        Text(if(owner&&cleared)"本机示例快照已清除，仍可查看每日汇总。" else "汇总字数不等于当天新增；没有可用的本机快照时不展示增删。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
                    }
                }
                }
            }
        }
        Text(if(owner)"内置演示记录，并非真实保存历史。" else "作者公开汇总示例，不读取本机快照。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=12.dp,bottom=8.dp))
    }
    }
    if(monthPicker) MonthPicker(month,{monthValue=it.toString();selectedDay=0;monthPicker=false},{monthPicker=false})
    val record=records.find {it.articleId==diffId}
    if(record!=null&&owner&&snapshots&&!cleared) SoBottomSheet(onDismissRequest={diffId=null}) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp).testTag("diff-sheet")) {
            Text("文章 Diff",style=MaterialTheme.typography.headlineSmall)
            Text(SampleLibrary.article(record.articleId)!!.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=16.dp))
            DemoNotice("内置前后版本示例，不是设备捕获的真实历史。")
            Text("删减 −"+record.removed,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.error)
            Text(record.before,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.fillMaxWidth().padding(top=8.dp,bottom=20.dp))
            Text("新增 +"+record.added,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Text(record.after,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPicker(initial: YearMonth,onApply: (YearMonth)->Unit,onDismiss: ()->Unit) {
    var year by remember {mutableIntStateOf(initial.year)}
    var month by remember {mutableIntStateOf(initial.monthValue)}
    SoBottomSheet(onDismissRequest=onDismiss) {close ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("选择年月",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                val applySpark=rememberSparkControl();val cancelSpark=rememberSparkControl()
                TextButton(onClick={close {onApply(YearMonth.of(year,month))}},interactionSource=applySpark.source,modifier=applySpark.modifier) {Text("确定")}
                TextButton(onClick={close {}},interactionSource=cancelSpark.source,modifier=cancelSpark.modifier.testTag("month-cancel")) {Text("取消")}
            }
            Row(Modifier.fillMaxWidth().height(240.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                LazyColumn(state=rememberLazyListState((initial.year-2021).coerceAtLeast(0)),modifier=Modifier.weight(1f)) {
                    items((2021..2031).toList()) {value ->
                        val spark=rememberSparkControl()
                        TextButton(onClick={year=value},interactionSource=spark.source,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).then(spark.modifier).semantics {selected=value==year}) {Text(value.toString()+" 年",color=if(value==year)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}
                    }
                }
                LazyColumn(state=rememberLazyListState((initial.monthValue-3).coerceAtLeast(0)),modifier=Modifier.weight(1f)) {
                    items((1..12).toList()) {value ->
                        val spark=rememberSparkControl()
                        TextButton(onClick={month=value},interactionSource=spark.source,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).then(spark.modifier).semantics {selected=value==month}) {Text(value.toString()+" 月",color=if(value==month)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}
                    }
                }
            }
        }
    }
}

@Composable
fun RecordsScreen(state: PreviewState,onBack: ()->Unit) {
    PageFrame("记录",onBack,"records-screen") {
        DemoNotice("内置月历与版本对比示例；没有记录真实文章修改。")
        RecordCalendar("lin",owner=state.signedIn,snapshots=state.snapshots,cleared=state.snapshotsCleared,initialExpanded=true)
        Spacer(Modifier.height(28.dp))
    }
}
