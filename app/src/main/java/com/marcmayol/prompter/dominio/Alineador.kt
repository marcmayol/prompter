package com.marcmayol.prompter.dominio

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Una palabra del guion: su forma normalizada y dónde empieza y acaba en el texto original.
 * El texto original no se toca nunca; solo se usa para saber qué línea pintar.
 */
data class Palabra(val forma: String, val inicio: Int, val fin: Int)

object Texto {
    private val diacriticos = Regex("\\p{Mn}+")
    private val palabra = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?")

    /** minúsculas, sin tildes ni signos: «Atención,» y «atencion» son la misma palabra. */
    fun normalizar(s: String): String =
        diacriticos.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
            .replace("’", "'")

    fun palabras(texto: String): List<Palabra> =
        palabra.findAll(texto).map { Palabra(normalizar(it.value), it.range.first, it.range.last + 1) }.toList()

    fun formas(texto: String): List<String> = palabras(texto).map { it.forma }
}

/**
 * Sigue por dónde va leyendo la persona comparando lo que reconoce el ASR con el guion.
 *
 * Lo que oye el reconocedor nunca es exacto: se salta palabras, cambia unas por otras,
 * la persona repite una frase o improvisa. Por eso no se busca la última palabra oída,
 * sino que se alinean las últimas palabras oídas contra una ventana del guion por delante
 * de la posición actual (alineamiento local tipo Smith-Waterman, con coincidencias
 * aproximadas). Solo se avanza si la alineación tiene puntuación suficiente, y nunca hacia
 * atrás: repetir una frase deja el texto quieto, no lo rebobina.
 */
class Alineador(
    private val guion: List<Palabra>,
    /** 1 = muy exigente (avanza solo con mucha coincidencia), 5 = muy permisivo. */
    sensibilidad: Int = 3,
) {
    /** Índice de la última palabra del guion que se ha leído; -1 antes de empezar. */
    var posicion: Int = -1
        private set

    private val puntuacionMinima = when (sensibilidad.coerceIn(1, 5)) {
        1 -> 7.0
        2 -> 5.5
        3 -> 4.0
        4 -> 3.0
        else -> 2.0
    }

    val terminado: Boolean get() = posicion >= guion.size - 1

    fun reiniciar(desde: Int = -1) {
        posicion = desde.coerceIn(-1, guion.size - 1)
    }

    /** La persona ha movido el texto a mano: seguimos desde ahí. */
    fun saltarA(indice: Int) = reiniciar(indice)

    /**
     * Recibe la hipótesis actual del reconocedor (parcial o final) y devuelve la nueva
     * posición, que es igual a la anterior si no hay coincidencia fiable.
     */
    fun escuchar(hipotesis: String): Int {
        val oidas = Texto.formas(hipotesis).takeLast(MAX_OIDAS)
        if (oidas.isEmpty() || guion.isEmpty()) return posicion
        val desde = max(0, posicion - ATRAS)
        val hasta = min(guion.size, posicion + 1 + ADELANTE)
        val mejor = alinear(oidas, desde, hasta) ?: return posicion
        if (mejor.fin > posicion) posicion = mejor.fin
        return posicion
    }

    private data class Alineacion(val fin: Int, val puntos: Double)

    private fun alinear(oidas: List<String>, desde: Int, hasta: Int): Alineacion? {
        val n = oidas.size
        val m = hasta - desde
        if (m <= 0) return null
        // h[i][j]: mejor puntuación de una alineación que acaba en oidas[i-1] y guion[desde+j-1]
        var anterior = DoubleArray(m + 1)
        var mejor: Alineacion? = null
        for (i in 1..n) {
            val actual = DoubleArray(m + 1)
            for (j in 1..m) {
                val parecido = parecido(oidas[i - 1], guion[desde + j - 1].forma)
                val diagonal = anterior[j - 1] + if (parecido > 0) parecido else FALLO
                val v = maxOf(0.0, diagonal, anterior[j] + HUECO, actual[j - 1] + HUECO)
                actual[j] = v
                // Solo cuentan finales sobre una palabra que de verdad coincide: si no, el
                // final de la alineación «se estira» por huecos hacia delante.
                if (parecido > 0 && v > 0) {
                    val fin = desde + j - 1
                    // Avanzar mucho de golpe exige más evidencia que seguir la línea.
                    val salto = max(0, fin - posicion - 1)
                    val ajustada = v - salto * PENALIZACION_SALTO
                    if (ajustada >= puntuacionMinima &&
                        (mejor == null || ajustada > mejor.puntos ||
                            (ajustada == mejor.puntos && fin > mejor.fin))
                    ) mejor = Alineacion(fin, ajustada)
                }
            }
            anterior = actual
        }
        return mejor
    }

    companion object {
        const val MAX_OIDAS = 8
        const val ATRAS = 3
        const val ADELANTE = 40
        private const val FALLO = -1.0
        private const val HUECO = -0.6
        private const val PENALIZACION_SALTO = 0.08

        /** 2 = idénticas, 1 = muy parecidas, 0 = distintas. */
        fun parecido(a: String, b: String): Double {
            if (a == b) return 2.0
            if (a.length < 3 || b.length < 3) return 0.0
            if (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a))) return 1.0
            val d = distancia(a, b)
            val ratio = 1.0 - d.toDouble() / max(a.length, b.length)
            return when {
                ratio >= 0.8 -> 1.5
                ratio >= 0.65 -> 1.0
                else -> 0.0
            }
        }

        private fun distancia(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) {
                    val coste = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + coste)
                }
                prev = cur
            }
            return prev[b.length]
        }
    }
}
