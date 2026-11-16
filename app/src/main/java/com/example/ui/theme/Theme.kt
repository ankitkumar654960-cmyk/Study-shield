package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudyDarkColorScheme =
    darkColorScheme(
        primary = EmeraldGlow,
        onPrimary = Color(0xFF032318),
        primaryContainer = EmeraldContainerDark,
        onPrimaryContainer = Color(0xFFA7F3D0),
        secondary = AmberXp,
        onSecondary = Color(0xFF281400),
        secondaryContainer = AmberContainerDark,
        onSecondaryContainer = Color(0xFFFDE68A),
        tertiary = SapphireFocus,
        onTertiary = Color(0xFF00263A),
        tertiaryContainer = SapphireContainerDark,
        onTertiaryContainer = Color(0xFFBAE6FD),
        background = MidnightBg,
        onBackground = InkPrimaryDark,
        surface = MidnightSurface,
        onSurface = InkPrimaryDark,
        surfaceVariant = MidnightSurfaceVariant,
        onSurfaceVariant = InkMutedDark,
        surfaceContainerHigh = MidnightSurfaceHigh,
        outline = MidnightOutline,
        error = ShieldRose,
        onError = Color.White,
        errorContainer = ShieldRoseDark,
        onErrorContainer = Color(0xFFFFCCD5),
    )

private val StudyLightColorScheme =
    lightColorScheme(
        primary = EmeraldDeep,
        onPrimary = Color.White,
        primaryContainer = EmeraldContainerLight,
        onPrimaryContainer = Color(0xFF064E3B),
        secondary = AmberDeep,
        onSecondary = Color.White,
        secondaryContainer = AmberContainerLight,
        onSecondaryContainer = Color(0xFF78350F),
        tertiary = SapphireDeep,
        onTertiary = Color.White,
        tertiaryContainer = SapphireContainerLight,
        onTertiaryContainer = Color(0xFF0C4A6E),
        background = IvoryBg,
        onBackground = InkPrimaryLight,
        surface = IvorySurface,
        onSurface = InkPrimaryLight,
        surfaceVariant = IvorySurfaceVariant,
        onSurfaceVariant = InkMutedLight,
        surfaceContainerHigh = IvorySurfaceVariant,
        outline = IvoryOutline,
        error = ShieldRose,
        onError = Color.White,
        errorContainer = ShieldRoseLight,
        onErrorContainer = Color(0xFF881337),
    )

private val StudyShieldShapes =
    Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

@Composable
fun StudyShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) StudyDarkColorScheme else StudyLightColorScheme
  MaterialTheme(
      colorScheme = colorScheme,
      typography = StudyShieldTypography,
      shapes = StudyShieldShapes,
      content = content,
  )
}
