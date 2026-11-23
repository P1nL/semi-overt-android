package com.semiovert.app.feature.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.data.ProfileImages
import com.semiovert.app.core.data.userMessage
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*
import com.semiovert.app.navigation.SwipeBackPage
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun EditProfileScreen(initial: PreviewProfile,onBack: ()->Unit,onSave: (PreviewProfile)->Unit,
    persist: (suspend (PreviewProfile) -> Unit)? = null) {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var name by rememberSaveable {mutableStateOf(initial.name)}
    var handle by rememberSaveable {mutableStateOf(initial.handle)}
    var bio by rememberSaveable {mutableStateOf(initial.bio)}
    var avatarPhoto by rememberSaveable {mutableStateOf(initial.avatarPhoto)}
    var coverPhoto by rememberSaveable {mutableStateOf(initial.coverPhoto)}
    var coverName by rememberSaveable {mutableStateOf(initial.cover?.name?:"none")}
    var coverPosition by rememberSaveable {mutableFloatStateOf(initial.coverPosition)}
    var cropSource by rememberSaveable {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    var imageError by remember {mutableStateOf(false)}
    var cropError by remember {mutableStateOf(false)}
    var attempted by rememberSaveable {mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)}
    var saveError by remember {mutableStateOf<String?>(null)}
    val bioLimit = if(persist==null)200 else 50
    val nameError = if(persist==null)PreviewValidation.name(name)
        else if(name.isBlank() || name.trim().length>60) "昵称需为 1–60 个字符" else null
    val handleError = if(persist==null)PreviewValidation.handle(handle) else null
    val cover=Cover.entries.find {it.name==coverName}
    val draft=initial.copy(name=name.trim(),handle=handle.trim(),bio=bio.trim(),cover=cover,
        avatarPhoto=avatarPhoto,coverPhoto=coverPhoto,coverPosition=coverPosition)
    val dirty=draft!=initial
    val avatar=rememberProfilePhoto(avatarPhoto).bitmap
    val banner=rememberProfileCover(coverPhoto,cover)
    val source=rememberProfilePhoto(cropSource)
    val motion=LocalMotionEnabled.current
    val avatarScale=remember {Animatable(1f)}
    LaunchedEffect(avatar,avatarPhoto,motion) {
        if(avatar!=null&&avatarPhoto!=initial.avatarPhoto&&motion) {
            avatarScale.snapTo(.86f)
            avatarScale.animateTo(1f,spring(dampingRatio=.72f,stiffness=600f))
        }else avatarScale.snapTo(1f)
    }

    fun deleteDraft(photo: String?, kept: String?) {
        if(photo!=kept)ProfileImages.delete(context,photo)
    }
    fun closeCrop() {
        ProfileImages.delete(context,cropSource)
        cropSource=null
        cropError=false
    }
    val back: ()->Unit={
        if(!busy)when {
            cropSource!=null->closeCrop()
            dirty->discard=true
            else->onBack()
        }
    }
    fun importPhoto(uri: Uri?,applyPhoto: (String)->Unit) {
        if(uri==null)return
        busy=true
        imageError=false
        scope.launch {
            try {
                val photo=ProfileImages.importPhoto(context,uri)
                if(photo==null)imageError=true else applyPhoto(photo)
            } finally {busy=false}
        }
    }
    val avatarPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->
        importPhoto(uri) {photo->closeCrop();cropSource=photo}
    }
    val coverPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->
        importPhoto(uri) {photo->
            deleteDraft(coverPhoto,initial.coverPhoto)
            coverPhoto=photo
            coverName="none"
            coverPosition=.5f
        }
    }
    val selectImage=PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val canPick=!busy&&cropSource==null
    LaunchedEffect(cropSource,source) {
        if(cropSource!=null&&source.loaded&&source.bitmap==null) {closeCrop();imageError=true}
    }
    BackHandler(dirty||busy||cropSource!=null) {back()}
    SwipeBackPage(canPick,back) {
    PageFrame("编辑资料",back,"edit-profile-screen") {
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.graphicsLayer {scaleX=avatarScale.value;scaleY=avatarScale.value}.testTag("edit-avatar-preview")) {
                ProfileAvatar(initial.avatar,size=72,photo=avatar)
            }
            TextButton(onClick={avatarPicker.launch(selectImage)},enabled=canPick) {Text("更换头像")}
        }
        ProfileCoverFrame(banner,hasCover=coverPhoto!=null||cover!=null,position=coverPosition,enabled=canPick,
            onPick={coverPicker.launch(selectImage)},onMove={delta->
                val next=(coverPosition+delta).coerceIn(0f,1f)
                if(next==coverPosition)false else {coverPosition=next;true}
            },onRemove={
                deleteDraft(coverPhoto,initial.coverPhoto)
                coverPhoto=null
                coverName="none"
                coverPosition=.5f
            })
        if(busy||cropSource!=null&&!source.loaded)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(imageError)Text("无法读取图片",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        StitchedTextField(name,{if(!busy)name=it},label={Text("昵称")},singleLine=true,isError=attempted&&nameError!=null,
            supportingText=if(attempted&&nameError!=null){{Text(nameError.withInterfaceDigits())}} else null,modifier=Modifier.fillMaxWidth().padding(top=24.dp).testTag("edit-name"))
        if (persist == null) StitchedTextField(handle,{handle=it},label={Text("账号")},singleLine=true,isError=attempted&&handleError!=null,
            supportingText=if(attempted&&handleError!=null){{Text(handleError.withInterfaceDigits())}} else null,modifier=Modifier.fillMaxWidth().padding(top=12.dp).testTag("edit-handle"))
        else Text("账号  @${initial.handle}", Modifier.padding(top=16.dp), style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        StitchedTextField(bio,{if(!busy)bio=it},label={Text("简介")},isError=bio.length>bioLimit,
            supportingText={Text(("${bio.length} / $bioLimit").withInterfaceDigits())},
            minLines=3,modifier=Modifier.fillMaxWidth().padding(top=12.dp).testTag("edit-bio"))
        saveError?.let { Text(it, Modifier.padding(top=12.dp).testTag("profile-save-error"), color=MaterialTheme.colorScheme.error) }
        Button(onClick={
            attempted=true
            if(nameError==null&&handleError==null&&bio.length<=bioLimit) {
                if (persist == null) onSave(draft) else {
                    busy = true
                    saveError = null
                    scope.launch {
                        try { persist(draft) }
                        catch(error: CancellationException) { throw error }
                        catch(error: Exception) { saveError=error.userMessage() }
                        finally { busy=false }
                    }
                }
            }
        },enabled=canPick,modifier=Modifier.fillMaxWidth().padding(top=24.dp).heightIn(min=52.dp)) {Text("保存资料")}
        Spacer(Modifier.height(32.dp))
    }
    }
    source.bitmap?.let {image->key(cropSource) {
        AvatarCropDialog(image,busy,cropError,onCancel=::closeCrop,onConfirm={crop->
            busy=true
            cropError=false
            scope.launch {
                try {
                    val photo=ProfileImages.save(context) {AvatarCropGeometry(image.width,image.height).render(image.asAndroidBitmap(),crop)}
                    if(photo==null)cropError=true else {
                        deleteDraft(avatarPhoto,initial.avatarPhoto)
                        avatarPhoto=photo
                        closeCrop()
                    }
                } finally {busy=false}
            }
        })
    }}
    if(discard)ConfirmationSheet("放弃本次修改？","未保存的资料修改将被丢弃。",confirm="放弃",onConfirm={
        discard=false
        deleteDraft(avatarPhoto,initial.avatarPhoto)
        deleteDraft(coverPhoto,initial.coverPhoto)
        closeCrop()
        onBack()
    },onDismiss={discard=false})
}
