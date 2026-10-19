package com.semiovert.app.feature.profile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*
import com.semiovert.app.feature.records.RecordCalendar
import com.semiovert.app.navigation.*

@Composable
fun ProfileScreen(state: PreviewState,onTheme: ()->Unit,onNavigate: (Destination)->Unit,onRoute: (String)->Unit,onArticle: (String)->Unit) {
    val headerInk=if(state.signedIn&&state.profile.cover!=null)SampleCoverInk else MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxSize()) {
        if(state.signedIn)state.profile.cover?.let {SampleCover(it,Modifier.fillMaxWidth().height(260.dp))}
        BrowseScene(Destination.PROFILE,"我的",onNavigate,header={openMenu ->
            Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min=64.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=openMenu,modifier=Modifier.semantics {contentDescription="打开页面导航"}) {Text("我的",style=MaterialTheme.typography.labelLarge,color=headerInk)}
                Spacer(Modifier.weight(1f))
                IconButton(onClick={onRoute(ScreenRoutes.SETTINGS)},modifier=Modifier.semantics {contentDescription="设置"}) {SoIcon(SoSymbol.SETTINGS,tint=headerInk)}
            }
        }) {
            if(!state.signedIn) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start=24.dp,end=24.dp,top=48.dp,bottom=128.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                ProfileAvatar(size=80)
                Text("尚未登录",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=20.dp))
                Text("先读一篇，再慢慢认识这里。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
                Button(onClick={onRoute(ScreenRoutes.LOGIN)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("登录")}
                FilledTonalButton(onClick={onRoute(ScreenRoutes.REGISTER)},modifier=Modifier.fillMaxWidth().padding(top=10.dp).heightIn(min=52.dp)) {Text("创建账号")}
                HorizontalDivider(Modifier.padding(top=38.dp,bottom=12.dp),color=MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick=onTheme,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                    SoIcon(SoSymbol.APPEARANCE)
                    Text("外观",Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface)
                    Text(state.theme.label,style=MaterialTheme.typography.bodyMedium)
                }
                Text("离线体验",style=MaterialTheme.typography.titleMedium,modifier=Modifier.fillMaxWidth().padding(top=26.dp))
                Text("文章、作者、账号表单和记录均为本地演示。不会登录真实账号或上传输入。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.fillMaxWidth().padding(top=8.dp))
            } else ProfileFeed(state.profile,"lin",true,state.snapshots,state.snapshotsCleared,onArticle,{onRoute(ScreenRoutes.EDIT_PROFILE)})
        }
    }
}

@Composable
private fun ProfileFeed(profile: PreviewProfile,authorId: String,owner: Boolean,snapshots: Boolean,cleared: Boolean,onArticle: (String)->Unit,onEdit: ()->Unit={}) {
    val articles=SampleLibrary.articles.filter {it.author.id==authorId}
    LazyColumn(Modifier.fillMaxSize().testTag(if(owner)"my-profile-feed" else "author-profile-feed"),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=128.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item("identity") {
            Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.secondaryContainer,
                modifier=Modifier.fillMaxWidth().then(if(owner)Modifier.clickable(onClickLabel="编辑资料",onClick=onEdit).testTag("profile-identity") else Modifier)) {
                Column(Modifier.padding(20.dp)) {
                    ProfileAvatar(profile.avatar)
                    Text(profile.name,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=16.dp))
                    Text("@"+profile.handle,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=4.dp),color=MaterialTheme.colorScheme.onSecondaryContainer)
                    if(profile.bio.isNotBlank())Text(profile.bio,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=12.dp))
                    Text(if(owner)"演示身份 · 点击身份区编辑" else "本地示例作者 · 只读主页",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=12.dp))
                }
            }
        }
        item("records") {RecordCalendar(authorId,owner,snapshots,cleared)}
        item("articles-heading") {Text("公开文章 · "+articles.size+"（示例）",style=MaterialTheme.typography.labelLarge)}
        items(articles,key={it.id}) {ArticleCard(it,onArticle)}
    }
}

@Composable
fun AuthorScreen(authorId: String,onBack: ()->Unit,onArticle: (String)->Unit) {
    val author=SampleLibrary.authors.find {it.id==authorId}
    if(author==null) {PageFrame("作者主页",onBack,"author-missing-screen") {Text("没有找到这位示例作者",style=MaterialTheme.typography.titleMedium)};return}
    val cover=if(author.id=="zhou")Cover.ARCHITECTURE else null
    Box(Modifier.fillMaxSize().testTag("author-screen")) {
        cover?.let {SampleCover(it,Modifier.fillMaxWidth().height(260.dp))}
        Column(Modifier.fillMaxSize()) {
            Text("作者主页",style=MaterialTheme.typography.labelLarge,color=if(cover!=null)SampleCoverInk else MaterialTheme.colorScheme.onSurface,modifier=Modifier.statusBarsPadding().padding(horizontal=24.dp,vertical=24.dp))
            ProfileFeed(PreviewProfile(author.name,"sample_"+author.id,author.bio,cover=cover),author.id,false,false,false,onArticle)
        }
    }
}
