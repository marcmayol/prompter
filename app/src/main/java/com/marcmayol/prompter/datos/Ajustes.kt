package com.marcmayol.prompter.datos

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Seguimiento { VOZ, AUTOMATICO, MANUAL }
enum class Camara { FRONTAL, TRASERA }
enum class Calidad(val etiqueta: String) { UHD("4K"), FHD("1080p"), HD("720p") }
enum class Formato(val etiqueta: String) { HORIZONTAL("Horizontal 16:9"), VERTICAL("Vertical 9:16") }
enum class Fuente(val etiqueta: String) { SANS("Sans"), SERIF("Serif"), MONO("Mono") }

/** Todo lo que la persona puede ajustar. Se guarda en DataStore: sobrevive a que Android mate la app. */
data class Ajustes(
    val colorTexto: Long = 0xFFF2EEE8,
    val colorFondo: Long = 0xFF0E0E10,
    val colorMarcador: Long = 0xFFD62828,
    val tamanoLetra: Float = 34f,
    val interlineado: Float = 1.35f,
    val margen: Float = 24f,
    /** Fracción del ancho que ocupa la columna de texto (0,5–1). */
    val anchoColumna: Float = 1f,
    /** Altura de la línea de lectura, como fracción de la pantalla (0 arriba, 1 abajo). */
    val alturaLectura: Float = 0.28f,
    val fuente: Fuente = Fuente.SANS,
    val opacidadLeido: Float = 0.35f,
    val seguimiento: Seguimiento = Seguimiento.VOZ,
    /** 1 exigente … 5 permisivo. */
    val sensibilidad: Int = 3,
    /**
     * Cuántas palabras por delante de la última reconocida se usan para colocar el texto.
     * El reconocedor va unas décimas por detrás de la voz: así el salto de línea llega a tiempo.
     */
    val adelanto: Int = 3,
    /** Velocidad del modo automático, en píxeles por segundo (en dp). */
    val velocidadAuto: Float = 40f,
    val espejo: Boolean = false,
    val cuentaAtras: Int = 3,
    val camara: Camara = Camara.FRONTAL,
    val calidad: Calidad = Calidad.FHD,
    val formato: Formato = Formato.VERTICAL,
    val verCamara: Boolean = true,
    val fps60: Boolean = false,
    val estabilizacion: Boolean = true,
    /** Vídeo HDR de 10 bits (HLG), si la cámara lo admite. */
    val hdr: Boolean = false,
    /** Más tasa de bits que la de serie: menos compresión, archivos más grandes. */
    val tasaAlta: Boolean = true,
    /** Compensación de exposición, en pasos EV (-2 … +2). */
    val exposicion: Float = 0f,
    /** 1 = normal; por debajo de 1, gran angular si la cámara lo tiene. */
    val zoom: Float = 1f,
)

private val Context.almacen by preferencesDataStore("ajustes")

class RepositorioAjustes(private val context: Context) {
    private object K {
        val colorTexto = longPreferencesKey("color_texto")
        val colorFondo = longPreferencesKey("color_fondo")
        val colorMarcador = longPreferencesKey("color_marcador")
        val tamanoLetra = floatPreferencesKey("tamano_letra")
        val interlineado = floatPreferencesKey("interlineado")
        val margen = floatPreferencesKey("margen")
        val anchoColumna = floatPreferencesKey("ancho_columna")
        val alturaLectura = floatPreferencesKey("altura_lectura")
        val fuente = stringPreferencesKey("fuente")
        val opacidadLeido = floatPreferencesKey("opacidad_leido")
        val seguimiento = stringPreferencesKey("seguimiento")
        val sensibilidad = intPreferencesKey("sensibilidad")
        val adelanto = intPreferencesKey("adelanto")
        val velocidadAuto = floatPreferencesKey("velocidad_auto")
        val espejo = booleanPreferencesKey("espejo")
        val cuentaAtras = intPreferencesKey("cuenta_atras")
        val camara = stringPreferencesKey("camara")
        val calidad = stringPreferencesKey("calidad")
        val formato = stringPreferencesKey("formato")
        val verCamara = booleanPreferencesKey("ver_camara")
        val fps60 = booleanPreferencesKey("fps60")
        val estabilizacion = booleanPreferencesKey("estabilizacion")
        val hdr = booleanPreferencesKey("hdr")
        val tasaAlta = booleanPreferencesKey("tasa_alta")
        val exposicion = floatPreferencesKey("exposicion")
        val zoom = floatPreferencesKey("zoom")
    }

    private inline fun <reified E : Enum<E>> Preferences.enumOr(k: Preferences.Key<String>, def: E): E =
        this[k]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: def

    val ajustes: Flow<Ajustes> = context.almacen.data.map { p ->
        val d = Ajustes()
        Ajustes(
            colorTexto = p[K.colorTexto] ?: d.colorTexto,
            colorFondo = p[K.colorFondo] ?: d.colorFondo,
            colorMarcador = p[K.colorMarcador] ?: d.colorMarcador,
            tamanoLetra = p[K.tamanoLetra] ?: d.tamanoLetra,
            interlineado = p[K.interlineado] ?: d.interlineado,
            margen = p[K.margen] ?: d.margen,
            anchoColumna = p[K.anchoColumna] ?: d.anchoColumna,
            alturaLectura = p[K.alturaLectura] ?: d.alturaLectura,
            fuente = p.enumOr(K.fuente, d.fuente),
            opacidadLeido = p[K.opacidadLeido] ?: d.opacidadLeido,
            seguimiento = p.enumOr(K.seguimiento, d.seguimiento),
            sensibilidad = p[K.sensibilidad] ?: d.sensibilidad,
            adelanto = p[K.adelanto] ?: d.adelanto,
            velocidadAuto = p[K.velocidadAuto] ?: d.velocidadAuto,
            espejo = p[K.espejo] ?: d.espejo,
            cuentaAtras = p[K.cuentaAtras] ?: d.cuentaAtras,
            camara = p.enumOr(K.camara, d.camara),
            calidad = p.enumOr(K.calidad, d.calidad),
            formato = p.enumOr(K.formato, d.formato),
            verCamara = p[K.verCamara] ?: d.verCamara,
            fps60 = p[K.fps60] ?: d.fps60,
            estabilizacion = p[K.estabilizacion] ?: d.estabilizacion,
            hdr = p[K.hdr] ?: d.hdr,
            tasaAlta = p[K.tasaAlta] ?: d.tasaAlta,
            exposicion = p[K.exposicion] ?: d.exposicion,
            zoom = p[K.zoom] ?: d.zoom,
        )
    }

    suspend fun guardar(a: Ajustes) {
        context.almacen.edit { p ->
            p[K.colorTexto] = a.colorTexto
            p[K.colorFondo] = a.colorFondo
            p[K.colorMarcador] = a.colorMarcador
            p[K.tamanoLetra] = a.tamanoLetra
            p[K.interlineado] = a.interlineado
            p[K.margen] = a.margen
            p[K.anchoColumna] = a.anchoColumna
            p[K.alturaLectura] = a.alturaLectura
            p[K.fuente] = a.fuente.name
            p[K.opacidadLeido] = a.opacidadLeido
            p[K.seguimiento] = a.seguimiento.name
            p[K.sensibilidad] = a.sensibilidad
            p[K.adelanto] = a.adelanto
            p[K.velocidadAuto] = a.velocidadAuto
            p[K.espejo] = a.espejo
            p[K.cuentaAtras] = a.cuentaAtras
            p[K.camara] = a.camara.name
            p[K.calidad] = a.calidad.name
            p[K.formato] = a.formato.name
            p[K.verCamara] = a.verCamara
            p[K.fps60] = a.fps60
            p[K.estabilizacion] = a.estabilizacion
            p[K.hdr] = a.hdr
            p[K.tasaAlta] = a.tasaAlta
            p[K.exposicion] = a.exposicion
            p[K.zoom] = a.zoom
        }
    }

    suspend fun restablecer() = guardar(Ajustes())
}
