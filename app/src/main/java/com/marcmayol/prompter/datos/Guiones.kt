package com.marcmayol.prompter.datos

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Guion(
    val id: String = UUID.randomUUID().toString(),
    val titulo: String,
    val texto: String,
    val editado: Long = System.currentTimeMillis(),
    /** Si viene de Notion: id de su página, para reimportarlo encima y marcarlo como grabado. */
    val notionId: String? = null,
)

/** Un guion = un JSON en filesDir/guiones. Se escribe a disco en cuanto se guarda. */
class RepositorioGuiones(context: Context) {
    private val carpeta = File(context.filesDir, "guiones").apply { mkdirs() }
    private val _guiones = MutableStateFlow<List<Guion>>(emptyList())
    val guiones: StateFlow<List<Guion>> = _guiones

    suspend fun cargar() = withContext(Dispatchers.IO) {
        val lista = carpeta.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { leer(it) }
        if (lista.isEmpty()) {
            val ejemplo = Guion(titulo = "Ejemplo: qué es la KV cache", texto = EJEMPLO)
            escribir(ejemplo)
            _guiones.value = listOf(ejemplo)
        } else {
            _guiones.value = lista.sortedByDescending { it.editado }
        }
    }

    fun buscar(id: String): Guion? = _guiones.value.firstOrNull { it.id == id }

    /** Importa una ficha de Notion: si ya estaba, la actualiza en lugar de duplicarla. */
    suspend fun importar(notionId: String, titulo: String, texto: String): Guion {
        val previo = _guiones.value.firstOrNull { it.notionId == notionId }
        val g = previo?.copy(titulo = titulo, texto = texto) ?: Guion(titulo = titulo, texto = texto, notionId = notionId)
        guardar(g)
        return g
    }

    suspend fun guardar(g: Guion) = withContext(Dispatchers.IO) {
        val nuevo = g.copy(editado = System.currentTimeMillis())
        escribir(nuevo)
        _guiones.value = (listOf(nuevo) + _guiones.value.filter { it.id != g.id })
    }

    suspend fun borrar(id: String) = withContext(Dispatchers.IO) {
        File(carpeta, "$id.json").delete()
        _guiones.value = _guiones.value.filter { it.id != id }
    }

    private fun escribir(g: Guion) {
        val json = JSONObject().put("id", g.id).put("titulo", g.titulo).put("texto", g.texto).put("editado", g.editado)
            .put("notionId", g.notionId)
        val tmp = File(carpeta, "${g.id}.json.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(File(carpeta, "${g.id}.json"))
    }

    private fun leer(f: File): Guion? = runCatching {
        val j = JSONObject(f.readText())
        Guion(j.getString("id"), j.getString("titulo"), j.getString("texto"), j.optLong("editado"),
            j.optString("notionId").takeIf { it.isNotBlank() && it != "null" })
    }.getOrNull()

    companion object {
        val EJEMPLO = """
            Tu LLM escribe palabra a palabra, y cada palabra tiene que releer todo lo anterior.

            Para elegir el siguiente token, el modelo compara el nuevo con todos los anteriores. De cada token necesita dos cosas: su K, la clave, y su V, el valor.

            Sin caché, en cada paso vuelve a calcular la K y la V de todo el texto. Con diez tokens, poco. Con cien mil, una barbaridad.

            La KV cache hace lo obvio: las calcula una vez, las guarda y las reutiliza. Así cada paso solo calcula el token nuevo.

            ¿El precio? Memoria: unos 128 KiB por token en un modelo de 8B en FP16. Con contextos largos se nota, y mucho. Te hago la cuenta en el siguiente short.
        """.trimIndent()
    }
}
