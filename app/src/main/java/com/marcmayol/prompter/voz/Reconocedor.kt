package com.marcmayol.prompter.voz

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reconocimiento de voz local y en streaming con Vosk (modelo pequeño en español, en el APK).
 * Sin red y sin servicios de Google: funciona igual en el Pixel que en el emulador.
 */
class Reconocedor(private val alOir: (String) -> Unit) {
    private val cola = Channel<ShortArray>(capacity = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var trabajo: Job? = null
    private var reconocedor: Recognizer? = null
    private var ultimaParcial = ""

    /** Se llama desde el hilo de audio: copia y encola, nunca bloquea. */
    fun alimentar(pcm: ShortArray, n: Int) {
        cola.trySend(pcm.copyOf(n))
    }

    fun arrancar(modelo: Model, ambito: CoroutineScope) {
        if (trabajo != null) return
        val r = Recognizer(modelo, 16_000f).also { reconocedor = it }
        trabajo = ambito.launch(Dispatchers.Default) {
            for (trozo in cola) {
                if (r.acceptWaveForm(trozo, trozo.size)) {
                    val texto = JSONObject(r.result).optString("text")
                    if (texto.isNotBlank()) alOir(texto)
                    ultimaParcial = ""
                } else {
                    val parcial = JSONObject(r.partialResult).optString("partial")
                    if (parcial.isNotBlank() && parcial != ultimaParcial) {
                        ultimaParcial = parcial
                        alOir(parcial)
                    }
                }
            }
        }
    }

    fun parar() {
        trabajo?.cancel(); trabajo = null
        reconocedor?.close(); reconocedor = null
    }

    companion object {
        @Volatile private var modelo: Model? = null

        /** Copia el modelo de assets a disco la primera vez (unos segundos) y lo carga. */
        suspend fun cargarModelo(context: Context): Model {
            modelo?.let { return it }
            // Vosk escribe sin parar en el logcat y lo desborda: solo avisos.
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            return suspendCancellableCoroutine { c ->
                StorageService.unpack(context.applicationContext, "model-es", "model",
                    { m -> modelo = m; c.resume(m) },
                    { e -> Log.e("Reconocedor", "modelo", e); c.resumeWithException(e) })
            }
        }
    }
}
