package com.marcmayol.prompter.notion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Conexión con Notion: token de la integración y fuente de datos elegida.
 *
 * El token se guarda cifrado con una clave AES del Android Keystore, que no sale del
 * móvil: aunque la copia de seguridad se lleve el archivo, sin esa clave no sirve.
 */
class AlmacenNotion(context: Context) {
    private val prefs = context.getSharedPreferences("notion", Context.MODE_PRIVATE)

    val conectado: Boolean get() = token() != null && fuente != null

    var fuente: String?
        get() = prefs.getString("fuente", null)
        set(v) = prefs.edit().putString("fuente", v).apply()

    var nombreBase: String?
        get() = prefs.getString("nombre_base", null)
        set(v) = prefs.edit().putString("nombre_base", v).apply()

    /** Al guardar un vídeo de un guion de Notion, poner su Estado en «Grabado». */
    var marcarGrabado: Boolean
        get() = prefs.getBoolean("marcar_grabado", true)
        set(v) = prefs.edit().putBoolean("marcar_grabado", v).apply()

    fun token(): String? = runCatching {
        val cifrado = prefs.getString("token", null) ?: return null
        val datos = Base64.decode(cifrado, Base64.NO_WRAP)
        val c = Cipher.getInstance(AES)
        c.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, datos, 0, 12))
        String(c.doFinal(datos, 12, datos.size - 12))
    }.getOrNull()

    fun guardarToken(token: String) {
        val c = Cipher.getInstance(AES)
        c.init(Cipher.ENCRYPT_MODE, clave())
        val datos = c.iv + c.doFinal(token.trim().toByteArray())
        prefs.edit().putString("token", Base64.encodeToString(datos, Base64.NO_WRAP)).apply()
    }

    fun desconectar() = prefs.edit().remove("token").remove("fuente").remove("nombre_base").apply()

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private companion object {
        const val ALIAS = "prompter_notion"
        const val AES = "AES/GCM/NoPadding"
    }
}
