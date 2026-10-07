package com.marcmayol.prompter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.marcmayol.prompter.datos.Ajustes
import com.marcmayol.prompter.datos.RepositorioAjustes
import com.marcmayol.prompter.datos.RepositorioGuiones
import com.marcmayol.prompter.ui.PantallaAjustes
import com.marcmayol.prompter.ui.PantallaBiblioteca
import com.marcmayol.prompter.ui.PantallaEditor
import com.marcmayol.prompter.ui.PantallaPrompter
import com.marcmayol.prompter.ui.TemaPrompter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        // Vuelta de la pantalla de «instalar apps desconocidas»: reanuda la actualización.
        (application as PrompterApp).actualizador.onPermisoQuizaConcedido()
    }

    /** Qué tamaños de vídeo da cada cámara por Camera2 y si tiene perfil 4K (para decidir si merece la pena). */
    private fun diagnosticoCamaras() = runCatching {
        val cm = getSystemService(android.hardware.camera2.CameraManager::class.java)
        for (id in cm.cameraIdList) {
            val ch = cm.getCameraCharacteristics(id)
            val cara = when (ch.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)) { 0 -> "frontal"; 1 -> "trasera"; else -> "otra" }
            val mapa = ch.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
            val grandes = mapa.getOutputSizes(android.media.MediaCodec::class.java).orEmpty()
                .filter { maxOf(it.width, it.height) >= 2560 }
                .joinToString { s -> "${s.width}x${s.height}@" + (1e9 / mapa.getOutputMinFrameDuration(android.media.MediaCodec::class.java, s)).toInt() + "fps" }
            val perfil4k = android.media.CamcorderProfile.hasProfile(id.toIntOrNull() ?: -1, android.media.CamcorderProfile.QUALITY_2160P)
            val nivel = ch.get(android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            android.util.Log.i("Prompter", "camara $id $cara nivel=$nivel perfil4K=$perfil4k grandes=[$grandes]")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        diagnosticoCamaras()
        val guiones = RepositorioGuiones(this)
        val repoAjustes = RepositorioAjustes(this)
        val actualizador = (application as PrompterApp).actualizador
        val notion = com.marcmayol.prompter.notion.AlmacenNotion(this)

        setContent {
            TemaPrompter {
                val alcance = rememberCoroutineScope()
                LaunchedEffect(Unit) { guiones.cargar() }
                LaunchedEffect(Unit) { actualizador.comprobar(com.marcm.actualizador.Modo.AUTOMATICO) }
                val estadoActualizacion by actualizador.estado.collectAsState()
                val lista by guiones.guiones.collectAsState()
                val ajustes by repoAjustes.ajustes.collectAsState(initial = Ajustes())

                // Navegación mínima: qué pantalla y sobre qué guion. Sobrevive a rotar.
                var pantalla by rememberSaveable { mutableStateOf("biblioteca") }
                var guionId by rememberSaveable { mutableStateOf<String?>(null) }
                var desdePrompter by rememberSaveable { mutableStateOf(false) }

                fun ir(p: String, id: String? = guionId) { pantalla = p; guionId = id }

                BackHandler(enabled = pantalla != "biblioteca") {
                    when {
                        pantalla == "ajustes" && desdePrompter -> { desdePrompter = false; ir("prompter") }
                        else -> ir("biblioteca", null)
                    }
                }

                when (pantalla) {
                    "biblioteca" -> PantallaBiblioteca(
                        guiones = lista,
                        alAbrir = { ir("prompter", it) },
                        alEditar = { ir("editor", it) },
                        alNuevo = { ir("editor", null) },
                        alBorrar = { id -> alcance.launch { guiones.borrar(id) } },
                        alAjustes = { desdePrompter = false; ir("ajustes", null) },
                        estadoActualizacion = estadoActualizacion,
                        alNotion = { ir("notion", null) },
                        alActualizar = { actualizador.actualizarAhora() },
                    )
                    "notion" -> com.marcmayol.prompter.ui.PantallaNotion(
                        almacen = notion,
                        guiones = guiones,
                        alImportar = { g -> ir("prompter", g.id) },
                        alVolver = { ir("biblioteca", null) },
                    )
                    "editor" -> PantallaEditor(
                        guion = guionId?.let { guiones.buscar(it) },
                        alGuardar = { g -> alcance.launch { guiones.guardar(g); ir("biblioteca", null) } },
                        alVolver = { ir("biblioteca", null) },
                    )
                    "prompter" -> {
                        val g = remember(guionId, lista) { guionId?.let { guiones.buscar(it) } }
                        if (g == null) ir("biblioteca", null) else PantallaPrompter(
                            guion = g,
                            ajustes = ajustes,
                            alCambiarAjustes = { a -> alcance.launch { repoAjustes.guardar(a) } },
                            alVolver = { ir("biblioteca", null) },
                            alAjustes = { desdePrompter = true; ir("ajustes") },
                            notion = notion,
                        )
                    }
                    "ajustes" -> PantallaAjustes(
                        ajustes = ajustes,
                        alCambiar = { a -> alcance.launch { repoAjustes.guardar(a) } },
                        alRestablecer = { alcance.launch { repoAjustes.restablecer() } },
                        actualizador = actualizador,
                        notion = notion,
                        alVolver = {
                            if (desdePrompter) { desdePrompter = false; ir("prompter") } else ir("biblioteca", null)
                        },
                    )
                }
            }
        }
    }
}
