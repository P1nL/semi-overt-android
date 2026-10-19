package com.semiovert.app.feature.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*

@Composable
fun EditProfileScreen(initial: PreviewProfile,onBack: ()->Unit,onSave: (PreviewProfile)->Unit) {
    var name by rememberSaveable {mutableStateOf(initial.name)}
    var handle by rememberSaveable {mutableStateOf(initial.handle)}
    var bio by rememberSaveable {mutableStateOf(initial.bio)}
    var avatar by rememberSaveable {mutableIntStateOf(initial.avatar)}
    var coverName by rememberSaveable {mutableStateOf(initial.cover?.name?:"none")}
    var attempted by rememberSaveable {mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)}
    val cover=Cover.entries.find {it.name==coverName}
    val draft=PreviewProfile(name.trim(),handle.trim(),bio.trim(),avatar,cover)
    val dirty=draft!=initial
    val back: ()->Unit={if(dirty)discard=true else onBack()}
    BackHandler(dirty) {discard=true}
    PageFrame("编辑资料",back,"edit-profile-screen") {
        DemoNotice("资料只保存在本机演示偏好中；头像和封面使用内置图形，不会上传图片。")
        ProfileAvatar(avatar,size=72)
        Text("更换头像",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=16.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("人物","书页","小屋").forEachIndexed {index,label ->FilterChip(selected=avatar==index,onClick={avatar=index},label={Text(label)})}}
        Text("设置封面",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=20.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("none" to "无封面","FOREST" to "林间","ARCHITECTURE" to "街道").forEach {option ->FilterChip(selected=coverName==option.first,onClick={coverName=option.first},label={Text(option.second)})}}
        cover?.let {SampleCover(it,Modifier.fillMaxWidth().padding(top=12.dp).height(120.dp).clip(RoundedCornerShape(20.dp)))}
        OutlinedTextField(name,{name=it},label={Text("昵称")},singleLine=true,isError=attempted&&PreviewValidation.name(name)!=null,
            supportingText=if(attempted&&PreviewValidation.name(name)!=null){{Text(PreviewValidation.name(name)!!)}} else null,modifier=Modifier.fillMaxWidth().padding(top=24.dp).animateContentSize(tween(motionMillis(180),easing=SoEase)).testTag("edit-name"))
        OutlinedTextField(handle,{handle=it},label={Text("账号")},singleLine=true,isError=attempted&&PreviewValidation.handle(handle)!=null,
            supportingText=if(attempted&&PreviewValidation.handle(handle)!=null){{Text(PreviewValidation.handle(handle)!!)}} else null,modifier=Modifier.fillMaxWidth().padding(top=12.dp).animateContentSize(tween(motionMillis(180),easing=SoEase)).testTag("edit-handle"))
        OutlinedTextField(bio,{bio=it},label={Text("简介")},isError=bio.length>200,supportingText={Text(bio.length.toString()+" / 200")},minLines=3,modifier=Modifier.fillMaxWidth().padding(top=12.dp).testTag("edit-bio"))
        Button(onClick={attempted=true;if(PreviewValidation.name(name)==null&&PreviewValidation.handle(handle)==null&&bio.length<=200)onSave(draft)},modifier=Modifier.fillMaxWidth().padding(top=24.dp).heightIn(min=52.dp)) {Text("保存资料")}
        Spacer(Modifier.height(32.dp))
    }
    if(discard)ConfirmationSheet("放弃本次修改？","未保存的资料修改将被丢弃。",confirm="放弃",onConfirm={discard=false;onBack()},onDismiss={discard=false})
}
