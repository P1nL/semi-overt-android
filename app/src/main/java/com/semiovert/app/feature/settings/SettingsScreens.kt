package com.semiovert.app.feature.settings

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.data.LocalBusiness
import com.semiovert.app.core.data.userMessage
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*
import com.semiovert.app.navigation.ScreenRoutes
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.circleColors
import com.semiovert.app.feature.home.LocalHomeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(state: PreviewState,model: PreviewViewModel,onBack: ()->Unit,onRoute: (String)->Unit,onSignOut: ()->Unit) {
    val business=LocalBusiness.current
    val owner=business?.session?.collectAsStateWithLifecycle()?.value?.account?.id
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var serverError by remember {mutableStateOf<String?>(null)}
    var confirmation by remember {mutableStateOf<String?>(null)}
    val sectionTitle=MaterialTheme.typography.titleMedium.withInterfaceTypeface()
    val itemTitle=MaterialTheme.typography.bodyMedium.withInterfaceTypeface()
    PageFrame("设置",onBack,"settings-screen",titleStyle=sectionTitle) {
        Text("外观",style=sectionTitle)
        val dark=MaterialTheme.colorScheme.background.luminance()<.5f
        Row(Modifier.fillMaxWidth().heightIn(min=80.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end=16.dp)) {
                Text("跟随系统",style=itemTitle)
                Text(if(state.theme==ThemeMode.SYSTEM)"随系统自动切换浅色和深色" else "在首页头像下方切换主题",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val spark=rememberSparkControl()
            Switch(checked=state.theme==ThemeMode.SYSTEM,interactionSource=spark.source,
                onCheckedChange={model.theme(if(it)ThemeMode.SYSTEM else if(dark)ThemeMode.DARK else ThemeMode.LIGHT)},
                modifier=spark.modifier.testTag("follow-system-theme").semantics {contentDescription="主题跟随系统"})
        }
        SettingRow("调整导航位置","在底部安全区域移动主圆") {onRoute(ScreenRoutes.NAV_POSITION)}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text("本地修改记录",style=itemTitle);Text(if(state.signedIn)"查看文章增删与版本对比" else "登录后使用",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            val spark=rememberSparkControl()
            Switch(checked=state.snapshots,onCheckedChange={
                if(it)confirmation="enable"
                else if(business==null)model.snapshots(false)
                else if(owner!=null)business.history.enable(owner,false)
            },enabled=state.signedIn,interactionSource=spark.source,modifier=spark.modifier.semantics {contentDescription="本地修改记录开关"})
        }
        if(business==null||owner!=null) SettingRow("清除本地记录","清除快照，保留每日汇总") {confirmation="clear"}
        if(business!=null&&owner!=null) SettingRow("创作记录","查看创作日历") {onRoute(ScreenRoutes.RECORDS)}
        if(business==null&&state.snapshotsCleared) {
            val spark=rememberSparkControl()
            TextButton(onClick=model::restoreExamples,interactionSource=spark.source,modifier=spark.modifier) {Text("恢复本地快照")}
        }
        HorizontalDivider(Modifier.padding(vertical=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
        Text("账号",style=sectionTitle,modifier=Modifier.padding(bottom=8.dp))
        SettingRow("重置密码","验证邮箱并设置新密码") {onRoute(ScreenRoutes.FORGOT)}
        if(state.signedIn) {
            val spark=rememberSparkControl()
            OutlinedButton(onClick={confirmation="logout"},enabled=!busy,interactionSource=spark.source,modifier=Modifier.fillMaxWidth().padding(top=20.dp).heightIn(min=52.dp).then(spark.modifier)) {Text(if(busy)"正在退出…" else "退出登录")}
        }
        serverError?.let { Text(it,Modifier.padding(top=12.dp),color=MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(32.dp))
    }
    when(confirmation) {
        "enable"->ConfirmationSheet("开启修改记录？","保存到云端之后，才会在本机记录正文增删。",onConfirm={
            if(business==null)model.snapshots(true) else if(owner!=null)business.history.enable(owner,true)
            confirmation=null
        },onDismiss={confirmation=null})
        "clear"->ConfirmationSheet("清除本地记录？","清除本机快照，保留每日汇总。",confirm="清除",onConfirm={
            if(business==null)model.clearSnapshots() else if(owner!=null)scope.launch {business.history.clear(owner)}
            confirmation=null
        },onDismiss={confirmation=null})
        "logout"->ConfirmationSheet("退出登录？","退出后可重新登录。",confirm="退出",onConfirm={
            confirmation=null
            if(business==null){model.signOut();onSignOut()} else {
                busy=true;serverError=null
                scope.launch {
                    try { business.signOut();onSignOut() }
                    catch(error: CancellationException) { throw error }
                    catch(error: Exception) { serverError=error.userMessage() }
                    finally { busy=false }
                }
            }
        },onDismiss={confirmation=null})
    }
}

@Composable
fun NavigationPositionScreen(initial: NavigationPlacement,onBack: ()->Unit,onSave: (NavigationPlacement)->Unit) {
    var x by rememberSaveable {mutableFloatStateOf(initial.x)}
    var y by rememberSaveable {mutableFloatStateOf(initial.y)}
    val density=LocalDensity.current
    val snapshot=LocalHomeSnapshot.current?.image
    var softened by remember(snapshot) {mutableStateOf<ImageBitmap?>(null)}
    LaunchedEffect(snapshot) {
        if(snapshot!=null&&Build.VERSION.SDK_INT<31)softened=withContext(Dispatchers.Default) {
            // Downsample only the non-interactive snapshot; this also works before Android 12.
            val small=Bitmap.createScaledBitmap(snapshot.asAndroidBitmap(),(snapshot.width/8).coerceAtLeast(1),
                (snapshot.height/8).coerceAtLeast(1),true)
            val result=Bitmap.createScaledBitmap(small,snapshot.width,snapshot.height,true).asImageBitmap()
            small.recycle();result
        }
    }
    BackHandler(onBack=onBack)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("navigation-position-screen")) {
        snapshot?.let {Image(it,null,Modifier.fillMaxSize().testTag("position-home-snapshot"),contentScale=ContentScale.FillBounds)}
        val bottomInset=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Only mark the actual swept bounds of the 56dp circle and its 140dp vertical travel.
        val adjustmentHeight=56.dp+140.dp
        val nativeBlur=Build.VERSION.SDK_INT>=31
        (if(nativeBlur)snapshot else softened)?.let {image->
            Box(Modifier.fillMaxSize().drawWithContent {
                val bottom=size.height-(bottomInset+18.dp).toPx()
                val bounds=Rect(20.dp.toPx(),(bottom-adjustmentHeight.toPx()).coerceAtLeast(0f),size.width-20.dp.toPx(),bottom)
                val area=Path().apply {addRoundRect(RoundRect(bounds,CornerRadius(24.dp.toPx())))}
                clipPath(area) {this@drawWithContent.drawContent()}
            }) {Image(image,null,Modifier.fillMaxSize().then(if(nativeBlur)Modifier.blur(6.dp) else Modifier),contentScale=ContentScale.FillBounds)}
        }
        val background=MaterialTheme.colorScheme.background
        val circle=Destination.HOME.circleColors
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start=20.dp,end=20.dp,bottom=18.dp)
            .fillMaxWidth().height(adjustmentHeight).testTag("position-adjustment-area")
            .clip(RoundedCornerShape(24.dp)).background(background.copy(alpha=.42f)),contentAlignment=Alignment.Center) {
            Text("拖动调整",style=MaterialTheme.typography.bodyMedium.withInterfaceTypeface(),color=MaterialTheme.colorScheme.onSurface,
                modifier=Modifier.testTag("position-adjustment-hint"))
        }
        val saveSpark=rememberSparkControl()
        FloatingActionButton(onClick={onSave(NavigationPlacement(x,y))},shape=CircleShape,interactionSource=saveSpark.source,
            containerColor=circle.background,contentColor=circle.foreground,
            modifier=Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end=20.dp,bottom=248.dp)
                .size(56.dp).then(saveSpark.modifier).testTag("save-navigation-position").semantics {contentDescription="保存位置"}) {
            SoIcon(SoSymbol.CHECK,tint=circle.foreground)
        }
        val travelX=with(density){(maxWidth-96.dp).coerceAtLeast(0.dp).toPx()}
        val travelY=with(density){140.dp.toPx()}
        Surface(shape=CircleShape,color=circle.background,contentColor=circle.foreground,shadowElevation=6.dp,
            modifier=Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end=20.dp,bottom=18.dp)
                .offset(x=-(maxWidth-96.dp).coerceAtLeast(0.dp)*(1f-x),y=-140.dp*y).size(56.dp)
                .testTag("position-preview").semantics {
                    contentDescription="拖动调整主圆位置"
                    stateDescription="横向 ${(x*100).roundToInt()}%，高度 ${(y*100).roundToInt()}%"
                    customActions=listOf(
                        CustomAccessibilityAction("向左移动") {x=(x-.1f).coerceAtLeast(0f);true},
                        CustomAccessibilityAction("向右移动") {x=(x+.1f).coerceAtMost(1f);true},
                        CustomAccessibilityAction("向上移动") {y=(y+.1f).coerceAtMost(1f);true},
                        CustomAccessibilityAction("向下移动") {y=(y-.1f).coerceAtLeast(0f);true})
                }.pointerInput(travelX,travelY) {
                    detectDragGestures {change,amount ->
                        change.consume();x=(x+amount.x/travelX.coerceAtLeast(1f)).coerceIn(0f,1f)
                        y=(y-amount.y/travelY).coerceIn(0f,1f)
                    }
                }) {Box(contentAlignment=Alignment.Center) {SoIcon(SoSymbol.HOME,tint=circle.foreground)}}
    }
}
