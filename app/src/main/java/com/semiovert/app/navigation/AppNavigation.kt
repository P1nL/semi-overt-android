package com.semiovert.app.navigation

import androidx.compose.animation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.data.LocalBusiness
import com.semiovert.app.core.data.rememberRequest
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.WaveDeckOverlayHost
import com.semiovert.app.core.designsystem.wave.DeckCounterHost
import com.semiovert.app.core.designsystem.wave.readerPageOverlay
import com.semiovert.app.core.designsystem.wave.secondaryPageOverlay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.semiovert.app.feature.home.HomeScreen
import com.semiovert.app.feature.home.HomeSnapshotState
import com.semiovert.app.feature.home.LocalHomeSnapshot
import com.semiovert.app.feature.category.CategoryScreen
import com.semiovert.app.feature.search.SearchScreen
import com.semiovert.app.feature.reader.ReaderScreen
import com.semiovert.app.feature.reader.ArticleExpansionHost
import com.semiovert.app.feature.reader.rememberArticleExpansionState
import com.semiovert.app.feature.profile.*
import com.semiovert.app.feature.account.*
import com.semiovert.app.feature.settings.*
import com.semiovert.app.feature.states.ContentStatesScreen
import com.semiovert.app.feature.editor.ArticleEditorScreen
import com.semiovert.app.feature.editor.rememberArticleEditorSession
import com.semiovert.app.feature.editor.EditorPalette
import com.semiovert.app.feature.editor.EDITOR_DRAFT_KEY

@Composable
private fun RouteSurface(edgeToEdge: Boolean=false,modifier: Modifier=Modifier,opaque: Boolean=false,content: @Composable ()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(modifier.fillMaxSize(),color=if(opaque)colors.background else Color.Transparent,contentColor=colors.onBackground) {
        Box(Modifier.fillMaxSize().then(if(edgeToEdge)Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)))) {content()}
    }
}

private fun String?.playsBrowseBackground(signedIn: Boolean)=when(this) {
    Destination.HOME.route,Destination.CATEGORY.route,Destination.SEARCH.route->true
    Destination.PROFILE.route->signedIn
    else->false
}

private const val SECONDARY_DEPTH_KEY="secondary-overlay-depth"
private const val SPARK_FEEDBACK_KEY="settings-spark-feedback"
private const val AUTHOR_AVATAR_ARRIVAL="author-avatar-arrival"

private fun NavGraphBuilder.secondary(route: String,onBack: ()->Unit,canGoBack: ()->Boolean,
    arguments: List<NamedNavArgument> = emptyList(),edgeToEdge: Boolean=false,swipeBack: Boolean=true,
    content: @Composable (NavBackStackEntry)->Unit) {
    composable(route,arguments=arguments) {entry ->RouteSurface(edgeToEdge,
        Modifier.secondaryPageOverlay(entry.savedStateHandle.get<Float>(SECONDARY_DEPTH_KEY)?:3f),opaque=true) {
        // Handle committed back inside NavHost instead of its predictive pop.
        BackHandler(canGoBack(),onBack)
        SparkFeedbackScope(entry.savedStateHandle.get<Boolean>(SPARK_FEEDBACK_KEY)==true) {
            if(swipeBack)SwipeBackPage(canGoBack(),onBack) {content(entry)} else content(entry)
        }
    }}
}

@Composable
fun AppNavigation(state: PreviewState,model: PreviewViewModel,onStatusBarAppearance: (Boolean)->Unit) {
    val business = LocalBusiness.current
    val session = business?.session?.collectAsStateWithLifecycle()?.value
    val account = session?.account
    val homeOwner = session?.takeIf { it.authenticated }?.account?.id
    val homeRequest = rememberUpdatedState(business?.let { rememberRequest(it, homeOwner) { it.home() } })
    val controller=rememberNavController()
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val motion=LocalMotionEnabled.current
    val homeSnapshot=remember {HomeSnapshotState()}
    val graphics=LocalGraphicsContext.current
    val navigationScope=rememberCoroutineScope()
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    var homeEntry by rememberSaveable {mutableIntStateOf(0)}
    var mainCirclePage by rememberSaveable {mutableStateOf(Destination.HOME)}
    var profileEntry by remember {mutableIntStateOf(0)}
    var handoff by remember {mutableStateOf<PageHandoff?>(null)}
    var authorHandoff by remember {mutableStateOf<AuthorAvatarHandoff?>(null)}
    var navigating by remember {mutableStateOf(false)}
    var editorOpen by rememberSaveable(account?.id) {mutableStateOf(false)}
    val editorStorage=account?.id?.let { "$EDITOR_DRAFT_KEY:account:$it" } ?: EDITOR_DRAFT_KEY
    val editorSession=rememberArticleEditorSession(editorStorage,business,account?.id,
        account?.profile ?: state.profile.takeIf {business==null&&state.signedIn})
    val expansion=rememberArticleExpansionState()
    val draftBox=remember(account?.id) {DraftBoxState()}
    val mayWrite=business==null||account!=null
    val entry by controller.currentBackStackEntryAsState()
    val introPreparation=LocalIntroPreparation.current
    val introRunning=introPreparation!=null
    val waitForHomeEntrance=remember {introRunning}
    var homeEntered by remember {mutableStateOf(false)}
    val canPlayBackground=!introRunning&&(!waitForHomeEntrance||homeEntered||
        entry?.destination?.route?.let {it!=Destination.HOME.route}==true)
    LaunchedEffect(entry) {
        navigating=true
        entry?.lifecycle?.currentStateFlow?.first {it==Lifecycle.State.RESUMED}
        navigating=false
    }
    val lightStatus=MaterialTheme.colorScheme.surface.luminance()>.5f
    SideEffect {onStatusBarAppearance(lightStatus)}
    fun beforeHomeDeparture(depart: ()->Unit) {
        if(navigating)return
        val source=controller.currentBackStackEntry
        // An animated reading open already captured before changing the source card.
        if(source?.destination?.route!=Destination.HOME.route||expansion.source?.origin==ArticleOrigin.HOME) {
            depart()
            return
        }
        navigating=true
        navigationScope.launch {
            var recorded=false
            try {
                homeSnapshot.captureDeparture(graphics) {
                    recorded=true
                    navigating=false
                    if(controller.currentBackStackEntry==source)depart()
                }
            } finally {
                if(!recorded)navigating=false
            }
        }
    }
    SideEffect {expansion.beforeHomeOpen={beforeHomeDeparture(it)}}
    val topRoutes=remember {Destination.entries.map {it.route}.toSet()}
    fun navigatePrimary(target: Destination,shortcut: Boolean) {
        if(!navigating)mainCirclePage=mainCirclePageAfter(mainCirclePage,target,shortcut)
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
                ?:authorSource?.arguments?.getString("articleId")?.let {
                    (if (business == null) SampleLibrary.article(it) else business.knownArticle(it))?.author?.id
                }
            val targetEntry=controller.currentBackStackEntry
            if(authorSource!=null&&authorId!=null&&targetEntry!=null)authorHandoff=AuthorAvatarHandoff(authorId,authorSource.id,targetEntry.id,
                maxOf(handoff?.identityMillis?:0,AvatarPixelMotion.DURATION_MILLIS),changesIdentity=true)
        }
    }
    val navigateTo: (Destination,Boolean)->Unit={target,shortcut ->
        if(target==Destination.HOME)navigatePrimary(target,shortcut)
        else beforeHomeDeparture {navigatePrimary(target,shortcut)}
    }
    val navigate: (Destination)->Unit={navigateTo(it,false)}
    val navigateShortcut: (Destination)->Unit={navigateTo(it,true)}
    fun changeRoute(restoringReaderCard: Boolean=false,visitingAuthor: String?=null,action: ()->Unit) =
        beforeHomeDeparture {
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
    val read: (String)->Unit={route("reader/"+Uri.encode(it))}
    fun visitAuthor(id: String,onReturn: ()->Unit={}) {changeRoute(visitingAuthor=id) {
        val departing=controller.currentBackStackEntry
        // The route includes its author argument: another author's page is not a match.
        if(controller.popBackStack("author/"+Uri.encode(id),false)) {
            // The popped entry remains mounted for its exit. Use the same opaque downward
            // handoff as the main circle, instead of removing the reader without animation.
            if(departing?.destination?.route=="reader/{articleId}")
                departing.savedStateHandle[READER_PAGE_NAVIGATION_KEY]=true
            onReturn()
        }
        else controller.navigate("author/"+Uri.encode(id)){launchSingleTop=true}
        controller.currentBackStackEntry?.savedStateHandle?.let {it[AUTHOR_AVATAR_ARRIVAL]=(it.get<Int>(AUTHOR_AVATAR_ARRIVAL)?:0)+1}
    }}
    val author: (String)->Unit={visitAuthor(it)}
    val profile: ()->Unit={route(Destination.PROFILE.route)}
    val enterDemo: (String?)->Unit={name ->
        if(controller.currentDestination?.route==Destination.PROFILE.route) {
            if (business == null) {
                if(name!=null)model.profile(state.profile.copy(name=name))
                model.signIn()
            }
        }else changeRoute {
            // Registration still exits down; inline login never pops the profile route.
            controller.currentBackStackEntry?.savedStateHandle?.set(ACCOUNT_COMPLETED_KEY,true)
            if (business == null) {
                if(name!=null)model.profile(state.profile.copy(name=name))
                model.signIn()
            }
            controller.popBackStack(Destination.PROFILE.route,false)
        }
    }
    val canSwipeBack: ()->Boolean={!navigating}
    CompositionLocalProvider(LocalNavigationPlacement provides state.placement,
        LocalMainCirclePage provides mainCirclePage,
        LocalShortcutNavigate provides navigateShortcut,
        LocalHomeSnapshot provides homeSnapshot,
        LocalHomeEntry provides homeEntry,
        LocalDraftBox provides draftBox.takeIf { mayWrite },
        LocalHomeDraftAction provides {action ->
            if(!mayWrite)profile()
            else if(action==HomeDraftAction.CREATE&&!editorOpen&&!navigating) {
                if(business!=null)editorSession.selectDraft(null)
                focus.clearFocus();keyboard?.hide();editorOpen=true
            }
        },
        LocalShortcuts provides ShortcutActions(state.shortcuts,model::shortcut,model::removeShortcut)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WebHomeBackground(Modifier.graphicsLayer {
            alpha=if(expansion.active)1f-SceneMotion.readingChrome(expansion.progress,expansion.returning) else 1f
        },active=canPlayBackground&&entry?.destination?.route.playsBrowseBackground(state.signedIn)&&
            LocalIntroReady.current&&!editorOpen&&!expansion.active,preparation=introPreparation)
        NavigationShortcutHost(Destination.entries.find {it.route==entry?.destination?.route},navigateShortcut,!navigating&&!editorOpen&&!draftBox.active) {
        SparkFeedbackHost {
        AuthorAvatarTransitionHost(authorHandoff) {
        WaveDeckOverlayHost {
        ArticleExpansionHost(expansion) {
        DeckCounterHost(entry?.destination?.route,if (business == null) SampleLibrary.articles.size else 20,navigating=navigating) {
        IdentityTransitionHost {
        NavHost(navController=controller,startDestination=Destination.HOME.route,modifier=Modifier.fillMaxSize().pointerInput(navigating) {
            if(navigating)awaitPointerEventScope {while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}}
        }, enterTransition={pageEnter(motion,profileGuest=!state.signedIn)},exitTransition={pageExit(motion,profileGuest=!state.signedIn)},
            popEnterTransition={pageEnter(motion,pop=true,profileGuest=!state.signedIn)},popExitTransition={pageExit(motion,pop=true,profileGuest=!state.signedIn)}) {
            composable(Destination.HOME.route) {homeRoute ->AuthorAvatarTransitionScene(this,homeRoute.id) {SceneMotionContent(Destination.HOME,handoff) {RouteSurface(edgeToEdge=true) {
                HomeScreen(navigate,read,onToggleTheme={model.theme(if(dark)ThemeMode.LIGHT else ThemeMode.DARK)},
                    remote=homeRequest.value,onEntranceComplete={homeEntered=true})
            }}}}
            composable(Destination.CATEGORY.route) {categoryRoute ->AuthorAvatarTransitionScene(this,categoryRoute.id) {
                BackHandler(!navigating&&entry?.id==categoryRoute.id) {navigate(Destination.HOME)}
                SceneMotionContent(Destination.CATEGORY,handoff) {RouteSurface(edgeToEdge=true) {CategoryScreen(navigate,read)}}
            }}
            composable(Destination.SEARCH.route) {searchEntry ->AuthorAvatarTransitionScene(this,searchEntry.id) {
                BackHandler(!navigating&&entry?.id==searchEntry.id) {navigate(Destination.HOME)}
                SceneMotionContent(Destination.SEARCH,handoff) {RouteSurface(edgeToEdge=true) {SearchScreen(navigate,read,author)}}
            }}
            composable(Destination.PROFILE.route) {profileRoute ->AuthorAvatarTransitionScene(this,profileRoute.id) {SceneMotionContent(Destination.PROFILE,handoff) {RouteSurface(edgeToEdge=true) {
                BackHandler(!navigating&&entry?.id==profileRoute.id) {navigate(Destination.HOME)}
                ProfileScreen(state,navigate,route,read,enterDemo,profileEntry)
            }}}}
            composable("reader/{articleId}",arguments=listOf(navArgument("articleId"){type=NavType.StringType}),
                enterTransition={EnterTransition.None},exitTransition={readerPageExit(motion)},popEnterTransition={EnterTransition.None},popExitTransition={readerPageExit(motion)}) {readerEntry ->
                val expansion=LocalArticleExpansion.current
                val origin=Destination.entries.find {it.route==controller.previousBackStackEntry?.destination?.route}?:Destination.PROFILE
                val leavingForPage={readerEntry.savedStateHandle.get<Boolean>(READER_PAGE_NAVIGATION_KEY)==true}
                AuthorAvatarTransitionScene(this,readerEntry.id) {
                RouteSurface(edgeToEdge=true,modifier=Modifier.readerPageOverlay(leavingForPage),opaque=true) {
                    ReaderScreen(back,{id->visitAuthor(id) {
                    expansion?.forgetLastOrigin(readerEntry.arguments?.getString("articleId").orEmpty())
                }},navigate,origin)}
                }
            }
            composable(ScreenRoutes.AUTHOR,arguments=listOf(navArgument("authorId"){type=NavType.StringType})) {authorEntry ->
                BackHandler(!navigating&&entry?.id==authorEntry.id,onBack=back)
                val avatarArrival by authorEntry.savedStateHandle.getStateFlow(AUTHOR_AVATAR_ARRIVAL,0).collectAsState()
                AuthorAvatarTransitionScene(this,authorEntry.id) {
                SceneMotionContent(Destination.PROFILE,handoff) {RouteSurface(edgeToEdge=true,opaque=true) {
                    SwipeBackPage(canSwipeBack(),back) {
                        AuthorScreen(authorEntry.arguments?.getString("authorId").orEmpty(),back,read,navigate,avatarArrival)
                    }
                }}
                }
            }
            secondary(ScreenRoutes.REGISTER,back,canSwipeBack) {RegistrationScreen(enterDemo)}
            secondary(ScreenRoutes.FORGOT,back,canSwipeBack) {ForgotPasswordScreen(enterDemo)}
            secondary(ScreenRoutes.SETTINGS,back,canSwipeBack) {SettingsScreen(state,model,back,route,profile)}
            secondary(ScreenRoutes.EDIT_PROFILE,back,canSwipeBack,swipeBack=false) {
                if (business == null) EditProfileScreen(state.profile,back,{model.profile(it);back()})
                else if (account != null) key(account.id) {
                    EditProfileScreen(state.profile, back, {}, persist = { business.saveProfile(account.id, state.profile, it); back() })
                } else PageFrame("编辑资料", back, "edit-profile-unavailable", scroll=false) {
                    RemoteState("请先登录",Modifier.fillMaxSize())
                }
            }
            secondary(ScreenRoutes.NAV_POSITION,back,canSwipeBack,edgeToEdge=true) {NavigationPositionScreen(state.placement,back,{model.placement(it);back()})}
            secondary(ScreenRoutes.STATES,back,canSwipeBack) {ContentStatesScreen(back)}
        }
        BackHandler(navigating) {
            val source=controller.currentBackStackEntry
            if(!state.signedIn&&source?.destination?.route==Destination.PROFILE.route)navigationScope.launch {
                snapshotFlow {!navigating}.first {it}
                if(controller.currentBackStackEntry===source)navigate(Destination.HOME)
            }
        }
        }
        }
        }
        }
        }
    }
    }
        DraftBoxBubble(draftBox,editorSession.drafts,editorSession.draftsLoading,editorSession.draftsFailed,
            onLoad={editorSession.loadDrafts(EditorPalette.forTheme(dark))},
            onOpenDraft={draft->editorSession.selectDraft(draft);focus.clearFocus();keyboard?.hide();editorOpen=true},
            deletingKey=editorSession.deletingDraftKey,onDeleteDraft=editorSession::deleteDraft,message=editorSession.draftsMessage,
            onRetry={editorSession.loadDrafts(EditorPalette.forTheme(dark),retry=true)})
        if(editorOpen&&mayWrite)ArticleEditorScreen(onBack={keyboard?.hide();focus.clearFocus();editorOpen=false},session=editorSession)
        }
        }
}
