package com.semiovert.app.core.model

import java.time.LocalDate

/** Non-authenticating offline preview state. Never contains credentials. */
data class NavigationPlacement(val x: Float=1f,val y: Float=0f)
data class NavigationShortcut(val route: String,val x: Float,val y: Float)
data class PreviewProfile(val name: String="林间来信",val handle: String="offline_reader",val bio: String="记录散步、天气与生活中的小事。",val avatar: Int=0,val cover: Cover?=null)
data class PreviewState(val signedIn: Boolean=false,val profile: PreviewProfile=PreviewProfile(),val theme: ThemeMode=ThemeMode.SYSTEM,val snapshots: Boolean=false,val snapshotsCleared: Boolean=false,val placement: NavigationPlacement=NavigationPlacement(),val shortcuts: List<NavigationShortcut> = emptyList())
data class DemoRecord(val articleId: String,val date: LocalDate,val summaryWords: Int,val before: String,val after: String) {
    val added get()=after.length
    val removed get()=before.length
}

object PreviewValidation {
    fun email(value: String): String?=if(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(value.trim())) null else "请输入有效的示例邮箱"
    fun password(value: String): String?=if(value.length>=8) null else "演示密码至少 8 位"
    fun name(value: String): String?=if(value.trim().length in 2..24) null else "昵称需为 2–24 个字"
    fun handle(value: String): String?=if(Regex("[A-Za-z0-9_]{3,24}").matches(value)) null else "账号需为 3–24 位字母、数字或下划线"
}
