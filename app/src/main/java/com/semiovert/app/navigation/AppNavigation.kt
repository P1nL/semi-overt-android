package com.semiovert.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.designsystem.*
import kotlinx.coroutines.*
import com.semiovert.app.feature.home.HomeScreen
import com.semiovert.app.feature.category.CategoryScreen
import com.semiovert.app.feature.search.SearchScreen
import com.semiovert.app.feature.reader.ReaderScreen
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

private fun NavGraphBuilder.secondary(route: String,arguments: List<NamedNavArgument> = emptyList(),edgeToEdge: Boolean=false,duration: Int=320,content: @Composable (NavBackStackEntry)->Unit) {
    composable(route,arguments=arguments,enterTransition={slideInHorizontally(tween(duration,easing=SoEase)) {it}},exitTransition={ExitTransition.None},popEnterTransition={EnterTransition.None},popExitTransition={slideOutHorizontally(tween(duration,easing=SoEase)) {it}}) {entry ->RouteSurface(edgeToEdge) {content(entry)}}
}

@Composable
fun AppNavigation(state: PreviewState,model: PreviewViewModel,onStatusBarAppearance: (Boolean)->Unit) {
    val controller=rememberNavController()
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val motion=LocalMotionEnabled.current
    val scope=rememberCoroutineScope()
    val roll=remember {Animatable(0f)}
    val reveal=remember {Animatable(1f)}
    var revealingRoute by remember {mutableStateOf<String?>(null)}
    var navigationJob by remember {mutableStateOf<Job?>(null)}
    var navigating by remember {mutableStateOf(false)}
    val entry by controller.currentBackStackEntryAsState()
    val coverVisible=(entry?.destination?.route==Destination.PROFILE.route && state.signedIn && state.profile.cover!=null) || (entry?.destination?.route==ScreenRoutes.AUTHOR && entry?.arguments?.getString("authorId")=="zhou")
    val lightStatus=coverVisible || MaterialTheme.colorScheme.surface.luminance()>.5f
    SideEffect {onStatusBarAppearance(lightStatus)}
    val topRoutes=remember {Destination.entries.map {it.route}.toSet()}
    val navigate: (Destination)->Unit={target ->
        if(controller.currentDestination?.route!=target.route&&!navigating) {
            focus.clearFocus();keyboard?.hide()
            navigationJob=scope.launch {
                navigating=true
                try {
                    val flows=setOf(Destination.HOME.route,Destination.CATEGORY.route)
                    val flowChange=controller.currentDestination?.route in flows&&target.route in flows
                    if(flowChange&&motion)roll.animateTo(1f,tween(1000,easing=androidx.compose.animation.core.FastOutLinearInEasing))
                    else {revealingRoute=target.route;reveal.snapTo(if(motion)0f else 1f)}
                    controller.navigate(target.route) {popUpTo(controller.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}
                    if(flowChange)roll.animateTo(0f,tween(if(motion)1000 else 0,easing=SoEase))
                    else reveal.animateTo(1f,tween(if(motion)480 else 0,easing=SoEase))
                } finally {withContext(NonCancellable) {roll.snapTo(0f);reveal.snapTo(1f);revealingRoute=null;navigating=false}}
            }
        }
    }
    BackHandler(navigating) {navigationJob?.cancel()}
    val route: (String)->Unit={focus.clearFocus();keyboard?.hide();controller.navigate(it){launchSingleTop=true}}
    val back: ()->Unit={controller.popBackStack()}
    val read: (String)->Unit={route("reader/"+it)}
    val author: (String)->Unit={route("author/"+it)}
    val profile: ()->Unit={if(!controller.popBackStack(Destination.PROFILE.route,false))navigate(Destination.PROFILE)}
    val enterDemo: (String?)->Unit={name ->if(name!=null)model.profile(state.profile.copy(name=name));model.signIn();profile()}
    val login: ()->Unit={if(!controller.popBackStack(ScreenRoutes.LOGIN,false))controller.navigate(ScreenRoutes.LOGIN) {popUpTo(ScreenRoutes.RESET_SUCCESS){inclusive=true};launchSingleTop=true}}
    val origin=Offset(.12f+.76f*state.placement.x,.94f-.18f*state.placement.y)
    fun sceneModifier(route: String)=if(route==revealingRoute)Modifier.circleReveal(reveal.value,origin) else Modifier
    val pageDuration=if(motion)320 else 0
    CompositionLocalProvider(LocalNavigationPlacement provides state.placement,LocalFlowRoll provides roll.value,LocalShortcuts provides ShortcutActions(state.shortcuts,model::shortcut,model::removeShortcut)) {
        NavHost(navController=controller,startDestination=Destination.HOME.route,modifier=Modifier.fillMaxSize(),
            enterTransition={if(initialState.destination.route !in topRoutes)EnterTransition.None else fadeIn(tween(if(motion)480 else 0),initialAlpha=1f)},
            exitTransition={if(targetState.destination.route !in topRoutes)ExitTransition.None else fadeOut(tween(if(motion)480 else 0),targetAlpha=1f)},popEnterTransition={EnterTransition.None},popExitTransition={ExitTransition.None}) {
            composable(Destination.HOME.route) {RouteSurface(modifier=sceneModifier(Destination.HOME.route)) {HomeScreen(navigate,read)}}
            composable(Destination.CATEGORY.route) {RouteSurface(modifier=sceneModifier(Destination.CATEGORY.route)) {CategoryScreen(navigate,read)}}
            composable(Destination.SEARCH.route) {RouteSurface(modifier=sceneModifier(Destination.SEARCH.route)) {SearchScreen(navigate,read,author)}}
            composable(Destination.PROFILE.route) {RouteSurface(edgeToEdge=true,modifier=sceneModifier(Destination.PROFILE.route)) {ProfileScreen(state,model::nextTheme,navigate,route,read)}}
            secondary("reader/{articleId}",listOf(navArgument("articleId"){type=NavType.StringType}),duration=pageDuration) {ReaderScreen(back,author)}
            secondary(ScreenRoutes.AUTHOR,listOf(navArgument("authorId"){type=NavType.StringType}),edgeToEdge=true,duration=pageDuration) {entry ->AuthorScreen(entry.arguments?.getString("authorId").orEmpty(),back,read)}
            secondary(ScreenRoutes.LOGIN,duration=pageDuration) {AccountScreen(false,back,{controller.navigate(ScreenRoutes.REGISTER){popUpTo(ScreenRoutes.LOGIN){inclusive=true}}},{route(ScreenRoutes.FORGOT)},enterDemo)}
            secondary(ScreenRoutes.REGISTER,duration=pageDuration) {AccountScreen(true,back,{controller.navigate(ScreenRoutes.LOGIN){popUpTo(ScreenRoutes.REGISTER){inclusive=true}}},{route(ScreenRoutes.FORGOT)},enterDemo)}
            secondary(ScreenRoutes.FORGOT,duration=pageDuration) {ForgotPasswordScreen(back,{route(ScreenRoutes.RESET)})}
            secondary(ScreenRoutes.RESET,duration=pageDuration) {ResetPasswordScreen(back,{controller.navigate(ScreenRoutes.RESET_SUCCESS){popUpTo(ScreenRoutes.FORGOT){inclusive=true}}})}
            secondary(ScreenRoutes.RESET_SUCCESS,duration=pageDuration) {ResetSuccessScreen(login)}
            secondary(ScreenRoutes.SETTINGS,duration=pageDuration) {SettingsScreen(state,model,back,route,profile)}
            secondary(ScreenRoutes.EDIT_PROFILE,duration=pageDuration) {EditProfileScreen(state.profile,back,{model.profile(it);back()})}
            secondary(ScreenRoutes.NAV_POSITION,duration=pageDuration) {NavigationPositionScreen(state.placement,back,{model.placement(it);back()})}
            secondary(ScreenRoutes.RECORDS,duration=pageDuration) {RecordsScreen(state,back)}
            secondary(ScreenRoutes.STATES,duration=pageDuration) {ContentStatesScreen(back)}
        }
    }
}
