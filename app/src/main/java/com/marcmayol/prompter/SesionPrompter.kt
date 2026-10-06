package com.marcmayol.prompter

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.prompter.audio.MotorAudio
import com.marcmayol.prompter.dominio.Alineador
import com.marcmayol.prompter.dominio.Palabra
import com.marcmayol.prompter.dominio.Texto
import com.marcmayol.prompter.grabacion.Grabadora
import com.marcmayol.prompter.voz.Reconocedor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface Estado {
    data object CargandoVoz : Estado
    data class ErrorVoz(val motivo: String) : Estado
    data object Listo : Estado
    data class CuentaAtras(val quedan: Int, val grabar: Boolean) : Estado
    /** Se ha pedido grabar, pero la cámara aún no ha confirmado el primer fotograma. */
    data object EmpezandoGrabacion : Estado
    /** Leyendo: el texto se mueve. [grabando] = además se graba vídeo. */
    data class EnMarcha(val grabando: Boolean, val desde: Long) : Estado
    data object Guardando : Estado
    data class Guardado(val uri: Uri) : Estado
    data class ErrorGrabacion(val motivo: String) : Estado
}

class SesionPrompter(app: Application) : AndroidViewModel(app) {
    val grabadora = Grabadora(app)

    private val _estado = MutableStateFlow<Estado>(Estado.CargandoVoz)
    val estado: StateFlow<Estado> = _estado

    /** Índice de la última palabra leída. */
    private val _posicion = MutableStateFlow(-1)
    val posicion: StateFlow<Int> = _posicion

    /** Lo último que ha oído el reconocedor, para enseñarlo pequeño (y depurar). */
    private val _oido = MutableStateFlow("")
    val oido: StateFlow<String> = _oido

    var palabras: List<Palabra> = emptyList(); private set
    private var alineador = Alineador(emptyList())
    private var sensibilidad = 3
    private var escuchando = false

    private val reconocedor = Reconocedor { h ->
        _oido.value = h
        if (escuchando) _posicion.value = alineador.escuchar(h)
    }

    /** En depuración se puede sustituir el micro por un WAV (para probar en el emulador). */
    private val motor = MotorAudio(
        alReconocedor = { pcm, n -> reconocedor.alimentar(pcm, n) },
        wavDePrueba = File(app.getExternalFilesDir(null), "prueba.wav").takeIf { BuildConfig.DEBUG && it.exists() },
    )
    val nivel = motor.nivel

    private var cuenta: Job? = null
    private var videoTmp: File? = null

    fun preparar(texto: String, sensibilidad: Int) {
        palabras = Texto.palabras(texto)
        this.sensibilidad = sensibilidad
        alineador = Alineador(palabras, sensibilidad)
        _posicion.value = -1
        if (_estado.value != Estado.CargandoVoz) return
        viewModelScope.launch {
            try {
                val modelo = Reconocedor.cargarModelo(getApplication())
                reconocedor.arrancar(modelo, viewModelScope)
                _estado.value = Estado.Listo
            } catch (e: Exception) {
                _estado.value = Estado.ErrorVoz(e.message ?: "No se pudo cargar el modelo de voz")
            }
        }
    }

    fun cambiarSensibilidad(s: Int) {
        if (s == sensibilidad) return
        sensibilidad = s
        val pos = alineador.posicion
        alineador = Alineador(palabras, s).also { it.saltarA(pos) }
    }

    /** El micro se abre al entrar en la pantalla (con permiso), para el vúmetro y para ensayar. */
    fun abrirMicro() = motor.arrancar()
    fun cerrarMicro() = motor.parar()

    fun empezar(grabar: Boolean, segundos: Int) {
        val actual = _estado.value
        if (actual is Estado.EnMarcha || actual is Estado.CuentaAtras ||
            actual == Estado.EmpezandoGrabacion || actual == Estado.Guardando) return
        cuenta?.cancel()
        cuenta = viewModelScope.launch {
            for (s in segundos downTo 1) {
                _estado.value = Estado.CuentaAtras(s, grabar)
                delay(1000)
            }
            if (grabar) iniciarGrabacion() else arrancarLectura(false)
        }
    }

    private fun arrancarLectura(grabando: Boolean) {
        escuchando = true
        _estado.value = Estado.EnMarcha(grabando, System.currentTimeMillis())
    }

    private var vigilante: Job? = null

    private fun iniciarGrabacion() {
        val app = getApplication<Application>()
        val v = File(app.cacheDir, "video_tmp.mp4").also { it.delete(); videoTmp = it }
        val a = File(app.cacheDir, "audio_tmp.m4a").also { it.delete() }
        _estado.value = Estado.EmpezandoGrabacion
        val ok = grabadora.empezar(v,
            alEmpezar = { videoUs ->
                // REC solo cuando la cámara ya graba de verdad; el audio arranca alineado con el vídeo.
                vigilante?.cancel()
                motor.empezarAGrabar(a, videoUs)
                arrancarLectura(true)
            },
            alCortarse = { motivo ->
                // La cámara ha parado sola: se guarda lo que haya y se avisa.
                if (_estado.value is Estado.EnMarcha) terminarGrabacion(motivo)
            },
        )
        if (!ok) { _estado.value = Estado.ErrorGrabacion("La cámara no está lista. Espera un momento y vuelve a intentarlo."); return }
        vigilante = viewModelScope.launch {
            delay(4_000)
            if (_estado.value == Estado.EmpezandoGrabacion) {
                grabadora.cancelar()
                motor.dejarDeGrabar()
                _estado.value = Estado.ErrorGrabacion("La cámara no ha empezado a grabar. Vuelve a intentarlo.")
            }
        }
    }

    fun pausar() {
        cuenta?.cancel()
        val e = _estado.value
        escuchando = false
        when {
            e is Estado.EnMarcha && e.grabando -> terminarGrabacion(null)
            e == Estado.EmpezandoGrabacion -> {
                vigilante?.cancel(); grabadora.cancelar(); motor.dejarDeGrabar(); _estado.value = Estado.Listo
            }
            e is Estado.Guardando -> Unit
            else -> _estado.value = Estado.Listo
        }
    }

    /** [aviso]: por qué se ha cortado, si no ha sido la persona quien ha parado. */
    private fun terminarGrabacion(aviso: String?) {
        escuchando = false
        _estado.value = Estado.Guardando
        viewModelScope.launch {
            try {
                grabadora.parar()
                val audio = motor.dejarDeGrabar()
                val v = videoTmp
                // Aunque la cámara diga que hubo un error, si dejó vídeo se guarda.
                if (v == null || !v.exists() || v.length() == 0L) {
                    _estado.value = Estado.ErrorGrabacion(aviso ?: "El vídeo no se pudo guardar"); return@launch
                }
                val uri = Grabadora.unir(getApplication(), v, audio)
                _estado.value = if (aviso == null) Estado.Guardado(uri)
                    else Estado.ErrorGrabacion("$aviso. Lo grabado hasta ahí está en Películas › Prompter.")
            } catch (e: Exception) {
                _estado.value = Estado.ErrorGrabacion(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cerrarAviso() {
        if (_estado.value is Estado.Guardado || _estado.value is Estado.ErrorGrabacion) _estado.value = Estado.Listo
    }

    /** La persona ha movido el texto a mano: el seguimiento continúa desde esa palabra. */
    fun saltarA(indice: Int) {
        alineador.saltarA(indice)
        _posicion.value = alineador.posicion
    }

    fun reiniciar() = saltarA(-1)

    override fun onCleared() {
        escuchando = false
        motor.parar()
        reconocedor.parar()
    }
}
