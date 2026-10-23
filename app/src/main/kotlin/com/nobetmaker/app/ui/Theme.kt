package com.nobetmaker.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nobetmaker.core.export.Colors

private val Light = lightColorScheme(
    primary = Color(0xFF2B3A55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3F2),
    onPrimaryContainer = Color(0xFF16213A),
    secondary = Color(0xFFC62828),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDECEC),
    onSecondaryContainer = Color(0xFF5F1111),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F2F5),
    surfaceContainer = Color(0xFFF3F4F7),
    surfaceContainerHigh = Color(0xFFECEEF2),
    outline = Color(0xFFC8CDD5),
    outlineVariant = Color(0xFFE2E5EA),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAFC2E8),
    onPrimary = Color(0xFF14213A),
    primaryContainer = Color(0xFF2B3A55),
    onPrimaryContainer = Color(0xFFDCE3F2),
    secondary = Color(0xFFFF8A80),
    onSecondary = Color(0xFF3B0A0A),
    secondaryContainer = Color(0xFF4A1717),
    onSecondaryContainer = Color(0xFFFDECEC),
    background = Color(0xFF14171C),
    surface = Color(0xFF1B1F26),
    surfaceVariant = Color(0xFF242932),
    surfaceContainer = Color(0xFF20242C),
    surfaceContainerHigh = Color(0xFF2A2F38),
    outline = Color(0xFF4A515D),
    outlineVariant = Color(0xFF353B45),
)

@Composable
fun NobetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Kişi rengi; açık temada beyazla, koyu temada yüzeyle karıştırılmış "fosforlu kalem" tonu. */
@Composable
fun personTint(hex: String): Color {
    val base = Color(Colors.parse(hex))
    val surface = MaterialTheme.colorScheme.surface
    return if (isSystemInDarkTheme()) lerp(surface, base, 0.45f) else lerp(Color.White, base, 0.42f)
}

fun personColor(hex: String): Color = Color(Colors.parse(hex))
