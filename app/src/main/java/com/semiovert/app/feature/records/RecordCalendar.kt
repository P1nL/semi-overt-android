package com.semiovert.app.feature.records

import androidx.compose.animation.*
import androidx.compose.animation.core.*

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.semiovert.app.core.data.*
import com.semiovert.app.core.designsystem.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*

private val recordDayColors=listOf(Color(0xFFBF0FFF),Color(0xFFCBFF49),Color(0xFF12BFBF))

private fun recordDayTier(words: Int): Int = when {
    words<=0->0
    words<300->1
    words<800->2
    else->3
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCalendar(authorId: String,owner: Boolean,snapshots: Boolean,cleared: Boolean,initialExpanded: Boolean=false,textProgress: Float=1f,topPadding: Dp=24.dp,
    calendar: Map<LocalDate,Int>?=null) {
    val business=LocalBusiness.current
    val changes=if(business!=null&&snapshots&&owner&&authorId.isNotBlank()) {
        val revision=business.history.changes.collectAsStateWithLifecycle().value
        rememberRequest(business,authorId,revision){business.history.list(authorId)}.first.value.orEmpty()
    } else emptyList()
    var expanded by rememberSaveable {mutableStateOf(initialExpanded)}
    var monthValue by rememberSaveable {mutableStateOf(if(business==null)"2026-10" else YearMonth.now(ZoneOffset.UTC).toString())}
    var selectedDay by rememberSaveable {mutableStateOf(0)}
    var monthPicker by remember {mutableStateOf(false)}
    var diffId by rememberSaveable {mutableStateOf<String?>(null)}
    val month=YearMonth.parse(monthValue)
    val records=remember(authorId,business) {if(business==null)DemoRecords.forAuthor(authorId) else emptyList()}
    val revisions=if(business!=null)changes else records.mapNotNull { record ->
        SampleLibrary.article(record.articleId)?.let { LocalRevision(record.articleId,it.title,record.date,record.before,record.after) }
    }
    val dailyWords=remember(calendar,records) {calendar ?: records.groupingBy {it.date}.fold(0) {count,record->count+record.summaryWords}}
    val motion=LocalMotionEnabled.current
    val duration=motionMillis(SceneMotion.DRAWER_MS)
    val typography=MaterialTheme.typography
    val weekdayStyle=typography.bodyMedium
    val dayStyle=typography.bodyLarge.copy(fontSize=22.sp)
    val arrow by animateFloatAsState(if(expanded)180f else 0f,tween(duration,easing=SoSceneEase),label="calendar-arrow")
    var displayedDate by remember {mutableStateOf(if(selectedDay>0)month.atDay(selectedDay) else null)}
    LaunchedEffect(selectedDay,month) {
        if(selectedDay>0)displayedDate=month.atDay(selectedDay)
    }
    Row(Modifier.fillMaxWidth().padding(top=topPadding,bottom=12.dp).drawWithContent {clipRect(right=size.width*textProgress.coerceIn(0f,1f)){this@drawWithContent.drawContent()}},verticalAlignment=Alignment.CenterVertically) {
        val expandSpark=rememberSparkControl();val monthSpark=rememberSparkControl()
        TextButton(onClick={expanded=!expanded},interactionSource=expandSpark.source,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp).then(expandSpark.modifier).testTag("records-expand")) {
            RecordSectionTitle("记录")
            SoIcon(SoSymbol.CHEVRON,modifier=Modifier.padding(start=4.dp).size(16.dp).graphicsLayer {rotationZ=arrow})
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick={monthPicker=true},interactionSource=monthSpark.source,modifier=monthSpark.modifier.testTag("records-month")) {
            RecordSectionNumber(month.toString().replace('-','.'))
        }
    }
    AnimatedVisibility(expanded,
        modifier=Modifier.testTag("record-calendar-grid"),
        enter=expandVertically(tween(duration,easing=SoSceneEase),expandFrom=Alignment.Top)+fadeIn(tween(duration)),
        exit=shrinkVertically(tween(duration,easing=SoSceneEase),shrinkTowards=Alignment.Top)+fadeOut(tween(duration))) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                listOf("一","二","三","四","五","六","日").forEach {
                    Text(it,Modifier.weight(1f).padding(vertical=10.dp),textAlign=TextAlign.Center,
                        style=weekdayStyle,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RecordDateGrid(month,dailyWords,selectedDay,dayStyle) {day->selectedDay=if(selectedDay==day)0 else day}
        }
    }
    val detailDate=if(selectedDay>0)month.atDay(selectedDay) else displayedDate
    AnimatedVisibility(expanded&&selectedDay>0,enter=expandVertically(tween(duration,easing=SoSceneEase),expandFrom=Alignment.Top)+fadeIn(tween(duration)),
            exit=shrinkVertically(tween(duration,easing=SoSceneEase),shrinkTowards=Alignment.Top)+fadeOut(tween(duration))) {
            Box(Modifier.fillMaxWidth().padding(top=12.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer).testTag("record-detail")) {
                AnimatedContent(detailDate,transitionSpec={
                    (slideInHorizontally(tween(duration,easing=SoSceneEase)) {it} togetherWith
                        slideOutHorizontally(tween(duration,easing=SoSceneEase)) {-it})
                        .using(SizeTransform(clip=true) {_,_->tween(duration,easing=SoSceneEase)})
                },label="record-date-content") {date ->
                if(date!=null)Column(Modifier.fillMaxWidth().padding(16.dp).testTag("record-content-"+date.dayOfMonth)) {
                    val selectedRecords=revisions.filter {it.date==date}
                    val wordCount=dailyWords[date] ?: 0
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("${date.monthValue}月${date.dayOfMonth}日·${wordCount}字".withEmphasizedInterfaceDigits(26.sp),style=typography.titleLarge,
                            fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        Box(Modifier.size(48.dp).clickable(remember {MutableInteractionSource()},indication=if(LocalSparkFeedbackEnabled.current)LocalIndication.current else null) {selectedDay=0},contentAlignment=Alignment.Center) {SoIcon(SoSymbol.CLOSE,"关闭日期详情")}
                    }
                    if(owner&&snapshots&&!cleared) {
                        selectedRecords.forEach {record ->
                            val spark=rememberSparkControl()
                            TextButton(onClick={diffId=record.id},interactionSource=spark.source,modifier=Modifier.fillMaxWidth().then(spark.modifier).testTag("record-diff-"+record.id)) {
                                Column(Modifier.weight(1f)) {
                                    Text(record.title,style=typography.bodyLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
                                    Text(("新增 +"+record.added+" · 删减 −"+record.removed).withEmphasizedInterfaceDigits(18.sp),style=weekdayStyle,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
                                }
                                SoIcon(SoSymbol.ARROW,tint=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                }
            }
    }
    if(monthPicker) MonthPicker(month,{monthValue=it.toString();selectedDay=0;monthPicker=false},{monthPicker=false})
    val record=revisions.find {it.id==diffId}
    if(record!=null&&owner&&snapshots&&!cleared) SoBottomSheet(onDismissRequest={diffId=null},dragHandleHeight=24.dp) {
        val colors=MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxWidth().padding(start=24.dp,top=4.dp,end=24.dp,bottom=24.dp).testTag("diff-sheet"),
            verticalArrangement=Arrangement.spacedBy(12.dp),
        ) {
            Text("Diff",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.semantics {heading()})
            Text(record.title,style=MaterialTheme.typography.titleMedium)
            DiffExcerpt(
                label="删减 −${record.removed}",text=record.before,accent=colors.error,
                background=colors.errorContainer,foreground=colors.onErrorContainer,
                modifier=Modifier.testTag("diff-removed-quote"),
            )
            DiffExcerpt(
                label="新增 +${record.added}",text=record.after,accent=colors.primary,
                background=colors.primaryContainer,foreground=colors.onPrimaryContainer,labelColor=colors.accentInk,
                modifier=Modifier.testTag("diff-added-quote"),
            )
        }
    }
}

@Composable
private fun RecordDateGrid(month: YearMonth,dailyWords: Map<LocalDate,Int>,selectedDay: Int,style: TextStyle,onSelect: (Int)->Unit) {
    val weeks=remember(month,dailyWords) {
        val first=month.atDay(1).dayOfWeek.value-1
        val days=month.lengthOfMonth()
        List((first+days+6)/7) {week ->
            List(7) {column ->
                val day=(week*7+column-first+1).takeIf {it in 1..days}
                day to recordDayTier(day?.let {dailyWords[month.atDay(it)]} ?: 0)
            }
        }
    }
    val colors=MaterialTheme.colorScheme
    val fills=remember(colors.surface,colors.surfaceVariant,colors.onSurface) {
        listOf(colors.surfaceVariant)+recordDayColors.map {color ->
            if(colors.onSurface.luminance()>.5f)color.copy(alpha=.42f).compositeOver(colors.surface) else color
        }
    }
    val shape=RoundedCornerShape(10.dp)
    val motion=LocalMotionEnabled.current
    val highlightAlpha by animateFloatAsState(if(selectedDay>0)1f else 0f,tween(motionMillis(SceneMotion.DRAWER_MS),easing=SoSceneEase),
        label="date-highlight-alpha")
    val dateBounds=remember(month) {mutableStateMapOf<Int,Rect>()}
    var gridOrigin by remember {mutableStateOf(Offset.Zero)}
    var highlightTarget by remember {mutableStateOf(Rect.Zero)}
    LaunchedEffect(selectedDay,dateBounds[selectedDay],gridOrigin) {
        if(selectedDay>0)dateBounds[selectedDay]?.let {highlightTarget=it.translate(-gridOrigin)}
    }
    fun weight(tier: Int)=1f+tier*.16f
    Box(Modifier.fillMaxWidth().height(336.dp).clipToBounds().onGloballyPositioned {gridOrigin=it.positionInRoot()}) {
        Column(Modifier.fillMaxSize()) {
            weeks.forEach {week->
                Row(Modifier.fillMaxWidth().weight(week.sumOf {weight(it.second).toDouble()}.toFloat())) {
                    week.forEach { (day,tier)->
                        val cell=Modifier.weight(weight(tier)).fillMaxHeight().padding(2.dp)
                        if(day==null) Spacer(cell)
                        else Box(cell.clip(shape).background(fills[tier])
                            .clickable(remember {MutableInteractionSource()},indication=if(LocalSparkFeedbackEnabled.current)LocalIndication.current else null) {onSelect(day)}
                            .semantics {contentDescription="${month.year}年${month.monthValue}月${day}日";selected=day==selectedDay}
                            .onGloballyPositioned {dateBounds[day]=Rect(it.positionInRoot(),it.size.toSize())}
                            .testTag("record-day-$day"),contentAlignment=Alignment.Center) {
                            Text(day.toString().withEmphasizedInterfaceDigits(),style=style,color=colors.onSurface,softWrap=false,maxLines=1)
                        }
                    }
                }
            }
        }
        if(highlightTarget!=Rect.Zero) {
            val highlight by animateRectAsState(highlightTarget,if(motion)spring(dampingRatio=.78f,stiffness=150f) else snap(),label="date-highlight-position")
            Canvas(Modifier.matchParentSize().testTag("record-selection")) {
                val inset=1.dp.toPx()
                drawRoundRect(colors.onSurface.copy(alpha=highlightAlpha),highlight.topLeft+Offset(inset,inset),
                    Size(highlight.width-2*inset,highlight.height-2*inset),CornerRadius(9.dp.toPx()),style=Stroke(2*inset))
            }
        }
    }
}

@Composable
internal fun RecordSectionTitle(value: String,modifier: Modifier=Modifier) {
    Text(value,modifier=modifier,style=MaterialTheme.typography.titleMedium.withInterfaceTypeface(),
        fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
}

@Composable
internal fun RecordSectionNumber(value: String,modifier: Modifier=Modifier) {
    Text(value.withEmphasizedInterfaceDigits(),modifier=modifier,style=MaterialTheme.typography.bodyLarge.copy(fontSize=20.sp),
        fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DiffExcerpt(label: String,text: String,accent: Color,background: Color,foreground: Color,
    modifier: Modifier=Modifier,labelColor: Color=accent) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label.withInterfaceDigits(),style=MaterialTheme.typography.labelLarge,color=labelColor)
        Text(
            text,style=MaterialTheme.typography.bodyLarge,color=foreground,
            modifier=modifier.fillMaxWidth()
                .drawBehind {
                    val ruleWidth=2.dp.toPx()
                    val ruleLeft=if(layoutDirection==LayoutDirection.Ltr)0f else size.width-ruleWidth
                    drawRect(accent,topLeft=Offset(ruleLeft,0f),size=Size(ruleWidth,size.height))
                }.padding(start=18.dp)
                .clip(RoundedCornerShape(16.dp)).background(background)
                .padding(horizontal=14.dp,vertical=8.dp),
        )
    }
}
