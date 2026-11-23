package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class WealthExtendedColors(
    val gain: Color,
    val gainContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val goldAccent: Color,
    val stockColor: Color,
    val etfColor: Color,
    val cashColor: Color,
    val commodityColor: Color,
    val heroCardStart: Color,
    val heroCardEnd: Color,
    val onHeroCard: Color,
    val onHeroCardMuted: Color,
    val heroBadgeGain: Color,
    val heroBadgeLoss: Color,
    val heroBadgeSurface: Color
)

private val LightExtendedColors = WealthExtendedColors(
    gain = LightGain,
    gainContainer = LightGainContainer,
    loss = LightLoss,
    lossContainer = LightLossContainer,
    goldAccent = LightGoldAccent,
    stockColor = LightStockColor,
    etfColor = LightEtfColor,
    cashColor = LightCashColor,
    commodityColor = LightCommodityColor,
    heroCardStart = Color(0xFF132922),
    heroCardEnd = Color(0xFF1E3B31),
    onHeroCard = Color(0xFFF6F3EB),
    onHeroCardMuted = Color(0xFFB7C4BD),
    heroBadgeGain = Color(0xFF3CCB88),
    heroBadgeLoss = Color(0xFFF06E6E),
    heroBadgeSurface = Color.White.copy(alpha = 0.10f)
)

private val DarkExtendedColors = WealthExtendedColors(
    gain = DarkGain,
    gainContainer = DarkGainContainer,
    loss = DarkLoss,
    lossContainer = DarkLossContainer,
    goldAccent = DarkGoldAccent,
    stockColor = DarkStockColor,
    etfColor = DarkEtfColor,
    cashColor = DarkCashColor,
    commodityColor = DarkCommodityColor,
    heroCardStart = Color(0xFF14211D),
    heroCardEnd = Color(0xFF1C2D27),
    onHeroCard = Color(0xFFF2EFE9),
    onHeroCardMuted = Color(0xFF9FB0A8),
    heroBadgeGain = Color(0xFF3CCB88),
    heroBadgeLoss = Color(0xFFF06E6E),
    heroBadgeSurface = Color.White.copy(alpha = 0.10f)
)

val LocalWealthColors = staticCompositionLocalOf { LightExtendedColors }

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.6f),
    error = DarkLoss,
    errorContainer = DarkLossContainer
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.6f),
    error = LightLoss,
    errorContainer = LightLossContainer
)

val ValorisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ValorisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalWealthColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ValorisTypography,
            shapes = ValorisShapes,
            content = content
        )
    }
}
