package com.marcmayol.prompter

import com.marcmayol.prompter.notion.ExtractorGuion
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractorGuionTest {

    private fun bloque(tipo: String, vararg trozos: String) = JSONObject()
        .put("type", tipo)
        .put(tipo, JSONObject().put("rich_text", JSONArray().apply {
            trozos.forEach { put(JSONObject().put("plain_text", it)) }
        }))

    /** Tal como quedan las fichas de la base «YouTube». */
    private val ficha = listOf(
        bloque("heading_2", "Guion (~50 s)"),
        bloque("paragraph", "Gancho (0–3 s)"),
        bloque("quote", "Tu LLM escribe palabra a palabra… y cada palabra tiene que releer todo lo anterior."),
        bloque("paragraph", "Desarrollo"),
        bloque("quote", "Para elegir el siguiente token, ", "el modelo compara el nuevo con todos los anteriores.\nDe cada token necesita dos cosas."),
        bloque("paragraph", "Cierre"),
        bloque("quote", "¿El precio? Memoria."),
        bloque("heading_2", "En pantalla"),
        bloque("bulleted_list_item", "Fila de tokens que crece"),
        bloque("quote", "Esto no se lee"),
        bloque("heading_2", "Fuente"),
        bloque("paragraph", "Post del 5-oct-2026"),
    )

    @Test
    fun `solo las citas de la seccion Guion, unidas por parrafos`() {
        assertEquals(
            "Tu LLM escribe palabra a palabra… y cada palabra tiene que releer todo lo anterior.\n\n" +
                "Para elegir el siguiente token, el modelo compara el nuevo con todos los anteriores.\nDe cada token necesita dos cosas.\n\n" +
                "¿El precio? Memoria.",
            ExtractorGuion.extraer(ficha),
        )
    }

    @Test
    fun `una pagina sin formato devuelve todos sus parrafos`() {
        val libre = listOf(bloque("paragraph", "Hola, esto es un guion."), bloque("paragraph", "Segunda frase."))
        assertEquals("Hola, esto es un guion.\n\nSegunda frase.", ExtractorGuion.extraer(libre))
    }

    @Test
    fun `seccion Guion sin citas usa sus parrafos sin los rotulos`() {
        val sinCitas = listOf(
            bloque("heading_2", "Guion"),
            bloque("paragraph", "Gancho"),
            bloque("paragraph", "Lo que se lee."),
            bloque("heading_2", "Notas"),
            bloque("paragraph", "Esto no."),
        )
        assertEquals("Lo que se lee.", ExtractorGuion.extraer(sinCitas))
    }

    @Test
    fun `un subencabezado dentro del guion no corta la seccion`() {
        val conSub = listOf(
            bloque("heading_2", "Guion"),
            bloque("heading_3", "Parte 1"),
            bloque("quote", "Primera."),
            bloque("heading_3", "Parte 2"),
            bloque("quote", "Segunda."),
            bloque("heading_2", "En pantalla"),
            bloque("quote", "No."),
        )
        assertEquals("Primera.\n\nSegunda.", ExtractorGuion.extraer(conSub))
    }
}
