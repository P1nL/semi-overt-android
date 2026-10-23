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
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.WaveDeckOverlayHost
import com.semiovert.app.core.designsystem.wave.DeckCounterHost
import com.semiovert.app.core.designsystem.wave.readerPageOverlay
import com.semiovert.app.core.designsystem.wave.secondaryPageOverlay
import kotlinx.coroutines.flow.first
import com.semiovert.app.feature.home.HomeScreen
import com.semiovert.app.feature.home.HomeSnapshotState
import com.semiovert.app.feature.home.LocalHomeSnapshot
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

private const val SECONDARY_DEPTH_KEY="secondary-overlay-depth"
private const val SPARK_FEEDBACK_KEY="settings-spark-feedback"
private const val AUTHOR_AVATAR_ARRIVAL="author-avatar-arrival"

private fun NavGraphBuilder.secondary(route: String,onBack: ()->Unit,canGoBack: ()->Boolean,
    arguments: List<NamedNavArgument> = emptyList(),edgeToEdge: Boolean=false,content: @Composable (NavBackStackEntry)->Unit) {
    composable(route,arguments=arguments) {entry ->RouteSurface(edgeToEdge,
        Modifier.secondaryPageOverlay(entry.savedStateHandle.get<Float>(SECONDARY_DEPTH_KEY)?:3f)) {
        SparkFeedbackScope(entry.savedStateHandle.get<Boolean>(SPARK_FEEDBACK_KEY)==true) {
            SwipeBackPage(canGoBack(),onBack) {content(entry)}
        }
    }}
}

@Composable
fun AppNavigation(state: PreviewState,model: PreviewViewModel,onStatusBarAppearance: (Boolean)->Unit) {
    val controller=rememberNavController()
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val motion=LocalMotionEnabled.current
    val homeSnapshot=remember {HomeSnapshotState()}
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    var homeEntry by rememberSaveable {mutableIntStateOf(0)}
    var profileEntry by remember {mutableIntStateOf(0)}
    var handoff by remember {mutableStateOf<PageHandoff?>(null)}
    var authorHandoff by remember {mutableStateOf<AuthorAvatarHandoff?>(null)}
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
            val authorSource=controller.currentBackStackEntry?.takeIf {it.destination.route==ScreenRoutes.AUTHOR||it.destination.route=="reader/{articleId}"}
            focus.clearFocus();keyboard?.hide()
            navigating=true
            authorHandoff=null
            handoff=PageHandoff.between(controller.currentDestination?.route,target.route,profileGuest=!state.signedIn)
            val leavingReader=controller.currentDestination?.route=="reader/{articleId}"
            if(leavingReader)controller.currentBackStackEntry?.savedStateHandle?.set(READER_PAGE_NAVIGATION_KEY,true)
            // Save primary-page state, never its author/reader tail. Popping only one reader
            // left an author (or another reader) attached to the saved search/profile stack.
            while(controller.currentDestination?.route !in topRoutes&&controller.previousBackStackEntry!=null) {
                controller.popBackStack()
            }
            if(controller.currentDestination?.route!=target.route) {
                if(target==Destination.HOME)homeEntry++
                if(target==Destination.PROFILE)profileEntry++
                controller.navigate(target.route) {popUpTo(controller.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}
                // Also normalize stacks saved before the detail-tail fix.
                if(controller.currentDestination?.route!=target.route)controller.popBackStack(target.route,false)
            }
            val authorId=authorSource?.arguments?.getString("authorId")
                ?:authorSource?.arguments?.getString("articleId")?.let {SampleLibrary.article(it)?.author?.id}
            val targetEntry=controller.currentBackStackEntry
            if(authorSource!=null&&authorId!=null&&targetEntry!=null)authorHandoff=AuthorAvatarHandoff(authorId,authorSource.id,targetEntry.id,
                maxOf(handoff?.identityMillis?:0,AvatarPixelMotion.DURATION_MILLIS),changesIdentity=true)
        }
    }
    fun changeRoute(restoringReaderCard: Boolean=false,visitingAuthor: String?=null,action: ()->Unit) {
        if(!navigating) {
            focus.clearFocus();keyboard?.hide()
            val previous=controller.currentBackStackEntry
            navigating=true
            action()
            val target=controller.currentBackStackEntry
            val returningAuthor=if(restoringReaderCard&&previous?.destination?.route==ScreenRoutes.AUTHOR&&
                target?.destination?.route in listOf(Destination.SEARCH.route,"reader/{articleId}"))previous.arguments?.getString("authorId") else null
            val authorId=visitingAuthor?:returningAuthor
            authorHandoff=if(authorId!=null&&previous!=null&&target!=null&&previous!=target)
                AuthorAvatarHandoff(authorId,previous.id,target.id,
                    if(previous.destination.route=="reader/{articleId}"||target.destination.route=="reader/{articleId}")SceneMotion.READER_PAGE_EXIT_MS else SceneMotion.SEARCH_PRIMARY_MS)
                else null
            // System back already restores the exact card through ArticleExpansionState.
            // Starting the destination's scene entrance as well moves that source card
            // while the reverse reading overlay is trying to measure and return to it.
            handoff=if(restoringReaderCard&&previous?.destination?.route=="reader/{articleId}")null
                else PageHandoff.between(previous?.destination?.route,controller.currentDestination?.route,profileGuest=!state.signedIn)
            if(previous==controller.currentBackStackEntry)navigating=false
        }
    }
    val route: (String)->Unit={destination ->changeRoute {
        if(destination==Destination.PROFILE.route) {
            if(!controller.popBackStack(destination,false))controller.navigate(destination) {
                popUpTo(controller.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true
            }
        }else {
            val depth=(controller.currentBackStackEntry?.savedStateHandle?.get<Float>(SECONDARY_DEPTH_KEY)?:2f)+1f
            val sparks=destination==ScreenRoutes.SETTINGS||controller.currentBackStackEntry?.savedStateHandle?.get<Boolean>(SPARK_FEEDBACK_KEY)==true
            controller.navigate(destination){launchSingleTop=true}
            controller.currentBackStackEntry?.savedStateHandle?.set(SECONDARY_DEPTH_KEY,depth)
            controller.currentBackStackEntry?.savedStateHandle?.set(SPARK_FEEDBACK_KEY,sparks)
        }
    }}
    val back: ()->Unit={changeRoute(restoringReaderCard=true) {controller.popBackStack()}}
    val read: (String)->Unit={route("reader/"+it)}
    fun visitAuthor(id: String,onReturn: ()->Unit={}) {changeRoute(visitingAuthor=id) {
        val departing=controller.currentBackStackEntry
        // The route includes its author argument: another author's page is not a match.
        if(controller.popBackStack("author/"+id,false)) {
            // The popped entry remains mounted for its exit. Use the same opaque downward
            // handoff as the main circle, instead of removing the reader without animation.
            if(departing?.destination?.route=="reader/{articleId}")
                departing.savedStateHandle[READER_PAGE_NAVIGATION_KEY]=true
            onReturn()
        }
        else controller.navigate("author/"+id){launchSingleTop=true}
        controller.currentBackStackEntry?.savedStateHandle?.let {it[AUTHOR_AVATAR_ARRIVAL]=(it.get<Int>(AUTHOR_AVATAR_ARRIVAL)?:0)+1}
    }}
    val author: (String)->Unit={visitAuthor(it)}
    val profile: ()->Unit={route(Destination.PROFILE.route)}
    val enterDemo: (String?)->Unit={name ->
        if(controller.currentDestination?.route==Destination.PROFILE.route) {
            if(name!=null)model.profile(state.profile.copy(name=name))
            model.signIn()
        }else changeRoute {
            // Registration still exits down; inline login never pops the profile route.
            controller.currentBackStackEntry?.savedStateHandle?.set(ACCOUNT_COMPLETED_KEY,true)
            if(name!=null)model.profile(state.profile.copy(name=name))
            model.signIn()
            controller.popBackStack(Destination.PROFILE.route,false)
        }
    }
    val canSwipeBack: ()->Boolean={!navigating}
    BackHandler(!navigating&&entry?.destination?.route in topRoutes&&entry?.destination?.route!=Destination.HOME.route) {navigate(Destination.HOME)}
    CompositionLocalProvider(LocalNavigationPlacement provides state.placement,
        LocalHomeSnapshot provides homeSnapshot,
        LocalHomeEntry provides homeEntry,
        LocalShortcuts provides ShortcutActions(state.shortcuts,model::shortcut,model::removeShortcut)) {
        SparkFeedbackHost {
        AuthorAvatarTransitionHost(authorHandoff) {
        DeckCounterHost(entry?.destination?.route,SampleLibrary.articles.size) {
        WaveDeckOverlayHost {
        ArticleExpansionHost {
        IdentityTransitionHost {
        NavHost(navController=controller,startDestination=Destination.HOME.route,modifier=Modifier.fillMaxSize().pointerInput(navigating) {
            if(navigating)awaitPointerEventScope {while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}}
        }, enterTransition={pageEnter(motion,profileGuest=!state.signedIn)},exitTransition={pageExit(motion,profileGuest=!state.signedIn)},
            popEnterTransition={pageEnter(motion,pop=true,profileGuest=!state.signedIn)},popExitTransition={pageExit(motion,pop=true,profileGuest=!state.signedIn)}) {
            composable(Destination.HOME.route) {homeRoute ->AuthorAvatarTransitionScene(this,homeRoute.id) {SceneMotionContent(Destination.HOME,handoff) {RouteSurface(edgeToEdge=true) {
                HomeScreen(navigate,read,onToggleTheme={model.theme(if(dark)ThemeMode.LIGHT else ThemeMode.DARK)})
            }}}}
            composable(Destination.CATEGORY.route) {categoryRoute ->AuthorAvatarTransitionScene(this,categoryRoute.id) {
                SceneMotionContent(Destination.CATEGORY,handoff) {RouteSurface(edgeToEdge=true) {CategoryScreen(navigate,read)}}
            }}
            composable(Destination.SEARCH.route) {searchEntry ->AuthorAvatarTransitionScene(this,searchEntry.id) {
                SceneMotionContent(Destination.SEARCH,handoff) {RouteSurface(edgeToEdge=true) {SearchScreen(navigate,read,author)}}
            }}
            composable(Destination.PROFILE.route) {profileRoute ->AuthorAvatarTransitionScene(this,profileRoute.id) {SceneMotionContent(Destination.PROFILE,handoff) {RouteSurface(edgeToEdge=true) {
                ProfileScreen(state,navigate,route,read,enterDemo,profileEntry)
            }}}}
            composable("reader/{articleId}",arguments=listOf(navArgument("articleId"){type=NavType.StringType}),
                enterTransition={EnterTransition.None},exitTransition={readerPageExit(motion)},popEnterTransition={EnterTransition.None},popExitTransition={readerPageExit(motion)}) {readerEntry ->
                val expansion=LocalArticleExpansion.current
                val origin=Destination.entries.find {it.route==controller.previousBackStackEntry?.destination?.route}?:Destination.PROFILE
                AuthorAvatarTransitionScene(this,readerEntry.id) {
                RouteSurface(edgeToEdge=true,modifier=Modifier.readerPageOverlay {
                    readerEntry.savedStateHandle.get<Boolean>(READER_PAGE_NAVIGATION_KEY)==true
                }){ReaderScreen(back,{id->visitAuthor(id) {
                    expansion?.forgetLastOrigin(readerEntry.arguments?.getString("articleId").orEmpty())
                }},navigate,origin)}
                }
            }
            composable(ScreenRoutes.AUTHOR,arguments=listOf(navArgument("authorId"){type=NavType.StringType})) {authorEntry ->
                val avatarArrival by authorEntry.savedStateHandle.getStateFlow(AUTHOR_AVATAR_ARRIVAL,0).collectAsState()
                AuthorAvatarTransitionScene(this,authorEntry.id) {
                SceneMotionContent(Destination.PROFILE,handoff) {RouteSurface(edgeToEdge=true) {
                    SwipeBackPage(canSwipeBack(),back) {
                        AuthorScreen(authorEntry.arguments?.getString("authorId").orEmpty(),back,read,navigate,avatarArrival)
                    }
                }}
                }
            }
            secondary(ScreenRoutes.REGISTER,back,canSwipeBack) {RegistrationScreen(profile,enterDemo)}
            secondary(ScreenRoutes.FORGOT,back,canSwipeBack) {ForgotPasswordScreen(profile)}
            secondary(ScreenRoutes.SETTINGS,back,canSwipeBack) {SettingsScreen(state,model,back,route,profile)}
            secondary(ScreenRoutes.EDIT_PROFILE,back,canSwipeBack) {EditProfileScreen(state.profile,back,{model.profile(it);back()})}
            secondary(ScreenRoutes.NAV_POSITION,back,canSwipeBack,edgeToEdge=true) {NavigationPositionScreen(state.placement,back,{model.placement(it);back()})}
            secondary(ScreenRoutes.RECORDS,back,canSwipeBack) {RecordsScreen(state,back)}
            secondary(ScreenRoutes.STATES,back,canSwipeBack) {ContentStatesScreen(back)}
        }
        }
        }
        }
        }
        }
    }
    }
    // Finish the handoff instead of snapping both scenes back to their resting positions.
    BackHandler(navigating) {}
}
