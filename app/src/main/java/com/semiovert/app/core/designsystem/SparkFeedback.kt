package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlin.math.*

private class SparkBurst(val origin: Offset,val color: Color) {
    val progress=Animatable(0f)
}
private val LocalSparkEmitter=staticCompositionLocalOf<((Offset,Color)->Unit)?> {null}
internal val LocalSparkFeedbackEnabled=compositionLocalOf {false}

/** The host outlives routes; sparks never delay navigation or inherit a route's clipping/fade. */
@Composable
fun SparkFeedbackHost(content: @Composable ()->Unit) {
    val bursts=remember {mutableStateListOf<SparkBurst>()}
    val scope=rememberCoroutineScope()
    var coordinates by remember {mutableStateOf<LayoutCoordinates?>(null)}
    val emit: (Offset,Color)->Unit=remember(scope) {{root,color ->
        val local=coordinates?.takeIf {it.isAttached}?.let {root-it.localToRoot(Offset.Zero)}?:root
        val burst=SparkBurst(local,color)
        bursts.add(burst)
        scope.launch {
            try {burst.progress.animateTo(1f,tween(400,easing=LinearEasing))}
            finally {bursts.remove(burst)}
        }
    }}
    Box(Modifier.fillMaxSize().onGloballyPositioned {coordinates=it}) {
        CompositionLocalProvider(LocalSparkEmitter provides emit) {content()}
        if(bursts.isNotEmpty())Canvas(Modifier.matchParentSize().testTag("spark-feedback")) {
            for(burst in bursts) {
                val p=burst.progress.value
                val spread=1f-(1f-p).pow(3)
                val ink=burst.color.copy(alpha=(1f-p).pow(1.4f))
                repeat(10) {index ->
                    val angle=index*2f*PI.toFloat()/10f+.18f
                    val direction=Offset(cos(angle),sin(angle))
                    val radius=(6f+(if(index%2==0)30f else 23f)*spread)*density
                    val tip=burst.origin+direction*radius
                    if(index%3==0)drawCircle(ink,(1.7f-.7f*p)*density,tip)
                    else drawLine(ink,tip-direction*((7f-5f*p)*density),tip,2f*density,StrokeCap.Round)
                }
            }
        }
    }
}

/** Use accepted press/release interactions, not consumed touches (disabled controls consume too). */
private data class SparkIndication(val emit: (Offset,Color)->Unit,val color: Color): IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode=SparkNode(interactionSource,emit,color)
}

private class SparkNode(val interactions: InteractionSource,val emit: (Offset,Color)->Unit,val color: Color):
    Modifier.Node(),DrawModifierNode,LayoutAwareModifierNode {
    private var coordinates: LayoutCoordinates?=null
    override fun onPlaced(coordinates: LayoutCoordinates) {this.coordinates=coordinates}
    override fun onAttach() {
        coroutineScope.launch {
            val presses=mutableMapOf<PressInteraction.Press,Offset>()
            interactions.interactions.collect {interaction ->when(interaction) {
                is PressInteraction.Press ->coordinates?.takeIf {it.isAttached}?.let {
                    val point=interaction.pressPosition.takeIf {p->p.x.isFinite()&&p.y.isFinite()}
                        ?:Offset(it.size.width/2f,it.size.height/2f)
                    presses[interaction]=it.localToRoot(point)
                }
                is PressInteraction.Release ->presses.remove(interaction.press)?.let {emit(it,color)}
                is PressInteraction.Cancel ->presses.remove(interaction.press)
            }}
        }
    }
    override fun ContentDrawScope.draw() {drawContent()}
}

@Composable
private fun sparkIndication(): IndicationNodeFactory? {
    val emit=LocalSparkEmitter.current?:return null
    if(!LocalMotionEnabled.current)return null
    return SparkIndication(emit,MaterialTheme.colorScheme.primary)
}

/** Scope Material controls as well as ordinary clickables, without changing their semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparkFeedbackScope(enabled: Boolean=true,content: @Composable ()->Unit) {
    val active=enabled&&LocalMotionEnabled.current
    if(!active) {content();return}
    val indication=sparkIndication()?:run {content();return}
    CompositionLocalProvider(LocalSparkFeedbackEnabled provides true,LocalRippleConfiguration provides null,
        LocalIndication provides indication,content=content)
}

internal data class SparkControl(val source: MutableInteractionSource?,val modifier: Modifier)

/** Material controls own their indication; connect their existing interaction source in this scope. */
@Composable
internal fun rememberSparkControl(): SparkControl {
    if(!LocalSparkFeedbackEnabled.current)return SparkControl(null,Modifier)
    val indication=sparkIndication()?:return SparkControl(null,Modifier)
    val source=remember {MutableInteractionSource()}
    return SparkControl(source,Modifier.indication(source,indication))
}

@Composable
fun Modifier.sparkClickable(enabled: Boolean=true,onClickLabel: String?=null,onClick: ()->Unit): Modifier {
    val source=remember {MutableInteractionSource()}
    val indication=sparkIndication()?:LocalIndication.current
    return clickable(source,indication,enabled,onClickLabel,onClick=onClick)
}
