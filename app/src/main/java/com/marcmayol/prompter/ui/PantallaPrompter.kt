package com.marcmayol.prompter.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcmayol.prompter.Estado
import com.marcmayol.prompter.SesionPrompter
import com.marcmayol.prompter.datos.Ajustes
import com.marcmayol.prompter.datos.Formato
import com.marcmayol.prompter.datos.Fuente
import com.marcmayol.prompter.datos.Guion
import com.marcmayol.prompter.datos.Seguimiento
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

fun Fuente.familia() = when (this) {
    Fuente.SANS -> FontFamily.SansSerif
    Fuente.SERIF -> FontFamily.Serif
    Fuente.MONO -> FontFamily.Monospace
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrompter(
    guion: Guion,
    ajustes: Ajustes,
    alCambiarAjustes: (Ajustes) -> Unit,
    alVolver: () -> Unit,
    alAjustes: () -> Unit,
    sesion: SesionPrompter = viewModel(),
) {
    val context = LocalContext.current
    val vista = LocalView.current
    val duenio = LocalLifecycleOwner.current

    val estado by sesion.estado.collectAsState()
    val posicion by sesion.posicion.collectAsState()
    val nivel by sesion.nivel.collectAsState()
    val oido by sesion.oido.collectAsState()

    // --- Permisos: cámara y micro, los dos a la vez ---
    fun concedido(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var permisos by remember { mutableStateOf(concedido(Manifest.permission.CAMERA) && concedido(Manifest.permission.RECORD_AUDIO)) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        permisos = r.values.all { it }
    }
    LaunchedEffect(Unit) {
        if (!permisos) pedir.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    // --- Orientación según el formato elegido, y pantalla siempre encendida ---
    DisposableEffect(ajustes.formato) {
        val act = context as? Activity
        act?.requestedOrientation = if (ajustes.formato == Formato.HORIZONTAL)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        vista.keepScreenOn = true
        onDispose {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            vista.keepScreenOn = false
        }
    }

    LaunchedEffect(guion.id, guion.texto) { sesion.preparar(guion.texto, ajustes.sensibilidad) }
    LaunchedEffect(ajustes.sensibilidad) { sesion.cambiarSensibilidad(ajustes.sensibilidad) }

    DisposableEffect(permisos) {
        if (permisos) sesion.abrirMicro()
        onDispose { sesion.pausar(); sesion.cerrarMicro() }
    }

    // --- Cámara ---
    val previa = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val rotacion = vista.display?.rotation ?: 0
    LaunchedEffect(permisos, ajustes.camara, ajustes.calidad, ajustes.fps60, ajustes.estabilizacion, ajustes.hdr, ajustes.tasaAlta, rotacion) {
        if (permisos) runCatching {
            sesion.grabadora.vincular(duenio, previa.surfaceProvider, ajustes, rotacion)
        }
    }
    LaunchedEffect(ajustes.zoom, ajustes.exposicion) { sesion.grabadora.aplicarControles(ajustes) }
    val avisoCamara by sesion.grabadora.aviso.collectAsState()

    val enMarcha = estado is Estado.EnMarcha
    val grabando = (estado as? Estado.EnMarcha)?.grabando == true
    var ajustesRapidos by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(ajustes.colorFondo))) {
        TextoQueSeMueve(
            texto = guion.texto,
            ajustes = ajustes,
            posicion = posicion,
            enMarcha = enMarcha,
            palabras = { sesion.palabras },
            alMoverAMano = sesion::saltarA,
        )

        // Arriba, un degradado del color de fondo: lo ya leído se funde y los rótulos se leen bien.
        // Nunca llega a la línea de lectura: como mucho, tres cuartos de lo que hay por encima.
        val altoPantalla = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
        val altoDegradado = minOf(170f, altoPantalla * ajustes.alturaLectura * 0.75f).dp
        Box(
            Modifier.fillMaxWidth().height(altoDegradado).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color(ajustes.colorFondo), 0.6f to Color(ajustes.colorFondo).copy(alpha = 0.85f), 1f to Color.Transparent,
                ),
            ),
        )

        // Arriba: estado, vúmetro y miniatura de la cámara
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                AnimatedVisibility(!grabando) {
                    IconButton(onClick = alVolver, modifier = Modifier.clip(CircleShape).background(Color(0x66000000))) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Insignia(estado, ajustes.seguimiento, nivel, oido)
                avisoCamara?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB3B3),
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x88000000)).padding(6.dp))
                }
            }
            if (ajustes.verCamara && permisos) {
                val ancho = if (ajustes.formato == Formato.HORIZONTAL) 112.dp else 96.dp
                val alto = if (ajustes.formato == Formato.HORIZONTAL) 63.dp else 128.dp
                Box(
                    Modifier.size(ancho, alto).clip(RoundedCornerShape(12.dp))
                        .border(2.dp, if (grabando) Marca.Rojo else Color(0x55FFFFFF), RoundedCornerShape(12.dp)),
                ) { AndroidView({ previa }, Modifier.fillMaxSize()) }
            }
        }

        // Abajo: controles
        Controles(
            modifier = Modifier.align(Alignment.BottomCenter),
            estado = estado,
            ajustes = ajustes,
            alEnsayar = { if (enMarcha) sesion.pausar() else sesion.empezar(false, 0) },
            alGrabar = { if (grabando) sesion.pausar() else sesion.empezar(true, ajustes.cuentaAtras) },
            alReiniciar = sesion::reiniciar,
            alCambiarModo = {
                val siguiente = Seguimiento.entries[(ajustes.seguimiento.ordinal + 1) % Seguimiento.entries.size]
                alCambiarAjustes(ajustes.copy(seguimiento = siguiente))
            },
            alAjustesRapidos = { ajustesRapidos = true },
            alAjustes = alAjustes,
        )

        // Cuenta atrás, a pantalla completa
        (estado as? Estado.CuentaAtras)?.let { c ->
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { sesion.pausar() }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${c.quedan}", fontSize = 140.sp, fontWeight = FontWeight.Black, color = if (c.grabar) Marca.Rojo else Color.White)
                    Text(if (c.grabar) "GRABANDO EN…" else "EMPIEZA EN…", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Spacer(Modifier.height(24.dp))
                    Text("Toca para cancelar", color = Marca.TextoSuave)
                }
            }
        }

        if (!permisos) AvisoPermisos(Modifier.align(Alignment.Center)) {
            pedir.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }

        Aviso(estado, Modifier.align(Alignment.Center), sesion::cerrarAviso)
    }

    if (ajustesRapidos) {
        ModalBottomSheet(onDismissRequest = { ajustesRapidos = false }, containerColor = Marca.Superficie) {
            AjustesRapidos(ajustes, alCambiarAjustes)
        }
    }
}

/** El guion, desplazándose para que la siguiente palabra a leer quede en la línea de lectura. */
@Composable
private fun TextoQueSeMueve(
    texto: String,
    ajustes: Ajustes,
    posicion: Int,
    enMarcha: Boolean,
    palabras: () -> List<com.marcmayol.prompter.dominio.Palabra>,
    alMoverAMano: (Int) -> Unit,
) {
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var tocando by remember { mutableStateOf(false) }
    var resincronizar by remember { mutableStateOf(false) }
    var automatico by remember { mutableStateOf(false) }
    val densidad = LocalDensity.current
    val colorTexto = Color(ajustes.colorTexto)
    val marcador = Color(ajustes.colorMarcador)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val alto = constraints.maxHeight.toFloat()
        val lineaY = alto * ajustes.alturaLectura

        // Seguir la voz: cada avance lleva la siguiente palabra a la línea de lectura.
        LaunchedEffect(posicion, layout, ajustes.seguimiento, ajustes.adelanto) {
            val l = layout ?: return@LaunchedEffect
            if (tocando || ajustes.seguimiento == Seguimiento.AUTOMATICO && enMarcha) return@LaunchedEffect
            val p = palabras()
            // Unas palabras por delante: el ASR llega tarde, así la línea siguiente sube a tiempo.
            val siguiente = p.getOrNull(posicion + 1 + ajustes.adelanto) ?: p.lastOrNull() ?: return@LaunchedEffect
            val objetivo = if (posicion < 0) 0f else l.getLineTop(l.getLineForOffset(siguiente.inicio))
            automatico = true
            scroll.animateScrollBy(objetivo - scroll.value, tween(250))
            automatico = false
        }

        // Modo automático: velocidad fija mientras está en marcha y no se toca.
        LaunchedEffect(enMarcha, ajustes.seguimiento, ajustes.velocidadAuto, tocando) {
            if (!enMarcha || ajustes.seguimiento != Seguimiento.AUTOMATICO || tocando) return@LaunchedEffect
            var antes = withFrameNanos { it }
            while (isActive) {
                val ahora = withFrameNanos { it }
                val px = with(densidad) { ajustes.velocidadAuto.dp.toPx() } * (ahora - antes) / 1e9f
                antes = ahora
                scroll.scrollBy(px)
            }
        }

        // Tras moverlo a mano, el seguimiento continúa desde la palabra que queda en la línea.
        LaunchedEffect(resincronizar, scroll.isScrollInProgress) {
            if (!resincronizar || scroll.isScrollInProgress) return@LaunchedEffect
            delay(80)
            resincronizar = false
            val l = layout ?: return@LaunchedEffect
            val linea = l.getLineForVerticalPosition(scroll.value.toFloat() + 1f)
            val inicio = l.getLineStart(linea)
            val indice = palabras().indexOfFirst { it.inicio >= inicio }
            if (indice >= 0) alMoverAMano(indice - 1)
        }

        val leidoHasta = palabras().getOrNull(posicion)?.fin ?: 0
        val anotado = remember(texto, leidoHasta, ajustes.colorTexto, ajustes.opacidadLeido) {
            buildAnnotatedString {
                append(texto)
                if (leidoHasta > 0) addStyle(SpanStyle(color = colorTexto.copy(alpha = ajustes.opacidadLeido)), 0, leidoHasta)
            }
        }

        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { if (ajustes.espejo) scaleX = -1f }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        tocando = true
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                        } while (e.changes.any { it.pressed })
                        tocando = false
                        resincronizar = true
                    }
                }
                .verticalScroll(scroll),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.fillMaxWidth(ajustes.anchoColumna.coerceIn(0.4f, 1f))
                    .padding(horizontal = ajustes.margen.dp),
            ) {
                Spacer(Modifier.height(with(densidad) { lineaY.toDp() }))
                androidx.compose.material3.Text(
                    anotado,
                    style = TextStyle(
                        color = colorTexto,
                        fontSize = ajustes.tamanoLetra.sp,
                        lineHeight = (ajustes.tamanoLetra * ajustes.interlineado).sp,
                        fontFamily = ajustes.fuente.familia(),
                        fontWeight = FontWeight.SemiBold,
                    ),
                    onTextLayout = { layout = it },
                )
                Spacer(Modifier.height(with(densidad) { alto.toDp() }))
            }
        }

        // Línea de lectura: un triángulo y un trazo fino en el color del marcador.
        val lineaAlto = with(densidad) { (ajustes.tamanoLetra * ajustes.interlineado).sp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val y = lineaY + lineaAlto / 2
            val t = Path().apply {
                moveTo(0f, y - 14f); lineTo(18f, y); lineTo(0f, y + 14f); close()
            }
            drawPath(t, marcador)
            drawLine(marcador.copy(alpha = 0.35f), Offset(24f, y + lineaAlto / 2), Offset(size.width, y + lineaAlto / 2), 2f)
        }
    }
}

@Composable
private fun Insignia(estado: Estado, modo: Seguimiento, nivel: Float, oido: String) {
    val (texto, color) = when (estado) {
        Estado.CargandoVoz -> "PREPARANDO VOZ…" to Marca.TextoSuave
        is Estado.ErrorVoz -> "SIN RECONOCIMIENTO DE VOZ" to Color(0xFFFF6B6B)
        is Estado.EnMarcha -> (if (estado.grabando) "● REC" else "ENSAYO") to (if (estado.grabando) Marca.Rojo else Color.White)
        Estado.Guardando -> "GUARDANDO…" to Color.White
        Estado.EmpezandoGrabacion -> "EMPEZANDO A GRABAR…" to Marca.Rojo
        else -> when (modo) {
            Seguimiento.VOZ -> "SIGUE TU VOZ"; Seguimiento.AUTOMATICO -> "AUTOMÁTICO"; Seguimiento.MANUAL -> "MANUAL"
        } to Color.White
    }
    var segundos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(estado) {
        val e = estado as? Estado.EnMarcha ?: return@LaunchedEffect
        while (isActive) { segundos = (System.currentTimeMillis() - e.desde) / 1000; delay(250) }
    }
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x88000000)).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(texto, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            if (estado is Estado.EnMarcha) {
                Spacer(Modifier.width(8.dp))
                Text("%d:%02d".format(segundos / 60, segundos % 60), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Outlined.Mic, null, tint = Marca.TextoSuave, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(width = 48.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = nivel; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                    .background(if (nivel > 0.95f) Marca.Rojo else Color(0xFF7BD389)))
            }
        }
        if (modo == Seguimiento.VOZ && oido.isNotBlank() && estado is Estado.EnMarcha) {
            Text("«${oido.takeLast(40)}»", style = MaterialTheme.typography.labelSmall, color = Marca.TextoSuave,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(220.dp))
        }
    }
}

@Composable
private fun Controles(
    modifier: Modifier,
    estado: Estado,
    ajustes: Ajustes,
    alEnsayar: () -> Unit,
    alGrabar: () -> Unit,
    alReiniciar: () -> Unit,
    alCambiarModo: () -> Unit,
    alAjustesRapidos: () -> Unit,
    alAjustes: () -> Unit,
) {
    val enMarcha = estado is Estado.EnMarcha
    val grabando = (estado as? Estado.EnMarcha)?.grabando == true
    val ocupado = estado is Estado.Guardando || estado is Estado.CuentaAtras || estado == Estado.EmpezandoGrabacion
    // Tres huecos fijos: el botón de grabar/parar no se mueve nunca de su sitio.
    Box(
        modifier.widthIn(max = 560.dp).fillMaxWidth().navigationBarsPadding().padding(16.dp)
            .clip(RoundedCornerShape(28.dp)).background(Color(0xCC0E0E10))
            .border(1.dp, Marca.Linea, RoundedCornerShape(28.dp)).padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (!grabando) Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alReiniciar) { Icon(Icons.Outlined.Replay, "Volver al principio", tint = Color.White) }
            TextButton(onClick = alCambiarModo) {
                Text(
                    when (ajustes.seguimiento) { Seguimiento.VOZ -> "VOZ"; Seguimiento.AUTOMATICO -> "AUTO"; Seguimiento.MANUAL -> "MANUAL" },
                    style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold,
                )
            }
        }
        // Botón de grabar: círculo rojo; mientras graba, cuadrado de parar. Siempre en el centro.
        Box(
            Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape).padding(6.dp)
                .clip(if (grabando) RoundedCornerShape(8.dp) else CircleShape).background(Marca.Rojo)
                .clickable(enabled = !ocupado && (grabando || !enMarcha), onClick = alGrabar),
            contentAlignment = Alignment.Center,
        ) {
            if (grabando) Icon(Icons.Filled.Stop, "Parar y guardar", tint = Color.White)
        }
        if (!grabando) Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alEnsayar, enabled = !ocupado,
                modifier = Modifier.clip(CircleShape).background(Marca.SuperficieAlta)) {
                Icon(if (enMarcha) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (enMarcha) "Pausar" else "Ensayar sin grabar", tint = Color.White)
            }
            IconButton(onClick = alAjustesRapidos) { Icon(Icons.Outlined.Tune, "Ajustes rápidos", tint = Color.White) }
            IconButton(onClick = alAjustes) { Icon(Icons.Outlined.Settings, "Ajustes", tint = Color.White) }
        }
    }
}

@Composable
private fun AvisoPermisos(modifier: Modifier, alPedir: () -> Unit) {
    Column(
        modifier.padding(32.dp).clip(RoundedCornerShape(16.dp)).background(Marca.Superficie).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Necesito la cámara y el micrófono", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("El micro sirve para seguir tu voz y para el audio del vídeo: es el mismo, así la grabación no se corta.",
            color = Marca.TextoSuave, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = alPedir) { Text("Dar permisos", color = Marca.Rojo, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Aviso(estado: Estado, modifier: Modifier, alCerrar: () -> Unit) {
    val (titulo, cuerpo) = when (estado) {
        is Estado.Guardado -> "Vídeo guardado" to "Está en Películas › Prompter."
        is Estado.ErrorGrabacion -> "No se pudo grabar" to estado.motivo
        else -> return
    }
    Row(
        modifier.fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(16.dp)).background(Marca.Superficie)
            .border(1.dp, if (estado is Estado.Guardado) Marca.Linea else Marca.Rojo, RoundedCornerShape(16.dp))
            .padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Bold, color = Marca.Texto)
            Text(cuerpo, color = Marca.TextoSuave, style = MaterialTheme.typography.bodyMedium)
        }
        if (estado is Estado.Guardado) {
            val context = LocalContext.current
            TextButton(onClick = {
                // Con permiso de lectura explícito: el visor de Archivos/Drive no puede abrirlo sin él.
                val ver = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(estado.uri, "video/mp4")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(ver) }
            }) { Text("Ver", color = Marca.Rojo, fontWeight = FontWeight.Bold) }
        }
        IconButton(onClick = alCerrar) { Icon(Icons.Outlined.Close, "Cerrar") }
    }
}
