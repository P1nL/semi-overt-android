package com.semiovert.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.semiovert.app.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF008564), onPrimary = Color.White,
    primaryContainer = Color(0xFF6AF3BB), onPrimaryContainer = Color(0xFF004D38),
    secondary = Color(0xFF6950D8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5DEF9), onSecondaryContainer = Color(0xFF3E287F),
    tertiary = Color(0xFFAA482D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDFC7), onTertiaryContainer = Color(0xFF593A28),
    background = Color(0xFFF7F6F2), onBackground = Color(0xFF202A25),
    surface = Color(0xFFF7F6F2), onSurface = Color(0xFF202A25),
    surfaceVariant = Color(0xFFDEEEE3), onSurfaceVariant = Color(0xFF456150),
    outline = Color(0xFF77887D), outlineVariant = Color(0xFFD8DFD8),
)
private val DarkBackground = Color(0xFF191A1C)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF82DFB8), onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF235440), onPrimaryContainer = Color(0xFFB9F5D7),
    secondary = Color(0xFFD0BEFF), onSecondary = Color(0xFF372067),
    secondaryContainer = Color(0xFF46376A), onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFFFFB39B), onTertiary = Color(0xFF542616),
    tertiaryContainer = Color(0xFF593F31), onTertiaryContainer = Color(0xFFFFDFCA),
    background = DarkBackground, onBackground = Color(0xFFE4EBE2),
    surface = DarkBackground, onSurface = Color(0xFFE4EBE2),
    surfaceDim = DarkBackground, surfaceBright = DarkBackground,
    surfaceContainerLowest = DarkBackground, surfaceContainerLow = DarkBackground,
    surfaceContainer = DarkBackground, surfaceContainerHigh = DarkBackground,
    surfaceContainerHighest = DarkBackground, surfaceTint = DarkBackground,
    surfaceVariant = Color(0xFF2C4033), onSurfaceVariant = Color(0xFFBDCEC0),
    outline = Color(0xFF91A194), outlineVariant = Color(0xFF3B4B3F),
)

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
    val target=if(dark)DarkColors else LightColors
    @Composable fun blend(value: Color)=animateColorAsState(value,tween(motionMillis(220)),label="theme-color").value
    val colors=target.copy(
        primary=blend(target.primary),onPrimary=blend(target.onPrimary),primaryContainer=blend(target.primaryContainer),onPrimaryContainer=blend(target.onPrimaryContainer),
        secondary=blend(target.secondary),onSecondary=blend(target.onSecondary),secondaryContainer=blend(target.secondaryContainer),onSecondaryContainer=blend(target.onSecondaryContainer),
        tertiary=blend(target.tertiary),onTertiary=blend(target.onTertiary),tertiaryContainer=blend(target.tertiaryContainer),onTertiaryContainer=blend(target.onTertiaryContainer),
        background=blend(target.background),onBackground=blend(target.onBackground),surface=blend(target.surface),onSurface=blend(target.onSurface),
        surfaceVariant=blend(target.surfaceVariant),onSurfaceVariant=blend(target.onSurfaceVariant),outline=blend(target.outline),outlineVariant=blend(target.outlineVariant),
        surfaceDim=blend(target.surfaceDim),surfaceBright=blend(target.surfaceBright),surfaceTint=blend(target.surfaceTint),
        surfaceContainerLowest=blend(target.surfaceContainerLowest),surfaceContainerLow=blend(target.surfaceContainerLow),surfaceContainer=blend(target.surfaceContainer),surfaceContainerHigh=blend(target.surfaceContainerHigh),surfaceContainerHighest=blend(target.surfaceContainerHighest),
    )
    MaterialTheme(colorScheme=colors,typography=SoTypography,content=content)
}
