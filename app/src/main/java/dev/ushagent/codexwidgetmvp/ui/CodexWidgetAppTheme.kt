package dev.ushagent.codexwidgetmvp.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val LightAppColors = lightColorScheme(
  background = Color(0xFFF5F1EA),
  surface = Color(0xFFFFFDF8),
  surfaceVariant = Color(0xFFFAF7F2),
  primary = Color(0xFF123B52),
  onPrimary = Color(0xFFF8FAFC),
  secondaryContainer = Color(0xFFE3EDF5),
  onSecondaryContainer = Color(0xFF123B52),
  tertiary = Color(0xFFD0682B),
  onTertiary = Color(0xFFFFF8F1),
  onSurface = Color(0xFF1A1F24),
  onSurfaceVariant = Color(0xFF39424C),
)

private val DarkAppColors = darkColorScheme(
  background = Color(0xFF10161C),
  surface = Color(0xFF171E25),
  surfaceVariant = Color(0xFF202A34),
  primary = Color(0xFF8BC8F2),
  onPrimary = Color(0xFF06263B),
  secondaryContainer = Color(0xFF1F3442),
  onSecondaryContainer = Color(0xFFD6EAF8),
  tertiary = Color(0xFFFFB68A),
  onTertiary = Color(0xFF4C2105),
  onSurface = Color(0xFFE8EEF4),
  onSurfaceVariant = Color(0xFFB9C5D1),
)

@Composable
fun CodexWidgetAppTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val darkTheme = isSystemInDarkTheme()
  val colors = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> dynamicLightColorScheme(context)
    darkTheme -> DarkAppColors
    else -> LightAppColors
  }

  MaterialTheme(
    colorScheme = colors,
    content = content,
  )
}
