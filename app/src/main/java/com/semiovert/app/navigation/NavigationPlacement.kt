package com.semiovert.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.semiovert.app.core.model.NavigationPlacement
import com.semiovert.app.core.model.NavigationShortcut

val LocalNavigationPlacement=staticCompositionLocalOf {NavigationPlacement()}
class ShortcutActions(val items: List<NavigationShortcut>,val save: (NavigationShortcut)->Unit,val remove: (String)->Unit)
val LocalShortcuts=staticCompositionLocalOf {ShortcutActions(emptyList(),{},{})}
