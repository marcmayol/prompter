package com.marcmayol.prompter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcmayol.prompter.datos.Guion
import com.marcmayol.prompter.dominio.Texto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEditor(guion: Guion?, alGuardar: (Guion) -> Unit, alVolver: () -> Unit) {
    var titulo by rememberSaveable { mutableStateOf2(guion?.titulo ?: "") }
    var texto by rememberSaveable { mutableStateOf2(guion?.texto ?: "") }
    val valido = texto.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (guion == null) "Nuevo guion" else "Editar guion") },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    TextButton(enabled = valido, onClick = {
                        val t = titulo.ifBlank { texto.lineSequence().first { it.isNotBlank() }.take(60) }
                        alGuardar(guion?.copy(titulo = t, texto = texto) ?: Guion(titulo = t, texto = texto))
                    }) { Text("Guardar", fontWeight = FontWeight.Bold, color = if (valido) Marca.Rojo else Marca.TextoSuave) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Marca.Fondo),
            )
        },
    ) { pad ->
        val colores = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Marca.Rojo, cursorColor = Marca.Rojo, focusedLabelColor = Marca.Rojo,
            unfocusedBorderColor = Marca.Linea,
        )
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).imePadding()) {
            OutlinedTextField(titulo, { titulo = it }, label = { Text("Título") }, singleLine = true,
                colors = colores, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(texto, { texto = it }, label = { Text("Texto que vas a leer") },
                placeholder = { Text("Pega aquí el guion…") }, colors = colores,
                modifier = Modifier.fillMaxWidth().weight(1f))
            Text("${Texto.formas(texto).size} palabras · ${duracionEstimada(texto)}",
                style = MaterialTheme.typography.labelSmall, color = Marca.TextoSuave,
                modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

private fun mutableStateOf2(v: String) = androidx.compose.runtime.mutableStateOf(v)
