package com.marcmayol.prompter.notion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Una fila de la base: lo justo para listarla y elegirla. */
data class FichaNotion(
    val id: String,
    val titulo: String,
    val formato: String?,
    val estado: String?,
    val editada: String,
)

class ErrorNotion(mensaje: String) : IOException(mensaje)

/**
 * Cliente mínimo de la API de Notion (versión 2025-09-03, la de las «data sources»),
 * con HttpURLConnection y org.json: nada de dependencias nuevas para cuatro llamadas.
 */
class ClienteNotion(private val token: String) {

    /** Acepta la URL de la base tal cual se copia de Notion, o su id. */
    suspend fun fuenteDeDatos(urlOId: String): String {
        val id = idDe(urlOId) ?: throw ErrorNotion("No reconozco esa dirección de Notion")
        // Puede ser una base (que contiene fuentes de datos) o directamente una fuente de datos.
        val base = runCatching { pedir("GET", "databases/$id") }.getOrNull()
        val fuentes = base?.optJSONArray("data_sources")
        if (fuentes != null && fuentes.length() > 0) return fuentes.getJSONObject(0).getString("id")
        pedir("GET", "data_sources/$id")
        return id
    }

    suspend fun fichas(fuente: String): List<FichaNotion> {
        val salida = mutableListOf<FichaNotion>()
        var cursor: String? = null
        do {
            val cuerpo = JSONObject().put("page_size", 100)
                .put("sorts", JSONArray().put(JSONObject().put("timestamp", "last_edited_time").put("direction", "descending")))
            cursor?.let { cuerpo.put("start_cursor", it) }
            val r = pedir("POST", "data_sources/$fuente/query", cuerpo)
            val res = r.getJSONArray("results")
            for (i in 0 until res.length()) salida += ficha(res.getJSONObject(i))
            cursor = if (r.optBoolean("has_more")) r.optString("next_cursor") else null
        } while (cursor != null)
        return salida
    }

    /** Todos los bloques de primer nivel de la página, en orden (con paginación). */
    suspend fun bloques(pagina: String): List<JSONObject> {
        val salida = mutableListOf<JSONObject>()
        var cursor: String? = null
        do {
            val r = pedir("GET", "blocks/$pagina/children?page_size=100" + (cursor?.let { "&start_cursor=$it" } ?: ""))
            val res = r.getJSONArray("results")
            for (i in 0 until res.length()) salida += res.getJSONObject(i)
            cursor = if (r.optBoolean("has_more")) r.optString("next_cursor") else null
        } while (cursor != null)
        return salida
    }

    /** Pone el select «Estado» de la página en [valor], si la página tiene esa propiedad. */
    suspend fun marcarEstado(pagina: String, valor: String) {
        val cuerpo = JSONObject().put("properties", JSONObject().put("Estado", JSONObject().put("select", JSONObject().put("name", valor))))
        pedir("PATCH", "pages/$pagina", cuerpo)
    }

    private fun ficha(p: JSONObject): FichaNotion {
        val props = p.getJSONObject("properties")
        var titulo = "(sin título)"
        var formato: String? = null
        var estado: String? = null
        for (k in props.keys()) {
            val v = props.getJSONObject(k)
            when (v.optString("type")) {
                "title" -> titulo = ExtractorGuion.plano(v.optJSONArray("title")).ifBlank { titulo }
                "select" -> {
                    val nombre = v.optJSONObject("select")?.optString("name")
                    if (k.equals("Formato", true)) formato = nombre
                    if (k.equals("Estado", true)) estado = nombre
                }
                "status" -> if (k.equals("Estado", true)) estado = v.optJSONObject("status")?.optString("name")
            }
        }
        return FichaNotion(p.getString("id"), titulo, formato, estado, p.optString("last_edited_time"))
    }

    private suspend fun pedir(metodo: String, ruta: String, cuerpo: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val c = URL("https://api.notion.com/v1/$ruta").openConnection() as HttpURLConnection
        try {
            c.requestMethod = if (metodo == "PATCH") "POST" else metodo
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Notion-Version", VERSION)
            c.setRequestProperty("Content-Type", "application/json")
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            if (metodo == "PATCH") parchear(c)
            if (cuerpo != null) { c.doOutput = true; c.outputStream.use { it.write(cuerpo.toString().toByteArray()) } }
            val codigo = c.responseCode
            val texto = (if (codigo in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (codigo !in 200..299) {
                val msg = runCatching { JSONObject(texto).optString("message") }.getOrNull().orEmpty()
                throw ErrorNotion(when (codigo) {
                    401 -> "El token no es válido"
                    403, 404 -> "No tengo acceso: comparte la base con la integración (··· › Conexiones)"
                    429 -> "Notion pide esperar un poco"
                    else -> "Notion respondió $codigo${if (msg.isNotBlank()) ": $msg" else ""}"
                })
            }
            JSONObject(texto)
        } finally { c.disconnect() }
    }

    /**
     * HttpURLConnection no admite PATCH. Se fija el campo `method` por reflexión, y no solo en
     * el objeto que devuelve URL.openConnection(): con https Android da una envoltura que delega
     * en otra conexión (campo `delegate`), y es esa la que hace la petición.
     */
    private fun parchear(c: HttpURLConnection) {
        val metodo = HttpURLConnection::class.java.getDeclaredField("method").apply { isAccessible = true }
        val vistos = mutableSetOf<Any>()
        fun fijar(o: Any?) {
            if (o == null || !vistos.add(o)) return
            if (o is HttpURLConnection) runCatching { metodo.set(o, "PATCH") }
            var k: Class<*>? = o.javaClass
            while (k != null && k != Any::class.java) {
                k.declaredFields.filter { it.name == "delegate" }.forEach { f -> runCatching { f.isAccessible = true; fijar(f.get(o)) } }
                k = k.superclass
            }
        }
        fijar(c)
    }

    companion object {
        const val VERSION = "2025-09-03"

        /** Saca el id de 32 hex de una URL de Notion (o lo devuelve si ya es un id). */
        fun idDe(texto: String): String? {
            val limpio = texto.substringBefore('?').substringBefore('#')
            val m = Regex("([0-9a-fA-F]{32})|([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})").findAll(limpio).lastOrNull()
            return m?.value?.replace("-", "")?.lowercase()
        }
    }
}
