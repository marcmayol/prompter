package com.marcmayol.prompter.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * El único dueño del micrófono en toda la app.
 *
 * Android 10+ deja escuchar a dos capturas a la vez, pero a la que no tiene prioridad le
 * entrega silencio sin avisar. Por eso no hay dos: este motor lee el micro una sola vez y
 * reparte cada trozo a quien lo necesite: al reconocedor de voz (rebajado a 16 kHz) y,
 * mientras se graba, al codificador AAC que acaba dentro del vídeo.
 */
class MotorAudio(
    /** Recibe audio a 16 kHz para el reconocedor. Se llama desde el hilo de audio. */
    private val alReconocedor: (ShortArray, Int) -> Unit,
    /** Solo para pruebas: un WAV PCM 16 bits mono a 48 kHz que sustituye al micro. */
    private val wavDePrueba: File? = null,
) {
    private val _nivel = MutableStateFlow(0f)
    /** Nivel de entrada 0–1, para el vúmetro. */
    val nivel: StateFlow<Float> = _nivel

    @Volatile private var activo = false
    @Volatile private var codificador: CodificadorAac? = null
    private var hilo: Thread? = null

    @SuppressLint("MissingPermission") // la pantalla pide RECORD_AUDIO antes de arrancar
    fun arrancar() {
        if (activo) return
        activo = true
        hilo = Thread({
            try {
                if (wavDePrueba != null) bucleWav(wavDePrueba) else bucleMicro()
            } catch (e: Exception) {
                Log.e(TAG, "audio", e)
            }
        }, "prompter-audio").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun parar() {
        activo = false
        hilo?.join(1000)
        hilo = null
        codificador?.terminar()
        codificador = null
    }

    @Volatile private var pendiente: Triple<File, Long, Long>? = null

    /**
     * A partir de ahora, cada trozo de audio entra también en este archivo AAC.
     * [videoUs]: cuánto vídeo llevaba grabado la cámara en el momento de pedirlo; con eso y
     * con el reloj se calcula en qué punto del vídeo cae la primera muestra.
     */
    fun empezarAGrabar(destino: File, videoUs: Long = 0) {
        pendiente = Triple(destino, videoUs, System.nanoTime())
    }

    /** Cierra el AAC y devuelve el archivo (o null si no se estaba grabando). */
    fun dejarDeGrabar(): File? {
        pendiente = null
        val c = codificador ?: return null
        codificador = null
        c.terminar()
        return c.destino
    }

    @SuppressLint("MissingPermission")
    private fun bucleMicro() {
        val minimo = AudioRecord.getMinBufferSize(FRECUENCIA, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val grabadora = AudioRecord(
            MediaRecorder.AudioSource.MIC, FRECUENCIA, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimo, FRECUENCIA * 2), // 1 s de margen
        )
        val trozo = ShortArray(MUESTRAS_TROZO)
        try {
            grabadora.startRecording()
            while (activo) {
                val n = grabadora.read(trozo, 0, trozo.size)
                if (n > 0) repartir(trozo, n)
            }
        } finally {
            runCatching { grabadora.stop() }
            grabadora.release()
        }
    }

    private fun bucleWav(f: File) {
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(44)
            val bytes = ByteArray(MUESTRAS_TROZO * 2)
            val trozo = ShortArray(MUESTRAS_TROZO)
            var siguiente = System.nanoTime()
            while (activo) {
                val leidos = raf.read(bytes)
                val n = if (leidos <= 0) { trozo.fill(0); trozo.size } else {
                    for (i in 0 until leidos / 2) trozo[i] = ((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xff)).toShort()
                    leidos / 2
                }
                repartir(trozo, n)
                // Al ritmo real, como un micro.
                siguiente += MUESTRAS_TROZO * 1_000_000_000L / FRECUENCIA
                val espera = (siguiente - System.nanoTime()) / 1_000_000
                if (espera > 0) Thread.sleep(espera)
            }
        }
    }

    private val a16k = ShortArray(MUESTRAS_TROZO / 3)

    private fun repartir(pcm: ShortArray, n: Int) {
        var suma = 0.0
        for (i in 0 until n) suma += pcm[i].toDouble() * pcm[i]
        // Escala logarítmica: -60 dBFS = 0, 0 dBFS = 1. La voz normal queda en la mitad, no siempre arriba.
        val dbfs = 20 * log10(maxOf(sqrt(suma / n) / 32768.0, 1e-6))
        _nivel.value = ((dbfs + 60) / 60).toFloat().coerceIn(0f, 1f)

        pendiente?.let { (destino, videoUs, pedido) ->
            // Este trozo se acaba de leer: su primera muestra se capturó hace (n / frecuencia).
            val transcurridoUs = (System.nanoTime() - pedido) / 1000
            val inicioUs = videoUs + transcurridoUs - n * 1_000_000L / FRECUENCIA
            codificador = CodificadorAac(destino, FRECUENCIA, maxOf(0L, inicioUs))
            pendiente = null
        }
        codificador?.codificar(pcm, n)

        // 48 kHz → 16 kHz: media de cada 3 muestras (filtro paso bajo sencillo y suficiente para voz).
        val m = n / 3
        for (i in 0 until m) a16k[i] = ((pcm[3 * i] + pcm[3 * i + 1] + pcm[3 * i + 2]) / 3).toShort()
        alReconocedor(a16k, m)
    }

    companion object {
        const val FRECUENCIA = 48_000
        const val MUESTRAS_TROZO = 960 // 20 ms: menos latencia para el vídeo y para la voz
        private const val TAG = "MotorAudio"
    }
}
