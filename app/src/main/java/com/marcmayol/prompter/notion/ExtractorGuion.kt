package com.marcmayol.prompter.notion

import org.json.JSONArray
import org.json.JSONObject

/**
 * Saca de los bloques de una página de Notion el texto que se lee a cámara.
 *
 * Las fichas de la base «YouTube» tienen esta forma:
 *
 *     ## Guion (~50 s)
 *     **Gancho (0–3 s)**          ← rótulo: no se lee
 *     > Tu LLM escribe…           ← cita: esto es lo que se lee
 *     **Desarrollo**
 *     > …
 *     ## En pantalla              ← notas de edición: fuera
 *
 * Regla: dentro de la sección cuyo encabezado empieza por «Guion», solo cuentan las citas.
 * Si la página no sigue ese formato, se cae a todo el texto de la página (párrafos y citas),
 * para que un guion escrito a pelo también sirva.
 */
object ExtractorGuion {

    fun extraer(bloques: List<JSONObject>): String {
        val seccion = seccionGuion(bloques)
        val citas = seccion.filter { it.optString("type") == "quote" }.map { textoDe(it) }.filter { it.isNotBlank() }
        if (citas.isNotEmpty()) return citas.joinToString("\n\n")
        val legibles = setOf("paragraph", "quote", "bulleted_list_item", "numbered_list_item", "callout")
        val todo = (seccion.ifEmpty { bloques })
            .filter { it.optString("type") in legibles }
            .map { textoDe(it) }
            .filter { it.isNotBlank() && !esRotulo(it) }
        return todo.joinToString("\n\n")
    }

    /** Bloques entre el encabezado «Guion…» y el siguiente encabezado del mismo nivel o superior. */
    private fun seccionGuion(bloques: List<JSONObject>): List<JSONObject> {
        val inicio = bloques.indexOfFirst { nivel(it) > 0 && textoDe(it).trim().startsWith("guion", ignoreCase = true) }
        if (inicio < 0) return emptyList()
        val nivelGuion = nivel(bloques[inicio])
        val resto = bloques.drop(inicio + 1)
        val fin = resto.indexOfFirst { val n = nivel(it); n in 1..nivelGuion }
        return if (fin < 0) resto else resto.take(fin)
    }

    private fun nivel(b: JSONObject): Int = when (b.optString("type")) {
        "heading_1" -> 1; "heading_2" -> 2; "heading_3" -> 3; else -> 0
    }

    /** «Gancho (0–3 s)», «Cierre»…: líneas cortas enteras en negrita que hacen de rótulo. */
    private fun esRotulo(texto: String): Boolean = texto.length < 40 && texto.matches(Regex("^[^.?!]*$")) &&
        listOf("gancho", "desarrollo", "cierre", "intro", "cta").any { texto.trim().lowercase().startsWith(it) }

    fun textoDe(bloque: JSONObject): String {
        val tipo = bloque.optString("type")
        val cuerpo = bloque.optJSONObject(tipo) ?: return ""
        return plano(cuerpo.optJSONArray("rich_text"))
    }

    fun plano(rich: JSONArray?): String {
        if (rich == null) return ""
        val sb = StringBuilder()
        for (i in 0 until rich.length()) sb.append(rich.optJSONObject(i)?.optString("plain_text").orEmpty())
        return sb.toString()
    }
}
