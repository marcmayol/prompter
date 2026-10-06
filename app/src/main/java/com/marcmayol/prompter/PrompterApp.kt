package com.marcmayol.prompter

import android.app.Application
import com.marcm.actualizador.Actualizador
import com.marcm.actualizador.ActualizadorConfig

class PrompterApp : Application() {
    /**
     * Prompter se distribuye fuera de Play Store (DracApps): consulta un manifiesto estático
     * en GitHub Pages y compara por versionCode entero. Mismo módulo que Kuse.
     */
    val actualizador: Actualizador by lazy {
        Actualizador(
            app = this,
            config = ActualizadorConfig(
                manifiestoUrl = MANIFIESTO_URL,
                versionCodeActual = BuildConfig.VERSION_CODE,
                checkHorasPorDefecto = 24,
            ),
        )
    }

    override fun onCreate() {
        super.onCreate()
        actualizador.programarPeriodica()
    }

    companion object {
        const val MANIFIESTO_URL = "https://marcmayol.com/prompter/updates.json"
    }
}
