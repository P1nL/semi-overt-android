package com.semiovert.app.core.designsystem

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.model.*

@Composable
fun PageFrame(title: String,onBack: ()->Unit,tag: String,scroll: Boolean=true,actions: (@Composable ()->Unit)?=null,content: @Composable ColumnScope.()->Unit) {
    Box(Modifier.fillMaxSize().imePadding().navigationBarsPadding().testTag(tag)) {
        val body=Modifier.fillMaxSize().then(if(scroll)Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(start=24.dp,end=24.dp,top=64.dp)
        Column(body,content=content)
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().heightIn(min=64.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            val backSpark=rememberSparkControl()
            IconButton(onClick=onBack,interactionSource=backSpark.source,modifier=backSpark.modifier.semantics {contentDescription="返回"}) {SoIcon(SoSymbol.ARROW,modifier=Modifier.graphicsLayer {rotationZ=180f})}
            Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f).semantics {heading()})
            actions?.invoke()
        }
    }
}

@Composable
fun DemoNotice(text: String="离线演示，不连接真实账号。请勿输入真实密码。") {
    Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.fillMaxWidth().padding(top=8.dp,bottom=20.dp))
}

@Composable
fun ProfileAvatar(variant: Int=0,size: Int=56,backgroundColor: Color=MaterialTheme.colorScheme.secondary,
    contentColor: Color=MaterialTheme.colorScheme.onSecondary) {
    Surface(shape=CircleShape,color=backgroundColor,modifier=Modifier.size(size.dp)) {
        // Shared-element bounds change every frame; scale the symbol with those bounds, not the final size argument.
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {SoIcon(when(variant%3) {1->SoSymbol.BOOK;2->SoSymbol.HOME;else->SoSymbol.PERSON},modifier=Modifier.fillMaxSize(.48f),tint=contentColor)}
    }
}

@Composable
fun SettingRow(label: String,detail: String?=null,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp).clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {Text(label,style=MaterialTheme.typography.bodyMedium);if(detail!=null)Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        SoIcon(SoSymbol.ARROW,modifier=Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(title: String,body: String,confirm: String="确定",onConfirm: ()->Unit,onDismiss: ()->Unit) {
    SoBottomSheet(onDismissRequest=onDismiss) {close ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
            Text(title,style=MaterialTheme.typography.headlineSmall)
            Text(body,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=16.dp,bottom=24.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                val confirmSpark=rememberSparkControl();val cancelSpark=rememberSparkControl()
                Button(onClick={close(onConfirm)},interactionSource=confirmSpark.source,modifier=Modifier.weight(1f).heightIn(min=48.dp).then(confirmSpark.modifier)) {Text(confirm)}
                OutlinedButton(onClick={close {}},interactionSource=cancelSpark.source,modifier=Modifier.weight(1f).heightIn(min=48.dp).then(cancelSpark.modifier)) {Text("取消")}
            }
        }
    }
}
