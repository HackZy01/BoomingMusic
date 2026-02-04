package com.mardous.booming.ui.theme

import android.app.UiModeManager
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import hct.Hct
import scheme.*

@Stable
@Composable
fun getSystemContrast(): Double {
    val context = LocalContext.current
    val uiManager = context.getSystemService<UiModeManager>()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        uiManager?.contrast?.toDouble() ?: 0.0
    } else {
        0.0
    }
}

@Stable
fun dynamicColorScheme(
    keyColor: Color,
    isDark: Boolean,
    style: PaletteStyle,
    contrastLevel: Double
): ColorScheme {
    val hct = Hct.fromInt(keyColor.toArgb())
    val scheme = when (style) {
        PaletteStyle.TonalSpot -> SchemeTonalSpot(hct, isDark, contrastLevel)
        PaletteStyle.Neutral -> SchemeNeutral(hct, isDark, contrastLevel)
        PaletteStyle.Vibrant -> SchemeVibrant(hct, isDark, contrastLevel)
        PaletteStyle.Expressive -> SchemeExpressive(hct, isDark, contrastLevel)
        PaletteStyle.Rainbow -> SchemeRainbow(hct, isDark, contrastLevel)
        PaletteStyle.FruitSalad -> SchemeFruitSalad(hct, isDark, contrastLevel)
        PaletteStyle.Monochrome -> SchemeMonochrome(hct, isDark, contrastLevel)
        PaletteStyle.Fidelity -> SchemeFidelity(hct, isDark, contrastLevel)
        PaletteStyle.Content -> SchemeContent(hct, isDark, contrastLevel)
    }

    return ColorScheme(
        primary = scheme.getPrimary().toColor(),
        onPrimary = scheme.getOnPrimary().toColor(),
        primaryContainer = scheme.getPrimaryContainer().toColor(),
        onPrimaryContainer = scheme.getOnPrimaryContainer().toColor(),
        inversePrimary = scheme.getInversePrimary().toColor(),
        secondary = scheme.getSecondary().toColor(),
        onSecondary = scheme.getOnSecondary().toColor(),
        secondaryContainer = scheme.getSecondaryContainer().toColor(),
        onSecondaryContainer = scheme.getOnSecondaryContainer().toColor(),
        tertiary = scheme.getTertiary().toColor(),
        onTertiary = scheme.getOnTertiary().toColor(),
        tertiaryContainer = scheme.getTertiaryContainer().toColor(),
        onTertiaryContainer = scheme.getOnTertiaryContainer().toColor(),
        background = scheme.getBackground().toColor(),
        onBackground = scheme.getOnBackground().toColor(),
        surface = scheme.getSurface().toColor(),
        onSurface = scheme.getOnSurface().toColor(),
        surfaceVariant = scheme.getSurfaceVariant().toColor(),
        onSurfaceVariant = scheme.getOnSurfaceVariant().toColor(),
        surfaceTint = scheme.getSurfaceTint().toColor(),
        inverseSurface = scheme.getInverseSurface().toColor(),
        inverseOnSurface = scheme.getInverseOnSurface().toColor(),
        error = scheme.getError().toColor(),
        onError = scheme.getOnError().toColor(),
        errorContainer = scheme.getErrorContainer().toColor(),
        onErrorContainer = scheme.getOnErrorContainer().toColor(),
        outline = scheme.getOutline().toColor(),
        outlineVariant = scheme.getOutlineVariant().toColor(),
        scrim = scheme.getScrim().toColor(),
        surfaceBright = scheme.getSurfaceBright().toColor(),
        surfaceDim = scheme.getSurfaceDim().toColor(),
        surfaceContainer = scheme.getSurfaceContainer().toColor(),
        surfaceContainerHigh = scheme.getSurfaceContainerHigh().toColor(),
        surfaceContainerHighest = scheme.getSurfaceContainerHighest().toColor(),
        surfaceContainerLow = scheme.getSurfaceContainerLow().toColor(),
        surfaceContainerLowest = scheme.getSurfaceContainerLowest().toColor(),
        primaryFixed = scheme.getPrimaryFixed().toColor(),
        primaryFixedDim = scheme.getPrimaryFixedDim().toColor(),
        onPrimaryFixed = scheme.getOnPrimaryFixed().toColor(),
        onPrimaryFixedVariant = scheme.getOnPrimaryFixedVariant().toColor(),
        secondaryFixed = scheme.getSecondaryFixed().toColor(),
        secondaryFixedDim = scheme.getSecondaryFixedDim().toColor(),
        onSecondaryFixed = scheme.getOnSecondaryFixed().toColor(),
        onSecondaryFixedVariant = scheme.getOnSecondaryFixedVariant().toColor(),
        tertiaryFixed = scheme.getTertiaryFixed().toColor(),
        tertiaryFixedDim = scheme.getTertiaryFixedDim().toColor(),
        onTertiaryFixed = scheme.getOnTertiaryFixed().toColor(),
        onTertiaryFixedVariant = scheme.getOnTertiaryFixedVariant().toColor()
    )
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Int.toColor(): Color = Color(this)

enum class PaletteStyle {
    TonalSpot,
    Neutral,
    Vibrant,
    Expressive,
    Rainbow,
    FruitSalad,
    Monochrome,
    Fidelity,
    Content,
}
