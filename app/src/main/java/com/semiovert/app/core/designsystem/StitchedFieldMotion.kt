package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal object StitchedFieldMotion {
    const val PRESSED_SCALE=.992f
    const val PRESS_MILLIS=80
    const val RELEASE_MILLIS=600

    val pressEase=Easing {fraction ->1f-(1f-fraction).pow(3)}

    // The supplied GSAP timeline uses elastic.out(1, 0.42), not a physical spring.
    val releaseEase=Easing {fraction ->
        when {
            fraction<=0f->0f
            fraction>=1f->1f
            else->(1.0+2.0.pow(-10.0*fraction)*sin((fraction-.42/4.0)*(2.0*PI/.42))).toFloat()
        }
    }
}
