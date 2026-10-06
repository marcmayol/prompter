package com.marcmayol.prompter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.prompter.datos.Guion
import com.marcmayol.prompter.dominio.Texto

/** ~150 palabras por minuto: el ritmo habitual leyendo a cámara. */
fun duracionEstimada(texto: String): String {
    val segundos = (Texto.formas(texto).size / 150.0 * 60).toInt()
    return if (segundos < 60) "~$segundos s" else "~${segundos / 60} min ${segundos % 60} s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBiblioteca(
    guiones: List<Guion>,
    alAbrir: (String) -> Unit,
    alEditar: (String) -> Unit,
    alNuevo: () -> Unit,
    alBorrar: (String) -> Unit,
    alAjustes: () -> Unit,
    estadoActualizacion: com.marcm.actualizador.EstadoActualizacion = com.marcm.actualizador.EstadoActualizacion.Inactivo,
    alActualizar: () -> Unit = {},
) {
    var aBorrar by remember { mutableStateOf<Guion?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Marca.Rojo))
                        Spacer(Modifier.width(10.dp))
                        Text("Prompter", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = { IconButton(onClick = alAjustes) { Icon(Icons.Outlined.Settings, "Ajustes") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Marca.Fondo),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = alNuevo,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nuevo guion") },
                containerColor = Marca.Rojo,
                contentColor = androidx.compose.ui.graphics.Color.White,
            )
        },
    ) { pad ->
        if (guiones.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Aún no hay guiones", color = Marca.TextoSuave)
            }
        } else LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { BannerActualizacion(estadoActualizacion, alActualizar) }
            item {
                Text("GUIONES · ${guiones.size}", style = MaterialTheme.typography.labelSmall, color = Marca.TextoSuave,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            items(guiones, key = { it.id }) { g ->
                TarjetaGuion(g, alAbrir = { alAbrir(g.id) }, alEditar = { alEditar(g.id) }, alBorrar = { aBorrar = g })
            }
        }
    }
    aBorrar?.let { g ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            title = { Text("¿Borrar «${g.titulo}»?") },
            text = { Text("No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { alBorrar(g.id); aBorrar = null }) { Text("Borrar", color = Marca.Rojo) } },
            dismissButton = { TextButton(onClick = { aBorrar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TarjetaGuion(g: Guion, alAbrir: () -> Unit, alEditar: () -> Unit, alBorrar: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Marca.Superficie)
            .border(1.dp, Marca.Linea, RoundedCornerShape(14.dp)).clickable(onClick = alAbrir)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(g.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(g.texto.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(), color = Marca.TextoSuave,
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text("${Texto.formas(g.texto).size} PALABRAS · ${duracionEstimada(g.texto).uppercase()}",
                style = MaterialTheme.typography.labelSmall, color = Marca.TextoSuave)
        }
        IconButton(onClick = alAbrir) { Icon(Icons.Filled.PlayArrow, "Leer", tint = Marca.Rojo) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Más") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menu = false; alEditar() })
                DropdownMenuItem(text = { Text("Borrar") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; alBorrar() })
            }
        }
    }
}
