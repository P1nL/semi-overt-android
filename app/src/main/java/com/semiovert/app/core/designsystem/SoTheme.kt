package com.semiovert.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.semiovert.app.core.model.ThemeMode

val SoGreen = SoPalette.ForestGreen

private fun Color.tintOn(surface: Color, amount: Float) = copy(alpha = amount).compositeOver(surface)

private val LightColors = with(SoPalette) {
    lightColorScheme(
        primary = ForestGreen, onPrimary = Color.White,
        primaryContainer = ForestGreen.tintOn(LightSurface, .10f), onPrimaryContainer = ForestGreen.tintOn(LightInk, .36f),
        secondary = Amethyst, onSecondary = Color.White,
        secondaryContainer = Amethyst.tintOn(LightSurface, .12f), onSecondaryContainer = Amethyst.tintOn(LightInk, .40f),
        tertiary = AutumnLeaf.tintOn(DarkSurface, .70f), onTertiary = Color.White,
        tertiaryContainer = CarrotOrange.tintOn(LightSurface, .18f), onTertiaryContainer = AutumnLeaf.tintOn(LightInk, .28f),
        background = LightSurface, onBackground = LightInk, surface = LightSurface, onSurface = LightInk,
        surfaceVariant = Teal.tintOn(LightSurface, .11f), onSurfaceVariant = LightInk.tintOn(LightSurface, .81f),
        surfaceDim = Teal.tintOn(LightSurface, .14f), surfaceBright = LightSurface, surfaceTint = ForestGreen,
        surfaceContainerLowest = Color.White, surfaceContainerLow = LightSurface,
        surfaceContainer = Teal.tintOn(LightSurface, .04f), surfaceContainerHigh = Teal.tintOn(LightSurface, .07f),
        surfaceContainerHighest = Teal.tintOn(LightSurface, .11f),
        outline = LightInk.tintOn(LightSurface, .57f), outlineVariant = Teal.tintOn(LightSurface, .24f),
        error = TomatoJam, onError = Color.White,
        errorContainer = Tomato.tintOn(LightSurface, .21f), onErrorContainer = TomatoJam.tintOn(LightInk, .45f),
        inverseSurface = DarkSurface, inverseOnSurface = DarkInk, inversePrimary = MintLeaf,
    )
}

private val DarkColors = with(SoPalette) {
    darkColorScheme(
        primary = MintLeaf, onPrimary = DarkSurface,
        primaryContainer = Teal.tintOn(DarkSurface, .30f), onPrimaryContainer = MintLeaf.tintOn(DarkInk, .28f),
        secondary = Amethyst.tintOn(Color.White, .54f), onSecondary = DarkSurface,
        secondaryContainer = Amethyst.tintOn(DarkSurface, .32f), onSecondaryContainer = Amethyst.tintOn(DarkInk, .24f),
        tertiary = CarrotOrange, onTertiary = DarkSurface,
        tertiaryContainer = CarrotOrange.tintOn(DarkSurface, .23f), onTertiaryContainer = CarrotOrange.tintOn(DarkInk, .30f),
        background = DarkSurface, onBackground = DarkInk, surface = DarkSurface, onSurface = DarkInk,
        surfaceVariant = Teal.tintOn(DarkSurface, .22f), onSurfaceVariant = DarkInk.tintOn(DarkSurface, .80f),
        surfaceDim = DarkSurface, surfaceBright = DarkSurface, surfaceTint = DarkSurface,
        surfaceContainerLowest = DarkSurface, surfaceContainerLow = DarkSurface,
        surfaceContainer = DarkSurface, surfaceContainerHigh = DarkSurface, surfaceContainerHighest = DarkSurface,
        outline = DarkInk.tintOn(DarkSurface, .46f), outlineVariant = Teal.tintOn(DarkSurface, .38f),
        error = Tomato, onError = DarkSurface,
        errorContainer = TomatoJam.tintOn(DarkSurface, .32f), onErrorContainer = Tomato.tintOn(DarkInk, .29f),
        inverseSurface = LightSurface, inverseOnSurface = LightInk, inversePrimary = ForestGreen,
    )
}

internal fun soColorScheme(dark: Boolean) = if (dark) DarkColors else LightColors

private val SoTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 43.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 31.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun SoTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val target=soColorScheme(dark)
    val revealing=LocalThemeReveal.current?.running==true
    @Composable fun blend(value: Color)=animateColorAsState(value,tween(if(revealing)0 else motionMillis(220)),label="theme-color").value
    val colors=target.copy(
        primary=blend(target.primary),onPrimary=blend(target.onPrimary),primaryContainer=blend(target.primaryContainer),onPrimaryContainer=blend(target.onPrimaryContainer),
        secondary=blend(target.secondary),onSecondary=blend(target.onSecondary),secondaryContainer=blend(target.secondaryContainer),onSecondaryContainer=blend(target.onSecondaryContainer),
        tertiary=blend(target.tertiary),onTertiary=blend(target.onTertiary),tertiaryContainer=blend(target.tertiaryContainer),onTertiaryContainer=blend(target.onTertiaryContainer),
        background=blend(target.background),onBackground=blend(target.onBackground),surface=blend(target.surface),onSurface=blend(target.onSurface),
        surfaceVariant=blend(target.surfaceVariant),onSurfaceVariant=blend(target.onSurfaceVariant),outline=blend(target.outline),outlineVariant=blend(target.outlineVariant),
        surfaceDim=blend(target.surfaceDim),surfaceBright=blend(target.surfaceBright),surfaceTint=blend(target.surfaceTint),
        surfaceContainerLowest=blend(target.surfaceContainerLowest),surfaceContainerLow=blend(target.surfaceContainerLow),surfaceContainer=blend(target.surfaceContainer),surfaceContainerHigh=blend(target.surfaceContainerHigh),surfaceContainerHighest=blend(target.surfaceContainerHighest),
        error=blend(target.error),onError=blend(target.onError),errorContainer=blend(target.errorContainer),onErrorContainer=blend(target.onErrorContainer),
        inverseSurface=blend(target.inverseSurface),inverseOnSurface=blend(target.inverseOnSurface),inversePrimary=blend(target.inversePrimary),
    )
    MaterialTheme(colorScheme=colors,typography=SoTypography,content=content)
}
