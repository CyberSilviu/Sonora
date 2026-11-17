/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFF7C4DFF)
private val LegacyDefaultThemeColor = Color(0xFFED5564)

val SonoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val effectiveSeedColor = if (themeColor == LegacyDefaultThemeColor) DefaultThemeColor else themeColor

    val baseColorScheme = rememberDynamicColorScheme(
        seedColor = effectiveSeedColor,
        isDark = darkTheme,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.Vibrant,
    )

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, effectiveSeedColor) {
        when {
            darkTheme && pureBlack -> baseColorScheme.pureBlack(true)
            darkTheme && effectiveSeedColor == DefaultThemeColor -> baseColorScheme.copy(
                background = Color(0xFF0A0914),
                surface = Color(0xFF0A0914),
                surfaceDim = Color(0xFF080710),
                surfaceContainerLowest = Color(0xFF06050C),
                surfaceContainerLow = Color(0xFF100E1E),
                surfaceContainer = Color(0xFF151326),
                surfaceContainerHigh = Color(0xFF1D1A33),
                surfaceContainerHighest = Color(0xFF262240),
                surfaceVariant = Color(0xFF231F3A),
            )
            else -> baseColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SonoraShapes,
        content = content,
    )
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
