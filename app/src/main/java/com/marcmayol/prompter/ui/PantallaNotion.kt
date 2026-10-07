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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.prompter.datos.Guion
import com.marcmayol.prompter.datos.RepositorioGuiones
import com.marcmayol.prompter.notion.AlmacenNotion
import com.marcmayol.prompter.notion.ClienteNotion
import com.marcmayol.prompter.notion.ExtractorGuion
import com.marcmayol.prompter.notion.FichaNotion
import kotlinx.coroutines.launch

/** Elegir una ficha de la base de Notion y traerse su guion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaNotion(
    almacen: AlmacenNotion,
    guiones: RepositorioGuiones,
    alImportar: (Guion) -> Unit,
    alVolver: () -> Unit,
) {
    var conectado by remember { mutableStateOf(almacen.conectado) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (conectado) almacen.nombreBase ?: "Notion" else "Conectar con Notion") },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Marca.Fondo),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (!conectado) Conectar(almacen) { conectado = true }
            else ListaFichas(almacen, guiones, alImportar, alDesconectar = { almacen.desconectar(); conectado = false })
        }
    }
}

@Composable
private fun Conectar(almacen: AlmacenNotion, alConectar: () -> Unit) {
    var token by remember { mutableStateOf("") }
    // App personal: viene con la base «YouTube» de Marc puesta. Sin el token no da acceso a nada.
    var url by remember { mutableStateOf(BASE_POR_DEFECTO) }
    var error by remember { mutableStateOf<String?>(null) }
    var probando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    val colores = OutlinedTextFieldDefaults.colors(focusedBorderColor = Marca.Rojo, cursorColor = Marca.Rojo, focusedLabelColor = Marca.Rojo, unfocusedBorderColor = Marca.Linea)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding()) {
        Text("Se hace una sola vez:", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        listOf(
            "En notion.so/my-integrations crea una integración interna y copia su «token» (empieza por ntn_).",
            "Abre en Notion la base con tus guiones, toca ··· › Conexiones y añade esa integración.",
            "Copia el enlace de la base (··· › Copiar enlace) y pégalo abajo junto al token.",
        ).forEachIndexed { i, paso ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("${i + 1}.", color = Marca.Rojo, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                Text(paso, color = Marca.TextoSuave, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(token, { token = it; error = null }, label = { Text("Token de la integración") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = colores, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(url, { url = it; error = null }, label = { Text("Enlace de la base de Notion") }, singleLine = true,
            colors = colores, modifier = Modifier.fillMaxWidth())
        Text("El token se guarda cifrado en este móvil y solo se usa para hablar con Notion.",
            style = MaterialTheme.typography.bodySmall, color = Marca.TextoSuave, modifier = Modifier.padding(top = 8.dp))
        error?.let { Text(it, color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = token.isNotBlank() && url.isNotBlank() && !probando,
            onClick = {
                probando = true
                alcance.launch {
                    try {
                        val fuente = ClienteNotion(token.trim()).fuenteDeDatos(url.trim())
                        almacen.guardarToken(token)
                        almacen.fuente = fuente
                        almacen.nombreBase = null
                        alConectar()
                    } catch (e: Exception) {
                        error = e.message ?: "No se pudo conectar"
                    } finally { probando = false }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Marca.Rojo, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (probando) "Comprobando…" else "Conectar") }
    }
}

@Composable
private fun ListaFichas(
    almacen: AlmacenNotion,
    guiones: RepositorioGuiones,
    alImportar: (Guion) -> Unit,
    alDesconectar: () -> Unit,
) {
    var fichas by remember { mutableStateOf<List<FichaNotion>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var importando by remember { mutableStateOf<String?>(null) }
    var formato by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableStateOf(0) }
    val alcance = rememberCoroutineScope()
    val cliente = remember { almacen.token()?.let { ClienteNotion(it) } }

    LaunchedEffect(recarga) {
        error = null
        fichas = null
        try {
            fichas = cliente!!.fichas(almacen.fuente!!)
        } catch (e: Exception) { error = e.message ?: "No se pudo leer la base" }
    }

    Column(Modifier.fillMaxSize()) {
        val formatos = fichas.orEmpty().mapNotNull { it.formato }.distinct()
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(formato == null, { formato = null }, { Text("Todos") }, colors = chip())
            formatos.forEach { f -> FilterChip(formato == f, { formato = f }, { Text(f) }, colors = chip()) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { recarga++ }) { Icon(Icons.Outlined.Refresh, "Recargar") }
        }
        when {
            error != null -> Column(Modifier.padding(16.dp)) {
                Text(error!!, color = Color(0xFFFF6B6B))
                TextButton(onClick = alDesconectar) { Text("Cambiar token o base", color = Marca.Rojo) }
            }
            fichas == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Marca.Rojo)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val visibles = fichas!!.filter { formato == null || it.formato == formato }
                    // Primero las que ya tienen guion escrito: son las que se pueden grabar.
                    .sortedBy { if (it.estado == "Guion") 0 else 1 }
                items(visibles, key = { it.id }) { f ->
                    val yaEsta = guiones.guiones.value.any { it.notionId == f.id.replace("-", "") || it.notionId == f.id }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Marca.Superficie)
                            .border(1.dp, Marca.Linea, RoundedCornerShape(12.dp))
                            .clickable(enabled = importando == null) {
                                importando = f.id
                                alcance.launch {
                                    try {
                                        val texto = ExtractorGuion.extraer(cliente!!.bloques(f.id))
                                        if (texto.isBlank()) error = "«${f.titulo}» no tiene texto de guion todavía"
                                        else alImportar(guiones.importar(f.id, f.titulo, texto))
                                    } catch (e: Exception) { error = e.message ?: "No se pudo importar" }
                                    finally { importando = null }
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.titulo, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(f.formato, f.estado, "ya importado".takeIf { yaEsta }).joinToString(" · ").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (f.estado == "Guion") Marca.Rojo else Marca.TextoSuave)
                        }
                        if (importando == f.id) CircularProgressIndicator(color = Marca.Rojo, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.CloudDownload, "Importar", tint = Marca.TextoSuave)
                    }
                }
                item {
                    TextButton(onClick = alDesconectar, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Desconectar Notion", color = Marca.TextoSuave)
                    }
                }
            }
        }
    }
}

private const val BASE_POR_DEFECTO = "https://www.notion.so/5040e820b60a4e1e9dfb31ab48307329"

@Composable
private fun chip() = FilterChipDefaults.filterChipColors(selectedContainerColor = Marca.RojoOscuro, selectedLabelColor = Color.White)
