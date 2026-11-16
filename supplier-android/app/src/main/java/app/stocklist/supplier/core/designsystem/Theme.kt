package app.stocklist.supplier.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF7A3E0F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF2F1300),
    secondary = Color(0xFF6B5B4E),
    error = Color(0xFFB3261E),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68A),
    onPrimary = Color(0xFF4F2300),
    primaryContainer = Color(0xFF6B3410),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFD8C2B2),
    error = Color(0xFFF2B8B5),
)

@Composable
fun SupplierTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
