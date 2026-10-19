package com.semiovert.app.core.data

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.semiovert.app.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Local preferences for demonstration UI only; no passwords, email, tokens or article snapshots. */
class PreviewViewModel(application: Application): AndroidViewModel(application) {
    private val preferences=application.getSharedPreferences("offline_preview",Context.MODE_PRIVATE)
    private val mutable=MutableStateFlow(read())
    val state=mutable.asStateFlow()
    private fun read()=PreviewState(
        signedIn=preferences.getBoolean("demo_signed_in",false),
        profile=PreviewProfile(preferences.getString("name",null)?:"林间来信",preferences.getString("handle",null)?:"offline_reader",preferences.getString("bio",null)?:"记录散步、天气与生活中的小事。",preferences.getInt("avatar",0),Cover.entries.find {it.name==preferences.getString("cover",null)}),
        theme=ThemeMode.entries.find {it.name==preferences.getString("theme",null)}?:ThemeMode.SYSTEM,
        snapshots=preferences.getBoolean("snapshot_preview",false),snapshotsCleared=preferences.getBoolean("snapshot_examples_cleared",false),
        placement=NavigationPlacement(preferences.getFloat("nav_x",1f).coerceIn(0f,1f),preferences.getFloat("nav_y",0f).coerceIn(0f,1f)),
        shortcuts=runCatching {val data=JSONArray(preferences.getString("nav_shortcuts","[]"));(0 until data.length()).map {val item=data.getJSONObject(it);NavigationShortcut(item.getString("route"),item.getDouble("x").toFloat().coerceIn(0f,1f),item.getDouble("y").toFloat().coerceIn(0f,1f))}}.getOrDefault(emptyList()),
    )
    private fun update(next: PreviewState) {
        preferences.edit().putBoolean("demo_signed_in",next.signedIn).putString("name",next.profile.name).putString("handle",next.profile.handle)
            .putString("bio",next.profile.bio).putInt("avatar",next.profile.avatar).putString("cover",next.profile.cover?.name)
            .putString("theme",next.theme.name).putBoolean("snapshot_preview",next.snapshots).putBoolean("snapshot_examples_cleared",next.snapshotsCleared)
            .putFloat("nav_x",next.placement.x).putFloat("nav_y",next.placement.y)
            .putString("nav_shortcuts",JSONArray().apply {next.shortcuts.forEach {put(JSONObject().put("route",it.route).put("x",it.x).put("y",it.y))}}.toString()).apply()
        mutable.value=next
    }
    fun signIn()=update(state.value.copy(signedIn=true))
    fun signOut()=update(state.value.copy(signedIn=false))
    fun profile(value: PreviewProfile)=update(state.value.copy(profile=value))
    fun theme(value: ThemeMode)=update(state.value.copy(theme=value))
    fun nextTheme()=theme(state.value.theme.next())
    fun snapshots(value: Boolean)=update(state.value.copy(snapshots=value))
    fun clearSnapshots()=update(state.value.copy(snapshotsCleared=true))
    fun restoreExamples()=update(state.value.copy(snapshotsCleared=false))
    fun placement(value: NavigationPlacement)=update(state.value.copy(placement=NavigationPlacement(value.x.coerceIn(0f,1f),value.y.coerceIn(0f,1f))))
    fun shortcut(value: NavigationShortcut)=update(state.value.copy(shortcuts=state.value.shortcuts.filterNot {it.route==value.route}+value.copy(x=value.x.coerceIn(0f,1f),y=value.y.coerceIn(0f,1f))))
    fun removeShortcut(route: String)=update(state.value.copy(shortcuts=state.value.shortcuts.filterNot {it.route==route}))
}
