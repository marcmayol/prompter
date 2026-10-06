package com.marcmayol.prompter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcm.actualizador.Actualizador
import com.marcm.actualizador.EstadoActualizacion
import com.marcm.actualizador.Modo
import com.marcm.actualizador.TipoError
import com.marcmayol.prompter.BuildConfig
import kotlinx.coroutines.launch

/** Aviso no bloqueante en la biblioteca: solo aparece si hay versión nueva o una en marcha. */
@Composable
fun BannerActualizacion(estado: EstadoActualizacion, alActualizar: () -> Unit, modifier: Modifier = Modifier) {
    val visible = estado is EstadoActualizacion.Disponible || estado is EstadoActualizacion.Descargando ||
        estado is EstadoActualizacion.Verificando || estado is EstadoActualizacion.PidiendoPermiso ||
        estado is EstadoActualizacion.Instalando
    AnimatedVisibility(visible, modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Marca.Superficie)
                .border(1.dp, Marca.Rojo, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (estado) {
                is EstadoActualizacion.Disponible -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prompter ${estado.info.versionName} disponible", fontWeight = FontWeight.Bold)
                        if (estado.info.notas.isNotBlank()) Text(estado.info.notas, color = Marca.TextoSuave,
                            style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                    Button(onClick = alActualizar, colors = ButtonDefaults.buttonColors(containerColor = Marca.Rojo, contentColor = Color.White)) {
                        Text("Actualizar")
                    }
                }
                is EstadoActualizacion.Descargando -> Progreso("Descargando la actualización", "${estado.porcentaje} %", estado.porcentaje / 100f)
                EstadoActualizacion.Verificando -> Progreso("Comprobando el archivo", "Verificando que la descarga es íntegra", null)
                EstadoActualizacion.PidiendoPermiso -> Progreso("Falta un permiso", "Autoriza a Prompter a instalar aplicaciones y volvemos aquí", null)
                EstadoActualizacion.Instalando -> Progreso("Instalando", "Prompter se reiniciará al terminar", null)
                else -> Unit
            }
        }
    }
}

@Composable
private fun Progreso(titulo: String, detalle: String, fraccion: Float?) {
    Text(titulo, fontWeight = FontWeight.Bold)
    Text(detalle, color = Marca.TextoSuave, style = MaterialTheme.typography.bodySmall)
    val m = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
    if (fraccion != null) LinearProgressIndicator(progress = { fraccion.coerceIn(0f, 1f) }, color = Marca.Rojo, trackColor = Marca.Linea, modifier = m)
    else LinearProgressIndicator(color = Marca.Rojo, trackColor = Marca.Linea, modifier = m)
}

/** Sección de Ajustes: versión instalada, búsqueda automática y comprobación a mano. */
@Composable
fun SeccionActualizaciones(actualizador: Actualizador) {
    val estado by actualizador.estado.collectAsState()
    var auto by remember { mutableStateOf(actualizador.buscarAutomaticamente) }
    val alcance = rememberCoroutineScope()
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("Prompter ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Buscar actualizaciones automáticamente", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(auto, { auto = it; actualizador.buscarAutomaticamente = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Marca.Rojo))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { alcance.launch { actualizador.comprobar(Modo.MANUAL) } },
                enabled = estado != EstadoActualizacion.Comprobando) {
                Text("Buscar ahora", color = Marca.Rojo, fontWeight = FontWeight.Bold)
            }
            if (estado is EstadoActualizacion.Disponible) TextButton(onClick = { actualizador.actualizarAhora() }) {
                Text("Actualizar", color = Marca.Rojo, fontWeight = FontWeight.Bold)
            }
        }
        val texto = mensajeDe(estado)
        if (texto.isNotEmpty()) Text(texto, style = MaterialTheme.typography.bodySmall,
            color = if (estado is EstadoActualizacion.Error) Color(0xFFFF6B6B) else Marca.TextoSuave)
    }
}

private fun mensajeDe(estado: EstadoActualizacion): String = when (estado) {
    EstadoActualizacion.Comprobando -> "Comprobando…"
    EstadoActualizacion.AlDia -> "Estás al día"
    is EstadoActualizacion.Disponible -> "Hay una versión nueva: ${estado.info.versionName}"
    is EstadoActualizacion.Descargando -> "Descargando… ${estado.porcentaje} %"
    EstadoActualizacion.Verificando -> "Comprobando el archivo…"
    EstadoActualizacion.PidiendoPermiso -> "Falta el permiso para instalar"
    EstadoActualizacion.Instalando -> "Instalando…"
    is EstadoActualizacion.Error -> when (estado.tipo) {
        TipoError.SIN_RED -> "Sin conexión"
        TipoError.HTTP -> "El servidor no responde"
        TipoError.MANIFIESTO -> "No se pudo leer la información de versiones"
        TipoError.DESCARGA -> "No se pudo descargar la actualización"
        TipoError.HASH -> "El archivo descargado no era válido"
        TipoError.INSTALACION -> estado.mensaje ?: "No se pudo instalar"
    }
    EstadoActualizacion.Inactivo -> ""
}
