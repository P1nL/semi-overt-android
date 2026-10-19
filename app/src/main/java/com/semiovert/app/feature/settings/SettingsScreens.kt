package com.semiovert.app.feature.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*
import com.semiovert.app.navigation.ScreenRoutes
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(state: PreviewState,model: PreviewViewModel,onBack: ()->Unit,onRoute: (String)->Unit,onSignOut: ()->Unit) {
    var confirmation by remember {mutableStateOf<String?>(null)}
    PageFrame("设置",onBack,"settings-screen") {
        DemoNotice("离线页面与本机偏好；账号和真实文章保存尚未接入。")
        Text("外观",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top=12.dp,bottom=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach {mode ->FilterChip(selected=state.theme==mode,onClick={model.theme(mode)},label={Text(mode.label)})}
        }
        SettingRow("调整导航位置","在底部安全区域移动主圆") {onRoute(ScreenRoutes.NAV_POSITION)}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text("本地修改记录",style=MaterialTheme.typography.bodyMedium);Text(if(state.signedIn)"仅预览内置快照，不采集真实修改" else "进入演示身份后可体验",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Switch(checked=state.snapshots,onCheckedChange={if(it)confirmation="enable" else model.snapshots(false)},enabled=state.signedIn,modifier=Modifier.semantics {contentDescription="本地修改记录开关"})
        }
        SettingRow("查看记录","月历、日期详情与版本示例") {onRoute(if(state.signedIn)ScreenRoutes.RECORDS else ScreenRoutes.LOGIN)}
        SettingRow("清除本地记录","只清除本机演示快照，不影响汇总") {confirmation="clear"}
        if(state.snapshotsCleared)TextButton(onClick=model::restoreExamples) {Text("恢复内置示例快照")}
        HorizontalDivider(Modifier.padding(vertical=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
        Text("账号",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=8.dp))
        SettingRow("重置密码","离线表单流程，不会修改真实密码") {onRoute(ScreenRoutes.FORGOT)}
        SettingRow("页面状态预览","加载、空、错误与恢复") {onRoute(ScreenRoutes.STATES)}
        if(state.signedIn)OutlinedButton(onClick={confirmation="logout"},modifier=Modifier.fillMaxWidth().padding(top=20.dp).heightIn(min=52.dp)) {Text("退出演示身份")}
        else Button(onClick={onRoute(ScreenRoutes.LOGIN)},modifier=Modifier.fillMaxWidth().padding(top=20.dp).heightIn(min=52.dp)) {Text("进入登录页")}
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
    PageFrame("调整导航位置",onBack,"navigation-position-screen") {
        Text("让主圆靠近顺手的位置",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=24.dp))
        DemoNotice("拖动下方的主圆，或使用位置按钮。保存后应用到各一级页面。")
        BoxWithConstraints(Modifier.fillMaxWidth().height(216.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
            val maxX=with(density){(maxWidth-56.dp).toPx()}.coerceAtLeast(1f)
            val maxY=with(density){(maxHeight-56.dp).toPx()}.coerceAtLeast(1f)
            Text("底部安全区域",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.Center))
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.offset {IntOffset((x*maxX).roundToInt(),((1-y)*maxY).roundToInt())}.size(56.dp).testTag("position-preview")
                .pointerInput(maxX,maxY) {detectDragGestures {change,amount ->change.consume();x=(x+amount.x/maxX).coerceIn(0f,1f);y=(y-amount.y/maxY).coerceIn(0f,1f)}}) {
                Box(contentAlignment=Alignment.Center) {SoIcon(SoSymbol.HOME,tint=MaterialTheme.colorScheme.onPrimaryContainer)}
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={x=0f;y=0f}) {Text("左下")}
            TextButton(onClick={x=.5f;y=0f}) {Text("居中")}
            TextButton(onClick={x=1f;y=0f}) {Text("右下")}
        }
        Button(onClick={onSave(NavigationPlacement(x,y))},modifier=Modifier.fillMaxWidth().padding(top=24.dp).heightIn(min=52.dp)) {Text("保存位置")}
        TextButton(onClick={x=1f;y=0f},modifier=Modifier.fillMaxWidth()) {Text("重置为右下角")}
        TextButton(onClick=onBack,modifier=Modifier.fillMaxWidth()) {Text("取消")}
    }
}
