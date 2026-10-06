package com.marcmayol.prompter

import com.marcmayol.prompter.dominio.Alineador
import com.marcmayol.prompter.dominio.Texto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlineadorTest {

    private val guion = """
        Tu LLM escribe palabra a palabra, y cada palabra tiene que releer todo lo anterior.
        Para elegir el siguiente token, el modelo compara el nuevo con todos los anteriores.
        De cada token necesita dos cosas: su K, la clave, y su V, el valor.
        Sin caché, en cada paso vuelve a calcular la K y la V de todo el texto.
        La KV cache hace lo obvio: las calcula una vez, las guarda y las reutiliza.
    """.trimIndent()

    private val palabras = Texto.palabras(guion)
    private fun indiceDe(fragmento: String): Int {
        val inicio = guion.indexOf(fragmento)
        require(inicio >= 0) { "no está: $fragmento" }
        return palabras.indexOfFirst { it.inicio >= inicio } + Texto.palabras(fragmento).size - 1
    }

    /** Simula el reconocedor: hipótesis parciales que crecen palabra a palabra. */
    private fun leer(alineador: Alineador, frase: String) {
        val p = frase.split(" ")
        for (k in 1..p.size) alineador.escuchar(p.take(k).joinToString(" "))
    }

    @Test
    fun `lectura limpia avanza hasta el final de la frase`() {
        val a = Alineador(palabras)
        leer(a, "tu llm escribe palabra a palabra y cada palabra tiene que releer todo lo anterior")
        assertEquals(indiceDe("todo lo anterior"), a.posicion)
    }

    @Test
    fun `normaliza tildes y signos`() {
        assertEquals(listOf("sin", "cache", "en", "cada", "paso"), Texto.formas("Sin caché, en cada paso"))
    }

    @Test
    fun `tolera palabras mal reconocidas y saltadas`() {
        val a = Alineador(palabras)
        leer(a, "tu elemen escribe palabra a palabra y cada palabra tiene que relee todo lo anterior")
        leer(a, "para elegir el siguiente toque el modelo compara el nuevo con todos los anteriores")
        assertEquals(indiceDe("todos los anteriores"), a.posicion)
    }

    @Test
    fun `no se mueve con ruido o improvisacion`() {
        val a = Alineador(palabras)
        leer(a, "tu llm escribe palabra a palabra")
        val antes = a.posicion
        leer(a, "bueno esto que os cuento ahora me pasó ayer en el gimnasio")
        assertEquals(antes, a.posicion)
    }

    @Test
    fun `repetir una frase no rebobina`() {
        val a = Alineador(palabras)
        leer(a, "tu llm escribe palabra a palabra y cada palabra tiene que releer todo lo anterior")
        val antes = a.posicion
        leer(a, "tu llm escribe palabra a palabra")
        assertEquals(antes, a.posicion)
    }

    @Test
    fun `retoma tras improvisar`() {
        val a = Alineador(palabras)
        leer(a, "tu llm escribe palabra a palabra y cada palabra tiene que releer todo lo anterior")
        leer(a, "y esto es importante eh")
        leer(a, "para elegir el siguiente token el modelo compara")
        assertEquals(indiceDe("el modelo compara"), a.posicion)
    }

    @Test
    fun `una coincidencia suelta lejana no provoca un salto`() {
        val a = Alineador(palabras)
        leer(a, "tu llm escribe")
        val antes = a.posicion
        a.escuchar("reutiliza")
        assertEquals(antes, a.posicion)
    }

    @Test
    fun `si se salta una frase entera acaba encontrando la siguiente`() {
        val a = Alineador(palabras)
        leer(a, "tu llm escribe palabra a palabra y cada palabra tiene que releer todo lo anterior")
        leer(a, "de cada token necesita dos cosas su k la clave y su v el valor")
        assertEquals(indiceDe("su V, el valor"), a.posicion)
    }

    @Test
    fun `sensibilidad baja exige mas coincidencia que la alta`() {
        val exigente = Alineador(palabras, sensibilidad = 1)
        val permisivo = Alineador(palabras, sensibilidad = 5)
        exigente.escuchar("tu llm")
        permisivo.escuchar("tu llm")
        assertTrue(permisivo.posicion > exigente.posicion)
    }

    @Test
    fun `salto manual continua desde ahi`() {
        val a = Alineador(palabras)
        a.saltarA(indiceDe("todo el texto"))
        leer(a, "la kv cache hace lo obvio")
        assertEquals(indiceDe("hace lo obvio"), a.posicion)
    }
}
