package app.stocklist.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1B5E50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2DC),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635C),
    error = Color(0xFFB3261E),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AD6C0),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFFA6F2DC),
    secondary = Color(0xFFB1CCC3),
    error = Color(0xFFF2B8B5),
)

@Composable
fun StockListTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
