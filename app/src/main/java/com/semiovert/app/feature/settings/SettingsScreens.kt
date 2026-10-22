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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*
import com.semiovert.app.navigation.ScreenRoutes
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.circleColors
import com.semiovert.app.feature.home.LocalHomeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(state: PreviewState,model: PreviewViewModel,onBack: ()->Unit,onRoute: (String)->Unit,onSignOut: ()->Unit) {
    var confirmation by remember {mutableStateOf<String?>(null)}
    PageFrame("设置",onBack,"settings-screen") {
        DemoNotice("离线页面与本机偏好；账号和真实文章保存尚未接入。")
        Text("外观",style=MaterialTheme.typography.titleMedium)
        val dark=MaterialTheme.colorScheme.background.luminance()<.5f
        Row(Modifier.fillMaxWidth().heightIn(min=80.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end=16.dp)) {
                Text("跟随系统",style=MaterialTheme.typography.bodyMedium)
                Text(if(state.theme==ThemeMode.SYSTEM)"随系统自动切换浅色和深色" else "在首页头像下方切换主题",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked=state.theme==ThemeMode.SYSTEM,
                onCheckedChange={model.theme(if(it)ThemeMode.SYSTEM else if(dark)ThemeMode.DARK else ThemeMode.LIGHT)},
                modifier=Modifier.testTag("follow-system-theme").semantics {contentDescription="主题跟随系统"})
        }
        SettingRow("调整导航位置","在底部安全区域移动主圆") {onRoute(ScreenRoutes.NAV_POSITION)}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text("本地修改记录",style=MaterialTheme.typography.bodyMedium);Text(if(state.signedIn)"仅预览内置快照，不采集真实修改" else "进入演示身份后可体验",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Switch(checked=state.snapshots,onCheckedChange={if(it)confirmation="enable" else model.snapshots(false)},enabled=state.signedIn,modifier=Modifier.semantics {contentDescription="本地修改记录开关"})
        }
        SettingRow("查看记录","月历、日期详情与版本示例") {onRoute(if(state.signedIn)ScreenRoutes.RECORDS else Destination.PROFILE.route)}
        SettingRow("清除本地记录","只清除本机演示快照，不影响汇总") {confirmation="clear"}
        if(state.snapshotsCleared)TextButton(onClick=model::restoreExamples) {Text("恢复内置示例快照")}
        HorizontalDivider(Modifier.padding(vertical=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
        Text("账号",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=8.dp))
        SettingRow("重置密码","离线表单流程，不会修改真实密码") {onRoute(ScreenRoutes.FORGOT)}
        SettingRow("页面状态预览","加载、空、错误与恢复") {onRoute(ScreenRoutes.STATES)}
        if(state.signedIn)OutlinedButton(onClick={confirmation="logout"},modifier=Modifier.fillMaxWidth().padding(top=20.dp).heightIn(min=52.dp)) {Text("退出演示身份")}
        else Button(onClick={onRoute(Destination.PROFILE.route)},modifier=Modifier.fillMaxWidth().padding(top=20.dp).heightIn(min=52.dp)) {Text("进入登录页")}
        Spacer(Modifier.height(32.dp))
    }
    when(confirmation) {
        "enable"->ConfirmationSheet("开启本地修改记录？","本轮只演示内置文章增删和 Diff，不采集真实保存历史。关闭开关不会删除已有示例。",onConfirm={model.snapshots(true);confirmation=null},onDismiss={confirmation=null})
        "clear"->ConfirmationSheet("清除本地记录？","只清除本设备的演示快照显示状态；每日汇总仍保留，不影响真实账号或服务器。",confirm="清除",onConfirm={model.clearSnapshots();confirmation=null},onDismiss={confirmation=null})
        "logout"->ConfirmationSheet("退出演示身份？","回到未登录页面。不会调用服务器，也不会删除本机资料偏好。",confirm="退出",onConfirm={confirmation=null;model.signOut();onSignOut()},onDismiss={confirmation=null})
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
            val small=Bitmap.createScaledBitmap(snapshot.asAndroidBitmap(),(snapshot.width/24).coerceAtLeast(1),
                (snapshot.height/24).coerceAtLeast(1),true)
            val result=Bitmap.createScaledBitmap(small,snapshot.width,snapshot.height,true).asImageBitmap()
            small.recycle();result
        }
    }
    BackHandler(onBack=onBack)
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("navigation-position-screen")) {
        snapshot?.let {Image(it,null,Modifier.fillMaxSize().testTag("position-home-snapshot"),contentScale=ContentScale.FillBounds)}
        val bottomInset=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val panelHeight=320.dp+bottomInset
        val nativeBlur=Build.VERSION.SDK_INT>=31
        (if(nativeBlur)snapshot else softened)?.let {image->
            Box(Modifier.fillMaxSize().drawWithContent {
                clipRect(top=(size.height-panelHeight.toPx()).coerceAtLeast(0f)){this@drawWithContent.drawContent()}
            }) {Image(image,null,Modifier.fillMaxSize().then(if(nativeBlur)Modifier.blur(18.dp) else Modifier),contentScale=ContentScale.FillBounds)}
        }
        val background=MaterialTheme.colorScheme.background
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelHeight)
            .background(Brush.verticalGradient(listOf(background.copy(alpha=.2f),background.copy(alpha=.82f))))
            .padding(horizontal=24.dp,vertical=16.dp)) {
            Text("调整导航位置",style=MaterialTheme.typography.titleLarge)
            Text("拖动主圆，放到顺手的位置",style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp,bottom=12.dp))
            Button(onClick={onSave(NavigationPlacement(x,y))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("保存位置")}
        }
        val travelX=with(density){(maxWidth-96.dp).coerceAtLeast(0.dp).toPx()}
        val travelY=with(density){140.dp.toPx()}
        val circle=Destination.HOME.circleColors
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
