package com.semiovert.app.navigation

import androidx.compose.animation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.lifecycle.Lifecycle
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.WaveDeckOverlayHost
import kotlinx.coroutines.flow.first
import com.semiovert.app.feature.home.HomeScreen
import com.semiovert.app.feature.category.CategoryScreen
import com.semiovert.app.feature.search.SearchScreen
import com.semiovert.app.feature.reader.ReaderScreen
import com.semiovert.app.feature.reader.ArticleExpansionHost
import com.semiovert.app.feature.profile.*
import com.semiovert.app.feature.account.*
import com.semiovert.app.feature.settings.*
import com.semiovert.app.feature.records.RecordsScreen
import com.semiovert.app.feature.states.ContentStatesScreen

@Composable
private fun RouteSurface(edgeToEdge: Boolean=false,modifier: Modifier=Modifier,content: @Composable ()->Unit) {
    Surface(modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().then(if(edgeToEdge)Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)))) {content()}
    }
}

private fun NavGraphBuilder.secondary(route: String,arguments: List<NamedNavArgument> = emptyList(),edgeToEdge: Boolean=false,content: @Composable (NavBackStackEntry)->Unit) {
    composable(route,arguments=arguments) {entry ->RouteSurface(edgeToEdge) {content(entry)}}
}

@Composable
fun AppNavigation(state: PreviewState,model: PreviewViewModel,onStatusBarAppearance: (Boolean)->Unit) {
    val controller=rememberNavController()
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val motion=LocalMotionEnabled.current
    var homeEntry by rememberSaveable {mutableIntStateOf(0)}
    var profileEntry by remember {mutableIntStateOf(0)}
    var handoff by remember {mutableStateOf<PageHandoff?>(null)}
    var navigating by remember {mutableStateOf(false)}
    val entry by controller.currentBackStackEntryAsState()
    LaunchedEffect(entry) {
        navigating=true
        entry?.lifecycle?.currentStateFlow?.first {it==Lifecycle.State.RESUMED}
        navigating=false
    }
    val lightStatus=MaterialTheme.colorScheme.surface.luminance()>.5f
    SideEffect {onStatusBarAppearance(lightStatus)}
    val topRoutes=remember {Destination.entries.map {it.route}.toSet()}
    val navigate: (Destination)->Unit={target ->
        if(controller.currentDestination?.route!=target.route&&!navigating) {
            focus.clearFocus();keyboard?.hide()
            navigating=true
            handoff=PageHandoff.between(controller.currentDestination?.route,target.route)
            if(target==Destination.HOME)homeEntry++
            if(target==Destination.PROFILE) {
                profileEntry++
            }
            controller.navigate(target.route) {popUpTo(controller.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}
        }
    }
    fun changeRoute(action: ()->Unit) {
        if(!navigating) {
            focus.clearFocus();keyboard?.hide()
            handoff=null
            val previous=controller.currentBackStackEntry
            navigating=true
            action()
            if(previous==controller.currentBackStackEntry)navigating=false
        }
    }
    val route: (String)->Unit={destination ->changeRoute {controller.navigate(destination){launchSingleTop=true}}}
    val back: ()->Unit={changeRoute {controller.popBackStack()}}
    val read: (String)->Unit={route("reader/"+it)}
    val author: (String)->Unit={route("author/"+it)}
    val profile: ()->Unit={changeRoute {
        if(!controller.popBackStack(Destination.PROFILE.route,false))controller.navigate(Destination.PROFILE.route) {
            popUpTo(controller.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true
        }
    }}
    val enterDemo: (String?)->Unit={name ->if(name!=null)model.profile(state.profile.copy(name=name));model.signIn();profile()}
    val login: ()->Unit={changeRoute {if(!controller.popBackStack(ScreenRoutes.LOGIN,false))controller.navigate(ScreenRoutes.LOGIN) {popUpTo(ScreenRoutes.RESET_SUCCESS){inclusive=true};launchSingleTop=true}}}
    BackHandler(!navigating&&entry?.destination?.route in topRoutes&&entry?.destination?.route!=Destination.HOME.route) {navigate(Destination.HOME)}
    CompositionLocalProvider(LocalNavigationPlacement provides state.placement,
        LocalHomeEntry provides homeEntry,
        LocalShortcuts provides ShortcutActions(state.shortcuts,model::shortcut,model::removeShortcut)) {
        ArticleExpansionHost {
        IdentityTransitionHost {
        WaveDeckOverlayHost {
        NavHost(navController=controller,startDestination=Destination.HOME.route,modifier=Modifier.fillMaxSize().pointerInput(navigating) {
            if(navigating)awaitPointerEventScope {while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}}
        }, enterTransition={pageEnter(motion)},exitTransition={pageExit(motion)},
            popEnterTransition={pageEnter(motion,pop=true)},popExitTransition={pageExit(motion,pop=true)}) {
            composable(Destination.HOME.route) {SceneMotionContent(Destination.HOME,handoff) {RouteSurface(edgeToEdge=true) {HomeScreen(navigate,read)}}}
            composable(Destination.CATEGORY.route) {SceneMotionContent(Destination.CATEGORY,handoff) {RouteSurface(edgeToEdge=true) {CategoryScreen(navigate,read)}}}
            composable(Destination.SEARCH.route) {SceneMotionContent(Destination.SEARCH,handoff) {RouteSurface(edgeToEdge=true) {SearchScreen(navigate,read,author)}}}
            composable(Destination.PROFILE.route) {SceneMotionContent(Destination.PROFILE,handoff) {RouteSurface(edgeToEdge=true) {
                ProfileScreen(state,model::nextTheme,navigate,route,read,profileEntry)
            }}}
            composable("reader/{articleId}",arguments=listOf(navArgument("articleId"){type=NavType.StringType}),
                enterTransition={EnterTransition.None},exitTransition={ExitTransition.None},popEnterTransition={EnterTransition.None},popExitTransition={ExitTransition.None}) {
                RouteSurface(edgeToEdge=true){ReaderScreen(back,author)}
            }
            secondary(ScreenRoutes.AUTHOR,listOf(navArgument("authorId"){type=NavType.StringType}),edgeToEdge=true) {entry ->AuthorScreen(entry.arguments?.getString("authorId").orEmpty(),back,read)}
            secondary(ScreenRoutes.LOGIN) {AccountScreen(false,back,{changeRoute {controller.navigate(ScreenRoutes.REGISTER){popUpTo(ScreenRoutes.LOGIN){inclusive=true}}}},{route(ScreenRoutes.FORGOT)},enterDemo)}
            secondary(ScreenRoutes.REGISTER) {AccountScreen(true,back,{changeRoute {controller.navigate(ScreenRoutes.LOGIN){popUpTo(ScreenRoutes.REGISTER){inclusive=true}}}},{route(ScreenRoutes.FORGOT)},enterDemo)}
            secondary(ScreenRoutes.FORGOT) {ForgotPasswordScreen(back,{route(ScreenRoutes.RESET)})}
            secondary(ScreenRoutes.RESET) {ResetPasswordScreen(back,{changeRoute {controller.navigate(ScreenRoutes.RESET_SUCCESS){popUpTo(ScreenRoutes.FORGOT){inclusive=true}}}})}
            secondary(ScreenRoutes.RESET_SUCCESS) {ResetSuccessScreen(login)}
            secondary(ScreenRoutes.SETTINGS) {SettingsScreen(state,model,back,route,profile)}
            secondary(ScreenRoutes.EDIT_PROFILE) {EditProfileScreen(state.profile,back,{model.profile(it);back()})}
            secondary(ScreenRoutes.NAV_POSITION) {NavigationPositionScreen(state.placement,back,{model.placement(it);back()})}
            secondary(ScreenRoutes.RECORDS) {RecordsScreen(state,back)}
            secondary(ScreenRoutes.STATES) {ContentStatesScreen(back)}
        }
        }
        }
        }
    }
    // Finish the handoff instead of snapping both scenes back to their resting positions.
    BackHandler(navigating) {}
}
