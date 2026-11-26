package com.semiovert.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.semiovert.app.R
import com.semiovert.app.core.designsystem.CircleColors
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoPalette
import com.semiovert.app.core.designsystem.withInterfaceTypeface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Stable
internal class HomeDraftMenuState {
    var expanded by mutableStateOf(false)
    var visible by mutableStateOf(false)
    var pressed by mutableStateOf<HomeDraftAction?>(null)
    var detached by mutableStateOf<HomeDraftAction?>(null)
    var pulledPosition by mutableStateOf<Offset?>(null)
    var pulledStretch by mutableFloatStateOf(0f)
    var pendingAction by mutableStateOf<HomeDraftAction?>(null)
    val order = mutableStateListOf<HomeDraftAction>()
    val drops = List(2) { MenuDropletState() }
    val slotY = List(2) { Animatable(0f) }

    fun dockedOffset(item: HomeDraftAction) =
        Offset(0f, slotY[item.ordinal].value * drops[item.ordinal].travel.value.coerceAtLeast(0f))

    fun dropletJoins(density: Float, draftBoxActive: Boolean): Map<HomeDraftAction, MenuDropJoin> {
        if (!expanded && !visible) return emptyMap()
        return buildMap {
            for (item in order) {
                if (item == detached || (item == pressed && pulledPosition != null) || (item == HomeDraftAction.BOX && draftBoxActive)) continue
                val scale = DropletMenuMotion.scale(drops[item.ordinal].travel.value)
                val join = DropletMenuMotion.join(dockedOffset(item), scale, density, item.colors.background)
                if (join != null) put(item, join)
            }
        }
    }

    fun requestAction(action: HomeDraftAction) {
        if (pendingAction != null) return
        pendingAction = action
        expanded = false
    }
}

internal enum class HomeDraftAction(val route: String, val label: String, val icon: Int, val colors: CircleColors) {
    CREATE("create-draft", "创建草稿", R.drawable.ic_create_draft,
        paletteCircleColors(SoPalette.BrightAmber)),
    BOX("draft-box", "草稿箱", R.drawable.ic_baby_carriage,
        paletteCircleColors(SoPalette.MintLeaf)),
}

internal val LocalHomeDraftAction = staticCompositionLocalOf<(HomeDraftAction) -> Unit> { {} }

// Keep the lower circle outside the merge zone; leave 6dp between the 52dp circles.
internal val HomeDraftAction.menuOffset: Offset
    get() = HomeDraftCircleMotion.slotOffset(ordinal, HomeDraftAction.entries.size)

internal fun homeDraftOrder(previous: List<HomeDraftAction>, available: List<HomeDraftAction>) =
    previous.filter { it in available } + available.filter { it !in previous }

internal object HomeDraftCircleMotion {
    fun slotOffset(index: Int, count: Int) = Offset(0f, -76f - 58f * (count - 1 - index))
    fun slotShift() = spring<Float>(dampingRatio = .78f, stiffness = 360f, visibilityThreshold = .001f)
}

@Composable
internal fun HomeDraftIcon(action: HomeDraftAction) {
    Icon(painterResource(action.icon), null,
        Modifier.size(NavigationCircleIconSize).testTag("draft-icon-" + action.route),
        tint = action.colors.foreground)
}

@Composable
internal fun HomeDraftCircles(
    state: HomeDraftMenuState, mainCenter: Offset, sceneOrigin: Offset, mainColor: Color,
    joins: Map<HomeDraftAction, MenuDropJoin>,
) {
    val motion = LocalMotionEnabled.current
    val shortcuts = LocalShortcuts.current
    val onAction by rememberUpdatedState(LocalHomeDraftAction.current)
    val draftBox = LocalDraftBox.current
    val available = HomeDraftAction.entries.filter { action -> shortcuts.items.none { it.route == action.route } }
    val actions = state.order.toList()
    val pullShapes = actions.associateWith { item ->
        key(item.route) {
            animateFloatAsState(
                if (motion && state.pressed == item) state.pulledStretch else 0f,
                if (motion) RadialNavigationGesture.stretchSpring() else snap(), label = item.route + "-stretch",
            )
        }
    }
    val host = LocalNavigationShortcutHost.current
    val density = LocalDensity.current.density
    val inputOwner = remember { Any() }
    val scope = rememberCoroutineScope()
    var returnJob by remember { mutableStateOf<Job?>(null) }
    val activate: (HomeDraftAction) -> Unit = { action ->
        if (action == HomeDraftAction.BOX && draftBox != null) {
            val offset = state.pulledPosition?.takeIf { state.pressed == action }
                ?: state.dockedOffset(action)
            draftBox.open(sceneOrigin + (mainCenter + offset) * density)
        } else state.requestAction(action)
    }
    DisposableEffect(host) {
        onDispose {
            if (host?.draftMenu?.owner === inputOwner) host.draftMenu = null
        }
    }
    SideEffect {
        if (state.expanded || state.visible) {
            host?.draftMenu = DockedDraftMenu(
                owner = inputOwner,
                items = actions.filter { state.expanded && it != state.detached && state.drops[it.ordinal].travel.value > .01f }.map { item ->
                    val offset = if (state.pressed == item) state.pulledPosition else null
                    DockedDraftCircle(item, sceneOrigin +
                        (mainCenter + (offset ?: state.dockedOffset(item))) * density,
                        pullShapes.getValue(item))
                },
                press = {
                    returnJob?.cancel()
                    state.pulledPosition = null
                    state.pulledStretch = 0f
                    state.pressed = it
                },
                pull = { item, center, stretch ->
                    state.pressed = item
                    state.pulledPosition = (center - sceneOrigin) / density - mainCenter
                    state.pulledStretch = stretch
                },
                detach = { state.detached = it },
                tap = activate,
                finish = { completed ->
                    val item = state.pressed
                    val from = state.pulledPosition
                    if (!completed && item != null && from != null && state.detached == null) {
                        val stretch = state.pulledStretch
                        returnJob = scope.launch {
                            animate(1f, 0f, animationSpec = if (motion) RadialNavigationGesture.mergeMotion() else snap()) { value, _ ->
                                val rest = state.dockedOffset(item)
                                state.pulledPosition = lerp(rest, from, value)
                                state.pulledStretch = stretch * value
                            }
                            state.pressed = null
                            state.pulledPosition = null
                            state.detached = null
                        }
                    } else {
                        state.pressed = null
                        state.pulledPosition = null
                        state.pulledStretch = 0f
                        if (completed) state.expanded = false else state.detached = null
                    }
                },
            )
        } else if (host?.draftMenu?.owner === inputOwner) host.draftMenu = null
    }
    LaunchedEffect(state.expanded, available, motion) {
        val next = homeDraftOrder(state.order.toList(), available)
        val added = next.filter { it !in state.order }
        // Reset a returning circle before it becomes drawable; it must emerge from the main circle.
        added.forEach {
            state.drops[it.ordinal].snapTo(0f)
            state.slotY[it.ordinal].snapTo(HomeDraftCircleMotion.slotOffset(next.indexOf(it), next.size).y)
        }
        if (state.detached in added) state.detached = null
        state.order.clear()
        state.order.addAll(next)
        if (!state.expanded && !state.visible) {
            next.forEachIndexed { index, item ->
                state.drops[item.ordinal].snapTo(0f)
                state.slotY[item.ordinal].snapTo(HomeDraftCircleMotion.slotOffset(index, next.size).y)
            }
        } else {
            if (state.expanded) {
                state.pendingAction = null
                state.visible = true
            }
            coroutineScope {
                next.forEachIndexed { index, item ->
                    launch {
                        state.slotY[item.ordinal].animateTo(HomeDraftCircleMotion.slotOffset(index, next.size).y,
                            if (motion) HomeDraftCircleMotion.slotShift() else snap())
                    }
                    launch {
                        val rank = if (state.expanded) next.lastIndex - index else index
                        state.drops[item.ordinal].animateTo(state.expanded, rank, motion)
                    }
                }
            }
            state.visible = state.expanded
        }
        if (!state.expanded) {
            state.detached = null
            state.pendingAction?.let { action ->
                // Present the fully collapsed frame before mounting the editor's WebView.
                withFrameNanos { }
                if (!state.expanded && state.pendingAction == action) {
                    state.pendingAction = null
                    onAction(action)
                }
            }
        }
    }
    BackHandler(state.expanded) { state.expanded = false }
    if (!state.expanded && !state.visible) return

    // A scene-sized host keeps circles outside the main circle's bounds tappable.
    Box(Modifier.fillMaxSize().zIndex(3f)) {
        MenuDropletBridges(mainCenter, mainColor, joins.values, Modifier.matchParentSize())
        Box(Modifier.fillMaxSize().testTag("home-draft-dismiss")
            .clickable(
                interactionSource = null, indication = null, enabled = state.expanded,
                onClickLabel = "收起草稿操作",
            ) { state.expanded = false })
        val labelAnchor = actions.minOfOrNull { state.dockedOffset(it).y } ?: 0f
        actions.filter { it != state.detached }.forEach { item ->
            // Detaching one item must not transfer its remembered press animation to its neighbour.
            key(item.route) {
                val index = item.ordinal
                val drop = state.drops[index]
                val amount = drop.travel.value.coerceAtLeast(0f)
                val offset = state.pulledPosition?.takeIf { state.pressed == item }
                    ?: state.dockedOffset(item)
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val hover by animateFloatAsState(
                    if ((pressed || state.pressed == item) && state.expanded) RadialNavigationGesture.SELECTED_SCALE else 1f,
                    if (motion) RadialNavigationGesture.scaleSpring() else snap(),
                    label = item.route + "-hover",
                )
                val labelAlpha by animateFloatAsState(
                    if ((pressed || state.pressed == item) && state.expanded) 1f else 0f,
                    tween(if (motion) 120 else 0), label = item.route + "-label",
                )
                val stretch = pullShapes.getValue(item).value
                val joined = item in joins
                Box(Modifier.offset((mainCenter.x + offset.x - 34f).dp, (mainCenter.y + offset.y - 34f).dp)
                    .size(68.dp).testTag(item.route)
                    .semantics {
                        contentDescription = item.label
                        stateDescription = "长按拖动可固定为独立小圆"
                        customActions = listOf(CustomAccessibilityAction("固定" + item.label + "快捷入口") {
                            val center = sceneOrigin + (mainCenter + offset) * density
                            val controller = host?.beginDraftDrag?.invoke(item, center, center, 0f)
                            controller?.finish(false)
                            if (controller != null) state.expanded = false
                            controller != null
                        })
                    }
                    .graphicsLayer { alpha = if (item == HomeDraftAction.BOX && draftBox?.active == true) 0f else 1f }
                    .clickable(
                        interactionSource = interactions, indication = null,
                        enabled = state.expanded, role = Role.Button,
                    ) { activate(item) }) {
                    NavigationBubble(
                        item.colors, DropletMenuMotion.scale(amount) * if (joined) 1f else hover,
                        if (joined) 0f else stretch, -offset,
                        Modifier.align(Alignment.Center),
                        shadowElevation = if (joined) 0.dp else 3.dp,
                        iconAlpha = drop.icon.value,
                    ) {
                        HomeDraftIcon(item)
                    }
                    Text(item.label, maxLines = 1, softWrap = false,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = NavigationLabelFont, fontWeight = FontWeight.Bold).withInterfaceTypeface(),
                        // Keep the label above both circles, including when the lower one is held.
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = (labelAnchor - offset.y - 24f).dp)
                            .wrapContentWidth(unbounded = true).clearAndSetSemantics {}
                            .graphicsLayer { alpha = labelAlpha * amount.coerceIn(0f, 1f) })
                }
            }
        }
    }
}
