package com.semiovert.app.feature.records

import androidx.compose.animation.*
import androidx.compose.animation.core.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCalendar(authorId: String,owner: Boolean,snapshots: Boolean,cleared: Boolean,initialExpanded: Boolean=false) {
    var expanded by rememberSaveable {mutableStateOf(initialExpanded)}
    var monthValue by rememberSaveable {mutableStateOf("2026-10")}
    var selectedDay by rememberSaveable {mutableStateOf(0)}
    var monthPicker by remember {mutableStateOf(false)}
    var diffId by rememberSaveable {mutableStateOf<String?>(null)}
    val month=YearMonth.parse(monthValue)
    val records=remember(authorId) {DemoRecords.forAuthor(authorId)}
    val motion=LocalMotionEnabled.current
    val duration=motionMillis(280)
    val arrow by animateFloatAsState(if(expanded)180f else 0f,tween(duration,easing=SoEase),label="calendar-arrow")
    val dateBounds=remember {mutableStateMapOf<Int,Rect>()}
    var bubbleOrigin by remember {mutableStateOf(Offset.Zero)}
    var displayedDay by remember {mutableIntStateOf(selectedDay)}
    val bubble=remember {Animatable(if(selectedDay>0)1f else 0f)}
    LaunchedEffect(selectedDay,motion) {
        if(selectedDay>0) {if(displayedDay!=selectedDay)bubble.snapTo(0f);displayedDay=selectedDay}
        bubble.animateTo(if(selectedDay>0)1f else 0f,tween(if(motion)if(selectedDay>0)280 else 220 else 0,easing=SoEase))
    }
    Row(Modifier.fillMaxWidth().padding(top=24.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp).testTag("records-expand")) {
            Text("记录",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurface)
            SoIcon(SoSymbol.CHEVRON,modifier=Modifier.padding(start=4.dp).size(16.dp).graphicsLayer {rotationZ=arrow})
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick={monthPicker=true},modifier=Modifier.testTag("records-month")) {Text(month.toString().replace('-','.'),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    AnimatedVisibility(expanded,enter=expandVertically(tween(duration,easing=SoEase))+fadeIn(tween(duration)),exit=shrinkVertically(tween(duration,easing=SoEase))+fadeOut(tween(duration))) {
    Column {
        Row(Modifier.fillMaxWidth()) {listOf("一","二","三","四","五","六","日").forEach {Text(it,modifier=Modifier.weight(1f).padding(vertical=10.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        val start=month.atDay(1).dayOfWeek.value-1
        for(week in 0 until (start+month.lengthOfMonth()+6)/7) {
            Row(Modifier.fillMaxWidth()) {
                for(column in 0..6) {
                    val day=week*7+column-start+1
                    if(day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                    else {
                        val hasRecords=records.any {it.date==month.atDay(day)}
                        Box(Modifier.weight(1f).height(48.dp).clip(CircleShape).background(if(day==selectedDay)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable {selectedDay=if(selectedDay==day)0 else day}.semantics {contentDescription=month.year.toString()+"年"+month.monthValue+"月"+day+"日";selected=day==selectedDay}
                            .onGloballyPositioned {dateBounds[day]=it.boundsInRoot()}.testTag("record-day-"+day),contentAlignment=Alignment.Center) {
                            Text(day.toString(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurface)
                            if(hasRecords) Box(Modifier.align(Alignment.BottomCenter).padding(bottom=5.dp).size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
        if(displayedDay>0&&(selectedDay>0||bubble.value>0f)) {
            val selectedRecords=records.filter {it.date==month.atDay(displayedDay)}
            val bubbleColor=MaterialTheme.colorScheme.primaryContainer
            Box(Modifier.fillMaxWidth().padding(top=12.dp).testTag("record-detail").layout {measurable,constraints ->val child=measurable.measure(constraints);layout(child.width,(child.height*bubble.value).roundToInt()) {child.place(0,0)}}.onGloballyPositioned {bubbleOrigin=it.positionInRoot()}) {
                Canvas(Modifier.matchParentSize()) {
                    val source=dateBounds[displayedDay]
                    val p=bubble.value
                    val from=source?.let {Rect(it.left-bubbleOrigin.x,it.top-bubbleOrigin.y,it.right-bubbleOrigin.x,it.bottom-bubbleOrigin.y)}?:Rect(Offset.Zero,Size(48.dp.toPx(),48.dp.toPx()))
                    val rect=lerp(from,Rect(Offset.Zero,size),p)
                    drawRoundRect(bubbleColor,rect.topLeft,rect.size,CornerRadius((24f-4f*p).dp.toPx()))
                }
                Column(Modifier.padding(16.dp).graphicsLayer {alpha=((bubble.value-.65f)/.35f).coerceIn(0f,1f)}) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(month.monthValue.toString()+" 月 "+displayedDay+" 日",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        IconButton(onClick={selectedDay=0}) {SoIcon(SoSymbol.CLOSE,"关闭日期详情")}
                    }
                    if(selectedRecords.isEmpty())Text("这一天没有示例记录",style=MaterialTheme.typography.bodyMedium)
                    else if(owner&&snapshots&&!cleared) {
                        selectedRecords.forEach {record ->
                            TextButton(onClick={diffId=record.articleId},modifier=Modifier.fillMaxWidth().testTag("record-diff-"+record.articleId)) {
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
        Text(if(owner)"内置演示记录，并非真实保存历史。" else "作者公开汇总示例，不读取本机快照。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=12.dp,bottom=8.dp))
    }
    }
    if(monthPicker) MonthPicker(month,{monthValue=it.toString();selectedDay=0;monthPicker=false},{monthPicker=false})
    val record=records.find {it.articleId==diffId}
    if(record!=null&&owner&&snapshots&&!cleared) ModalBottomSheet(modifier=Modifier.preferredFrameRate(MotionFramePolicy.TARGET_FPS),onDismissRequest={diffId=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
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
    ModalBottomSheet(modifier=Modifier.preferredFrameRate(MotionFramePolicy.TARGET_FPS),onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("选择年月",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                TextButton(onClick={onApply(YearMonth.of(year,month))}) {Text("确定")}
                TextButton(onClick=onDismiss) {Text("取消")}
            }
            Row(Modifier.fillMaxWidth().height(240.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                LazyColumn(state=rememberLazyListState((initial.year-2021).coerceAtLeast(0)),modifier=Modifier.weight(1f)) {
                    items((2021..2031).toList()) {value -> TextButton(onClick={year=value},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics {selected=value==year}) {Text(value.toString()+" 年",color=if(value==year)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}}
                }
                LazyColumn(state=rememberLazyListState((initial.monthValue-3).coerceAtLeast(0)),modifier=Modifier.weight(1f)) {
                    items((1..12).toList()) {value -> TextButton(onClick={month=value},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics {selected=value==month}) {Text(value.toString()+" 月",color=if(value==month)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}}
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
