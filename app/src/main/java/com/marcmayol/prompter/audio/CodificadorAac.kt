package com.marcmayol.prompter.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/**
 * PCM mono 16 bits → AAC-LC dentro de un .m4a. Se alimenta desde el hilo de audio.
 * [inicioUs]: en qué instante del vídeo cae la primera muestra.
 */
class CodificadorAac(val destino: File, private val frecuencia: Int, private val inicioUs: Long = 0) {
    private val codec: MediaCodec
    private val muxer = MediaMuxer(destino.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var pista = -1
    private var muestras = 0L
    private val info = MediaCodec.BufferInfo()
    private var terminado = false

    init {
        val formato = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, frecuencia, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(formato, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    @Synchronized
    fun codificar(pcm: ShortArray, n: Int) {
        if (terminado) return
        var desde = 0
        while (desde < n) {
            val i = codec.dequeueInputBuffer(10_000)
            if (i < 0) { vaciar(false); continue }
            val buf = codec.getInputBuffer(i)!!
            buf.clear()
            buf.order(ByteOrder.nativeOrder()) // PCM en el orden de la plataforma, no big-endian
            val cuantas = minOf(n - desde, buf.capacity() / 2)
            for (k in 0 until cuantas) buf.putShort(pcm[desde + k])
            codec.queueInputBuffer(i, 0, cuantas * 2, inicioUs + muestras * 1_000_000L / frecuencia, 0)
            muestras += cuantas
            desde += cuantas
            vaciar(false)
        }
    }

    @Synchronized
    fun terminar() {
        if (terminado) return
        terminado = true
        val i = codec.dequeueInputBuffer(100_000)
        if (i >= 0) codec.queueInputBuffer(i, 0, 0, inicioUs + muestras * 1_000_000L / frecuencia, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        vaciar(true)
        codec.stop(); codec.release()
        if (pista >= 0) { muxer.stop() }
        muxer.release()
    }

    private fun vaciar(hastaElFinal: Boolean) {
        while (true) {
            val o = codec.dequeueOutputBuffer(info, if (hastaElFinal) 10_000 else 0)
            when {
                o == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!hastaElFinal) return
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    pista = muxer.addTrack(codec.outputFormat); muxer.start()
                }
                o >= 0 -> {
                    val buf = codec.getOutputBuffer(o)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && pista >= 0) muxer.writeSampleData(pista, buf, info)
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }
}
