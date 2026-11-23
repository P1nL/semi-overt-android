package com.semiovert.app.core.designsystem

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import com.semiovert.app.R

private val interfaceTypeface: FontFamily? = null
private val digitRuns = Regex("[0-9]+")
internal val InterfaceDigitFont = FontFamily(
    Font(R.font.yufanxinyu_digits_regular),
    Font(R.font.yufanxinyu_digits_bold, weight = FontWeight.Bold),
)
private val emphasizedDigits = SpanStyle(
    fontFamily = FontFamily(Font(R.font.yufanxinyu_digits_extrabold, weight = FontWeight.ExtraBold)),
    fontWeight = FontWeight.ExtraBold,
)

internal fun TextStyle.withInterfaceTypeface() =
    interfaceTypeface?.let { copy(fontFamily = it) } ?: this

internal fun String.withInterfaceDigits() = AnnotatedString(this).withInterfaceDigits()

internal fun String.withEmphasizedInterfaceDigits(size: TextUnit = TextUnit.Unspecified) =
    AnnotatedString(this).withInterfaceDigits(emphasizedDigits.copy(fontSize = size))

private fun AnnotatedString.withInterfaceDigits(digits: SpanStyle = SpanStyle(fontFamily = InterfaceDigitFont)): AnnotatedString = buildAnnotatedString {
    append(this@withInterfaceDigits)
    digitRuns.findAll(this@withInterfaceDigits.text).forEach { match ->
        addStyle(digits, match.range.first, match.range.last + 1)
    }
}

internal val InterfaceDigitTransformation = VisualTransformation {
    TransformedText(it.withInterfaceDigits(), OffsetMapping.Identity)
}
