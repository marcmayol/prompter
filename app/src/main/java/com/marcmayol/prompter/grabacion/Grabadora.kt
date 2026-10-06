package com.marcmayol.prompter.grabacion

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.marcmayol.prompter.datos.Ajustes
import com.marcmayol.prompter.datos.Calidad
import com.marcmayol.prompter.datos.Camara
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vídeo con CameraX, pero SIN audio: el audio lo pone MotorAudio, que es el único que toca el
 * micro. Al parar, se unen las dos pistas en un MP4 que va a Películas/Prompter.
 */
class Grabadora(private val context: Context) {
    private var captura: VideoCapture<Recorder>? = null
    private var grabacion: Recording? = null
    private var fin: CompletableDeferred<Boolean>? = null

    private var camaraActiva: Camera? = null

    /** Qué ajustes pedidos no admite esta cámara (para avisar), o null si todo va. */
    val aviso = MutableStateFlow<String?>(null)

    suspend fun vincular(duenio: LifecycleOwner, superficie: Preview.SurfaceProvider?, a: Ajustes, rotacion: Int) {
        // Reenganchar la cámara corta la grabación en curso: mientras se graba, no se toca.
        if (grabacion != null) return
        val proveedor = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        val pedida = if (a.camara == Camara.FRONTAL) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val cam = if (proveedor.hasCamera(pedida)) pedida else CameraSelector.DEFAULT_BACK_CAMERA
        val info = proveedor.getCameraInfo(cam)
        val capacidades = Recorder.getVideoCapabilities(info)

        Log.i(TAG, "cámara ${a.camara}: SDR=${capacidades.getSupportedQualities(DynamicRange.SDR)} " +
            "HLG=${runCatching { capacidades.getSupportedQualities(DynamicRange.HLG_10_BIT) }.getOrNull()} " +
            "estab=${capacidades.isStabilizationSupported} fps=${info.supportedFrameRateRanges}")
        // Solo se piden las funciones que esta cámara dice admitir.
        val sinSoporte = mutableListOf<String>()
        val calidadPedida = when (a.calidad) { Calidad.UHD -> Quality.UHD; Calidad.FHD -> Quality.FHD; Calidad.HD -> Quality.HD }
            .let { q -> capacidades.getSupportedQualities(DynamicRange.SDR).filter { altoDe(it) <= altoDe(q) }.maxByOrNull { altoDe(it) } ?: q }
        // El HDR puede existir en la cámara pero no a la calidad elegida (la frontal del Pixel no lo da en 4K).
        val hdr = a.hdr && DynamicRange.HLG_10_BIT in capacidades.supportedDynamicRanges &&
            calidadPedida in capacidades.getSupportedQualities(DynamicRange.HLG_10_BIT)
        if (a.hdr && !hdr) sinSoporte += "HDR a ${altoDe(calidadPedida)}p"
        val estab = a.estabilizacion && capacidades.isStabilizationSupported
        if (a.estabilizacion && !estab) sinSoporte += "estabilización"
        val fps60 = a.fps60 && info.supportedFrameRateRanges.any { it.upper >= 60 }
        if (a.fps60 && !fps60) sinSoporte += "60 fps"

        val calidad = when (a.calidad) { Calidad.UHD -> Quality.UHD; Calidad.FHD -> Quality.FHD; Calidad.HD -> Quality.HD }
        // Si la cámara no llega a la calidad pedida, la más cercana por debajo (no la mínima).
        val selector = QualitySelector.from(calidad, FallbackStrategy.lowerQualityOrHigherThan(calidad))

        fun montar(conExtras: Boolean): Pair<Preview, VideoCapture<Recorder>> {
            val rb = Recorder.Builder().setQualitySelector(selector)
            if (a.tasaAlta) {
                // Según la calidad que se graba de verdad, no la pedida: la frontal pide 4K y graba 1080p.
                val base = when (calidadPedida) { Quality.UHD -> 50_000_000; Quality.FHD -> 20_000_000; else -> 10_000_000 }
                rb.setTargetVideoEncodingBitRate(if (fps60 && conExtras) base * 3 / 2 else base)
            }
            val vb = VideoCapture.Builder(rb.build()).setTargetRotation(rotacion)
            val pb = Preview.Builder().setTargetRotation(rotacion)
            if (conExtras) {
                val rango = if (fps60) Range(60, 60) else Range(30, 30)
                vb.setTargetFrameRate(rango); pb.setTargetFrameRate(rango)
                if (estab) vb.setVideoStabilizationEnabled(true)
                if (hdr) { vb.setDynamicRange(DynamicRange.HLG_10_BIT); pb.setDynamicRange(DynamicRange.HLG_10_BIT) }
            }
            val p = pb.build().also { pr -> superficie?.let { pr.surfaceProvider = it } }
            return p to vb.build()
        }

        // Lo que se puede esperar de verdad: la calidad pedida o la máxima de esta cámara, si es menor.
        val maxima = capacidades.getSupportedQualities(DynamicRange.SDR).maxByOrNull { altoDe(it) }
        val altoPedido = minOf(when (a.calidad) { Calidad.UHD -> 2160; Calidad.FHD -> 1080; Calidad.HD -> 720 }, maxima?.let { altoDe(it) } ?: 2160)
        if (maxima != null && when (a.calidad) { Calidad.UHD -> 2160; Calidad.FHD -> 1080; Calidad.HD -> 720 } > altoDe(maxima)) sinSoporte += "${a.calidad.etiqueta} (llega a ${altoDe(maxima)}p)"
        fun vincularCon(conExtras: Boolean): VideoCapture<Recorder> {
            proveedor.unbindAll()
            val (p, v) = montar(conExtras)
            camaraActiva = proveedor.bindToLifecycle(duenio, cam, p, v)
            return v
        }
        // Resolución que la cámara ha concedido de verdad (API marcada como interna de CameraX, pero pública).
        @SuppressLint("RestrictedApi")
        fun alto(v: VideoCapture<Recorder>): Int = v.attachedSurfaceResolution?.let { minOf(it.width, it.height) } ?: altoPedido

        var video = try {
            vincularCon(true)
        } catch (e: Exception) {
            // Algunas combinaciones (p. ej. 4K + 60 fps + HDR) no caben a la vez: se cae a lo básico.
            Log.w(TAG, "combinación no admitida, sin extras", e)
            sinSoporte += "esta combinación de 60 fps / HDR / estabilización"
            vincularCon(false)
        }
        // CameraX rebaja la resolución en silencio si un extra no cabe con ella (la estabilización de
        // la frontal, por ejemplo, no llega a 4K). La resolución pedida manda: se quita el extra.
        // La resolución se decide cuando la cámara ya está en marcha, no al vincular: se espera un poco.
        withContext(Dispatchers.Default) { delay(1_500) }
        Log.i(TAG, "resolución concedida: ${alto(video)}p (pedido ${altoPedido}p)")
        if (alto(video) < altoPedido && (estab || fps60 || hdr) && grabacion == null) {
            Log.i(TAG, "resolución ${alto(video)} < $altoPedido con extras; se prueba sin ellos")
            val sinExtras = runCatching { vincularCon(false) }.getOrNull()
            withContext(Dispatchers.Default) { delay(1_500) }
            Log.i(TAG, "sin extras: ${sinExtras?.let { alto(it) }}p")
            if (sinExtras != null && alto(sinExtras) > alto(video)) {
                video = sinExtras
                sinSoporte += listOfNotNull("estabilización".takeIf { estab }, "60 fps".takeIf { fps60 }, "HDR".takeIf { hdr })
                    .joinToString(", ") + " a ${a.calidad.etiqueta}"
            } else {
                video = vincularCon(true)
            }
        }
        Log.i(TAG, "cámara lista: ${alto(video)}p (pedido ${altoPedido}p)")
        captura = video
        aviso.value = sinSoporte.takeIf { it.isNotEmpty() }?.let { "Esta cámara no admite: " + it.joinToString(", ") }
        aplicarControles(a)
    }

    private fun altoDe(q: Quality): Int = when (q) {
        Quality.UHD -> 2160; Quality.FHD -> 1080; Quality.HD -> 720; Quality.SD -> 480; else -> 0
    }

    /** Zoom y exposición se cambian en vivo, sin reenganchar la cámara. */
    fun aplicarControles(a: Ajustes) {
        val c = camaraActiva ?: return
        val z = c.cameraInfo.zoomState.value
        val zoom = if (z != null) a.zoom.coerceIn(z.minZoomRatio, z.maxZoomRatio) else a.zoom
        c.cameraControl.setZoomRatio(zoom)
        val ex = c.cameraInfo.exposureState
        if (ex.isExposureCompensationSupported) {
            val paso = ex.exposureCompensationStep.toFloat()
            val indice = if (paso > 0) Math.round(a.exposicion / paso) else 0
            c.cameraControl.setExposureCompensationIndex(indice.coerceIn(ex.exposureCompensationRange.lower, ex.exposureCompensationRange.upper))
        }
    }

    suspend fun soltar() = withContext(Dispatchers.Main) {
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        captura = null
    }

    /**
     * Empieza a grabar vídeo. [alEmpezar] se llama cuando de verdad entra el primer fotograma;
     * [alCortarse] si la cámara termina la grabación sin que se lo hayamos pedido.
     */
    @SuppressLint("MissingPermission")
    fun empezar(destino: File, alEmpezar: (Long) -> Unit, alCortarse: (String) -> Unit): Boolean {
        val c = captura ?: return false
        val terminado = CompletableDeferred<Boolean>().also { fin = it }
        parando = false
        var empezada = false
        grabacion = c.output.prepareRecording(context, FileOutputOptions.Builder(destino).build())
            .start(ContextCompat.getMainExecutor(context)) { e ->
                when (e) {
                    is VideoRecordEvent.Start -> Log.i(TAG, "grabación pedida")
                    // El Start llega ~0,3 s antes del primer fotograma. El audio se engancha con el
                    // primer Status que ya lleva vídeo, y se le dice cuánto vídeo hay ya grabado.
                    is VideoRecordEvent.Status -> if (!empezada && e.recordingStats.recordedDurationNanos > 0) {
                        empezada = true
                        Log.i(TAG, "grabación empezada, vídeo ya grabado: ${e.recordingStats.recordedDurationNanos / 1_000_000} ms")
                        alEmpezar(e.recordingStats.recordedDurationNanos / 1000)
                    }
                    is VideoRecordEvent.Finalize -> {
                        Log.i(TAG, "grabación finalizada, error=${e.error} ${e.cause?.message.orEmpty()}")
                        grabacion = null
                        val ok = !e.hasError() || e.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
                        terminado.complete(ok)
                        if (!parando) alCortarse(if (ok) "La cámara ha dejado de grabar" else "Error de la cámara (${e.error})")
                    }
                }
            }
        return true
    }

    @Volatile private var parando = false

    /** Para el vídeo y espera a que el archivo esté cerrado, como mucho unos segundos. */
    suspend fun parar(): Boolean {
        parando = true
        grabacion?.stop()
        val ok = withTimeoutOrNull(8_000) { fin?.await() } ?: false
        grabacion = null
        return ok
    }

    /** Cancela una grabación que no ha llegado a empezar. */
    fun cancelar() {
        parando = true
        grabacion?.stop(); grabacion = null
    }

    companion object {
        private const val TAG = "Prompter"

        /** Une la pista de vídeo y la de audio en un MP4 nuevo en Películas/Prompter. El audio que pase del final del vídeo se descarta. */
        suspend fun unir(context: Context, video: File, audio: File?): Uri = withContext(Dispatchers.IO) {
            val nombre = "Prompter_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date()) + ".mp4"
            val valores = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, nombre)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Prompter")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valores)!!
            val rotacion = MediaMetadataRetriever().run {
                try { setDataSource(video.absolutePath); extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0 }
                finally { release() }
            }
            resolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer.setOrientationHint(rotacion)
                var finVideoUs = Long.MAX_VALUE
                // Un extractor por pista, para poder ir intercalándolas.
                val pistas = listOfNotNull(video, audio?.takeIf { it.length() > 0 }).flatMap { f ->
                    val probe = MediaExtractor().apply { setDataSource(f.absolutePath) }
                    val indices = (0 until probe.trackCount).filter { i ->
                        probe.getTrackFormat(i).getString(MediaFormat.KEY_MIME)!!.let { it.startsWith("video/") || it.startsWith("audio/") }
                    }
                    probe.release()
                    indices.map { i ->
                        val ex = MediaExtractor().apply { setDataSource(f.absolutePath); selectTrack(i) }
                        val fmt = ex.getTrackFormat(i)
                        if (fmt.getString(MediaFormat.KEY_MIME)!!.startsWith("video/") && fmt.containsKey(MediaFormat.KEY_DURATION))
                            finVideoUs = fmt.getLong(MediaFormat.KEY_DURATION)
                        Triple(ex, muxer.addTrack(fmt), fmt.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/"))
                    }
                }
                muxer.start()
                val buf = ByteBuffer.allocate(4 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                // Vídeo y audio intercalados por tiempo, como lo escribe una cámara: con todo el
                // audio al final, el reproductor de Fotos no abre el archivo.
                while (true) {
                    val (ex, destino, esAudio) = pistas.filter { it.first.sampleTime >= 0 }.minByOrNull { it.first.sampleTime } ?: break
                    if (esAudio && ex.sampleTime > finVideoUs) { ex.advance(); continue }
                    info.size = ex.readSampleData(buf, 0)
                    if (info.size < 0) { ex.advance(); continue }
                    info.offset = 0
                    info.presentationTimeUs = ex.sampleTime
                    info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(destino, buf, info)
                    ex.advance()
                }
                pistas.forEach { it.first.release() }
                muxer.stop(); muxer.release()
            }
            valores.clear(); valores.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, valores, null, null)
            video.delete(); audio?.delete()
            uri
        }
    }
}
