package dev.ushagent.codexwidgetmvp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = lightColorScheme(
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

@Composable
fun CodexWidgetAppTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AppColors,
    content = content,
  )
}
