package com.marcmayol.prompter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Estilo del canal: fondo casi negro, texto hueso y el rojo de marca #D62828 como único acento. */
object Marca {
    val Fondo = Color(0xFF0E0E10)
    val Superficie = Color(0xFF18181B)
    val SuperficieAlta = Color(0xFF232327)
    val Linea = Color(0xFF2E2E33)
    val Texto = Color(0xFFF2EEE8)
    val TextoSuave = Color(0xFF9C9891)
    val Rojo = Color(0xFFD62828)
    val RojoOscuro = Color(0xFF5A1414)
}

private val esquema = darkColorScheme(
    primary = Marca.Rojo,
    onPrimary = Color.White,
    primaryContainer = Marca.RojoOscuro,
    onPrimaryContainer = Marca.Texto,
    secondary = Marca.Texto,
    onSecondary = Marca.Fondo,
    background = Marca.Fondo,
    onBackground = Marca.Texto,
    surface = Marca.Fondo,
    onSurface = Marca.Texto,
    surfaceVariant = Marca.Superficie,
    onSurfaceVariant = Marca.TextoSuave,
    surfaceContainer = Marca.Superficie,
    surfaceContainerHigh = Marca.SuperficieAlta,
    surfaceContainerHighest = Marca.SuperficieAlta,
    outline = Marca.Linea,
    outlineVariant = Marca.Linea,
    error = Color(0xFFFF6B6B),
)

private val base = Typography()
private val tipografia = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun TemaPrompter(contenido: @Composable () -> Unit) =
    MaterialTheme(colorScheme = esquema, typography = tipografia, content = contenido)
