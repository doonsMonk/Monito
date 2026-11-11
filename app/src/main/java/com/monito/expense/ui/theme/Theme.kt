package com.monito.expense.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.monito.expense.data.ThemeMode

/** Colours the original Monito used for amounts: red for expenses, green for incomes. */
data class MonitoColors(
    val expense: Color,
    val income: Color,
    val expenseContainer: Color,
    val incomeContainer: Color,
    val isDark: Boolean,
)

val LocalMonitoColors = staticCompositionLocalOf {
    MonitoColors(
        expense = Color(0xFFC62828),
        income = Color(0xFF2E7D32),
        expenseContainer = Color(0x1AC62828),
        incomeContainer = Color(0x1A2E7D32),
        isDark = false,
    )
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B3D10),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00332D),
    tertiary = Color(0xFF5C6BC0),
    error = Color(0xFFC62828),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1B1B1B),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFF9E9E9E),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0B3D10),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00332D),
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFF9FA8DA),
    error = Color(0xFFE57373),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2E2E2E),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF757575),
)

@Composable
fun MonitoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val monito = if (dark) MonitoColors(
        expense = Color(0xFFE57373),
        income = Color(0xFF81C784),
        expenseContainer = Color(0x33E57373),
        incomeContainer = Color(0x3381C784),
        isDark = true,
    ) else MonitoColors(
        expense = Color(0xFFC62828),
        income = Color(0xFF2E7D32),
        expenseContainer = Color(0x1AC62828),
        incomeContainer = Color(0x1A2E7D32),
        isDark = false,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalMonitoColors provides monito) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}

/** Parses the `color.color_code` hex string (RRGGBB without '#') stored in the database. */
fun categoryColor(code: String?): Color =
    runCatching { Color(0xFF000000L or code!!.trim().removePrefix("#").toLong(16)) }.getOrDefault(Color(0xFF9E9E9E))

/** Text colour that reads on a category colour; `color_text_type` 0 means the colour is light. */
fun categoryTextColor(textType: Int): Color = if (textType == 0) Color(0xFF1B1B1B) else Color.White
