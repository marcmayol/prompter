package com.marcmayol.prompter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcmayol.prompter.datos.Ajustes
import com.marcmayol.prompter.datos.Calidad
import com.marcmayol.prompter.datos.Camara
import com.marcmayol.prompter.datos.Formato
import com.marcmayol.prompter.datos.Fuente
import com.marcmayol.prompter.datos.Seguimiento
import kotlin.math.roundToInt

private val coloresTexto = listOf(0xFFF2EEE8, 0xFFFFFFFF, 0xFFFFE066, 0xFF7BD389, 0xFF8EC5FF, 0xFFFFB3C1, 0xFF0E0E10, 0xFF000000)
private val coloresFondo = listOf(0xFF0E0E10, 0xFF000000, 0xFF1B1F2A, 0xFF13261C, 0xFF2A1515, 0xFF3A3A40, 0xFFF2EEE8, 0xFFFFFFFF)
private val coloresMarcador = listOf(0xFFD62828, 0xFFFF6B35, 0xFFFFD23F, 0xFF3DDC97, 0xFF4EA8FF, 0xFFB388FF, 0xFFFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(
    ajustes: Ajustes,
    alCambiar: (Ajustes) -> Unit,
    alRestablecer: () -> Unit,
    alVolver: () -> Unit,
    actualizador: com.marcm.actualizador.Actualizador? = null,
    notion: com.marcmayol.prompter.notion.AlmacenNotion? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = { TextButton(onClick = alRestablecer) { Text("Restablecer", color = Marca.TextoSuave) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Marca.Fondo),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Muestra(ajustes)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            ) {
                Seccion("TEXTO")
                Paleta("Color del texto", coloresTexto, ajustes.colorTexto) { alCambiar(ajustes.copy(colorTexto = it)) }
                Paleta("Color del fondo", coloresFondo, ajustes.colorFondo) { alCambiar(ajustes.copy(colorFondo = it)) }
                Paleta("Color de la línea de lectura", coloresMarcador, ajustes.colorMarcador) { alCambiar(ajustes.copy(colorMarcador = it)) }
                Opciones("Tipo de letra", Fuente.entries, ajustes.fuente, { it.etiqueta }) { alCambiar(ajustes.copy(fuente = it)) }
                Deslizador("Tamaño de letra", ajustes.tamanoLetra, 18f..72f, "${ajustes.tamanoLetra.roundToInt()} sp") { alCambiar(ajustes.copy(tamanoLetra = it)) }
                Deslizador("Interlineado", ajustes.interlineado, 1.0f..2.2f, "%.2f".format(ajustes.interlineado)) { alCambiar(ajustes.copy(interlineado = it)) }
                Deslizador("Márgenes", ajustes.margen, 0f..80f, "${ajustes.margen.roundToInt()} dp") { alCambiar(ajustes.copy(margen = it)) }
                Deslizador("Ancho de la columna", ajustes.anchoColumna, 0.5f..1f, "${(ajustes.anchoColumna * 100).roundToInt()} %") { alCambiar(ajustes.copy(anchoColumna = it)) }
                Deslizador("Texto ya leído", ajustes.opacidadLeido, 0.1f..1f, "${(ajustes.opacidadLeido * 100).roundToInt()} %") { alCambiar(ajustes.copy(opacidadLeido = it)) }
                Deslizador("Altura de la línea de lectura", ajustes.alturaLectura, 0.1f..0.7f, "${(ajustes.alturaLectura * 100).roundToInt()} % desde arriba") { alCambiar(ajustes.copy(alturaLectura = it)) }
                Interruptor("Modo espejo", "Para usarlo con un cristal de teleprompter", ajustes.espejo) { alCambiar(ajustes.copy(espejo = it)) }

                Seccion("SEGUIMIENTO")
                Opciones("Cómo avanza el texto", Seguimiento.entries, ajustes.seguimiento, {
                    when (it) { Seguimiento.VOZ -> "Sigue mi voz"; Seguimiento.AUTOMATICO -> "Automático"; Seguimiento.MANUAL -> "Manual" }
                }) { alCambiar(ajustes.copy(seguimiento = it)) }
                Deslizador("Sensibilidad de la voz", ajustes.sensibilidad.toFloat(), 1f..5f,
                    listOf("", "Muy exigente", "Exigente", "Normal", "Permisiva", "Muy permisiva")[ajustes.sensibilidad], pasos = 3,
                ) { alCambiar(ajustes.copy(sensibilidad = it.roundToInt())) }
                Texto2("Exigente: avanza solo cuando está muy seguro. Permisiva: avanza antes, aunque entienda peor.")
                Deslizador("Adelanto del texto", ajustes.adelanto.toFloat(), 0f..8f, "${ajustes.adelanto} palabras", pasos = 7) {
                    alCambiar(ajustes.copy(adelanto = it.roundToInt()))
                }
                Texto2("Cuántas palabras por delante de tu voz se coloca la línea. Súbelo si notas que el texto va tarde.")
                Deslizador("Velocidad automática", ajustes.velocidadAuto, 10f..150f, "${ajustes.velocidadAuto.roundToInt()} dp/s") { alCambiar(ajustes.copy(velocidadAuto = it)) }
                Deslizador("Cuenta atrás antes de grabar", ajustes.cuentaAtras.toFloat(), 0f..10f, "${ajustes.cuentaAtras} s", pasos = 9) { alCambiar(ajustes.copy(cuentaAtras = it.roundToInt())) }

                Seccion("GRABACIÓN")
                Opciones("Cámara", Camara.entries, ajustes.camara, { if (it == Camara.FRONTAL) "Frontal" else "Trasera" }) { alCambiar(ajustes.copy(camara = it)) }
                Opciones("Formato", Formato.entries, ajustes.formato, { it.etiqueta }) { alCambiar(ajustes.copy(formato = it)) }
                Opciones("Calidad", Calidad.entries, ajustes.calidad, { it.etiqueta }) { alCambiar(ajustes.copy(calidad = it)) }
                Texto2("Si el móvil no la admite con esa cámara, se usa la mejor que tenga por debajo.")
                Interruptor("60 fotogramas por segundo", "Movimiento más suave; archivos más grandes", ajustes.fps60) { alCambiar(ajustes.copy(fps60 = it)) }
                Interruptor("Estabilización", "Quita el temblor si sujetas el móvil con la mano", ajustes.estabilizacion) { alCambiar(ajustes.copy(estabilizacion = it)) }
                Interruptor("HDR (10 bits)", "Más rango de luces y sombras. Algunos editores lo ven lavado", ajustes.hdr) { alCambiar(ajustes.copy(hdr = it)) }
                Interruptor("Máxima calidad de compresión", "Más tasa de bits que la de serie", ajustes.tasaAlta) { alCambiar(ajustes.copy(tasaAlta = it)) }
                Deslizador("Exposición", ajustes.exposicion, -2f..2f, "%+.1f EV".format(ajustes.exposicion)) { alCambiar(ajustes.copy(exposicion = it)) }
                Deslizador("Zoom", ajustes.zoom, 0.5f..3f, "%.1f×".format(ajustes.zoom)) { alCambiar(ajustes.copy(zoom = it)) }
                Texto2("Si una opción no la admite la cámara, se ignora y el prompter te lo dice arriba.")
                Interruptor("Ver la cámara", "Miniatura en la esquina para encuadrarte", ajustes.verCamara) { alCambiar(ajustes.copy(verCamara = it)) }

                if (notion != null) {
                    Seccion("NOTION")
                    var marcar by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(notion.marcarGrabado) }
                    var conectado by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(notion.conectado) }
                    Text(if (conectado) "Conectado a tu base de guiones" else "Sin conectar: hazlo desde el botón de Notion de la biblioteca",
                        style = MaterialTheme.typography.bodyMedium, color = Marca.TextoSuave)
                    Interruptor("Marcar como «Grabado» en Notion", "Al guardar un vídeo de un guion importado, cambia su Estado en la base", marcar) {
                        marcar = it; notion.marcarGrabado = it
                    }
                    if (conectado) TextButton(onClick = { notion.desconectar(); conectado = false }) { Text("Desconectar Notion", color = Marca.Rojo) }
                }

                Seccion("APLICACIÓN")
                if (actualizador != null) SeccionActualizaciones(actualizador)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Vista previa en vivo de cómo se verá el texto. */
@Composable
private fun Muestra(a: Ajustes) {
    val texto = Color(a.colorTexto)
    Box(
        Modifier.fillMaxWidth().height(150.dp).background(Color(a.colorFondo)).padding(vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = texto.copy(alpha = a.opacidadLeido))) { append("Tu LLM escribe palabra a palabra, ") }
                append("y cada palabra tiene que releer todo lo anterior.")
            },
            style = TextStyle(color = texto, fontSize = a.tamanoLetra.sp, lineHeight = (a.tamanoLetra * a.interlineado).sp,
                fontFamily = a.fuente.familia(), fontWeight = FontWeight.SemiBold),
            modifier = Modifier.fillMaxWidth(a.anchoColumna).padding(horizontal = a.margen.dp)
                .graphicsLayer { if (a.espejo) scaleX = -1f },
        )
        Box(Modifier.align(Alignment.CenterStart).size(width = 4.dp, height = 28.dp).background(Color(a.colorMarcador)))
    }
}

@Composable
private fun Seccion(t: String) {
    Text(t, style = MaterialTheme.typography.labelSmall, color = Marca.Rojo, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
}

@Composable
private fun Texto2(t: String) {
    Text(t, style = MaterialTheme.typography.bodySmall, color = Marca.TextoSuave, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Paleta(titulo: String, colores: List<Long>, actual: Long, alElegir: (Long) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colores.forEach { c ->
                val color = Color(c)
                val elegido = c == actual
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(color)
                        .border(if (elegido) 3.dp else 1.dp, if (elegido) Marca.Rojo else Marca.Linea, CircleShape)
                        .clickable { alElegir(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (elegido) Icon(Icons.Filled.Check, "Elegido", tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun <T> Opciones(titulo: String, opciones: List<T>, actual: T, etiqueta: (T) -> String, alElegir: (T) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opciones.forEach { o ->
                FilterChip(
                    selected = o == actual, onClick = { alElegir(o) }, label = { Text(etiqueta(o)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Marca.RojoOscuro, selectedLabelColor = Color.White),
                )
            }
        }
    }
}

@Composable
fun Deslizador(
    titulo: String, valor: Float, rango: ClosedFloatingPointRange<Float>, texto: String, pasos: Int = 0, alCambiar: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(texto, style = MaterialTheme.typography.labelSmall, color = Marca.TextoSuave)
        }
        Slider(
            value = valor.coerceIn(rango), onValueChange = alCambiar, valueRange = rango, steps = pasos,
            colors = SliderDefaults.colors(thumbColor = Marca.Rojo, activeTrackColor = Marca.Rojo, inactiveTrackColor = Marca.Linea),
        )
    }
}

@Composable
private fun Interruptor(titulo: String, detalle: String, valor: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { alCambiar(!valor) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = Marca.TextoSuave)
        }
        Spacer(Modifier.width(12.dp))
        Switch(valor, alCambiar, colors = SwitchDefaults.colors(checkedTrackColor = Marca.Rojo))
    }
}

/** Lo justo para retocar sin salir del prompter. */
@Composable
fun AjustesRapidos(a: Ajustes, alCambiar: (Ajustes) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Ajustes rápidos", style = MaterialTheme.typography.titleLarge)
        Deslizador("Tamaño de letra", a.tamanoLetra, 18f..72f, "${a.tamanoLetra.roundToInt()} sp") { alCambiar(a.copy(tamanoLetra = it)) }
        when (a.seguimiento) {
            Seguimiento.VOZ -> {
                Deslizador("Sensibilidad de la voz", a.sensibilidad.toFloat(), 1f..5f, "${a.sensibilidad} / 5", pasos = 3) {
                    alCambiar(a.copy(sensibilidad = it.roundToInt()))
                }
                Deslizador("Adelanto del texto", a.adelanto.toFloat(), 0f..8f, "${a.adelanto} palabras", pasos = 7) {
                    alCambiar(a.copy(adelanto = it.roundToInt()))
                }
            }
            Seguimiento.AUTOMATICO -> Deslizador("Velocidad", a.velocidadAuto, 10f..150f, "${a.velocidadAuto.roundToInt()} dp/s") {
                alCambiar(a.copy(velocidadAuto = it))
            }
            Seguimiento.MANUAL -> {}
        }
        Deslizador("Altura de la línea de lectura", a.alturaLectura, 0.1f..0.7f, "${(a.alturaLectura * 100).roundToInt()} %") {
            alCambiar(a.copy(alturaLectura = it))
        }
        Deslizador("Ancho de la columna", a.anchoColumna, 0.5f..1f, "${(a.anchoColumna * 100).roundToInt()} %") { alCambiar(a.copy(anchoColumna = it)) }
    }
}
