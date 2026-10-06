# Prompter

Teleprompter para Android que **sigue tu voz**: el texto avanza según lees, sin tocar nada, y la propia app graba el vídeo.

## La decisión que lo condiciona todo

Desde Android 10, si una app graba vídeo y otra escucha el micro a la vez, el sistema le da silencio a la segunda **sin avisar**. Por eso aquí solo hay un dueño del micro (`audio/MotorAudio.kt`): lee el audio una vez y reparte cada trozo al reconocedor de voz (a 16 kHz) y, mientras graba, al codificador AAC. CameraX graba **solo vídeo** y al parar se unen las dos pistas en un MP4 (`grabacion/Grabadora.kt`), que va a `Películas/Prompter`.

## Piezas

| Archivo | Qué hace |
|---|---|
| `dominio/Alineador.kt` | Casa lo que oye el reconocedor con el guion: alineamiento local con coincidencias aproximadas sobre una ventana por delante. Tolera palabras mal oídas o saltadas, no se mueve si improvisas y nunca rebobina. Tests en `AlineadorTest`. |
| `voz/Reconocedor.kt` | Vosk en local, streaming, modelo pequeño en español dentro del APK (sin red ni servicios de Google). |
| `datos/Ajustes.kt` | Todo lo personalizable, en DataStore: colores de texto, fondo y línea de lectura; tipo y tamaño de letra; interlineado, márgenes, ancho de columna, altura de la línea de lectura, opacidad de lo ya leído; modo voz/automático/manual, sensibilidad, velocidad; espejo, cuenta atrás; cámara, formato (vertical u horizontal) y calidad. |
| `datos/Guiones.kt` | Los guiones, un JSON por guion en `filesDir/guiones`. |

## Compilar

El modelo de voz no está en git: descarga `vosk-model-small-es-0.42` de alphacephei.com, descomprímelo en `app/src/main/assets/model-es` (sin el README) y crea ahí un archivo `uuid` con el nombre del modelo.

```
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Sin `keystore.properties` la release se firma con el debug.keystore: sirve para probar, no para publicar.

## Probar en el emulador

En las builds de depuración, si existe `Android/data/com.marcmayol.prompter/files/prueba.wav` (PCM 16 bits, mono, 48 kHz) se usa en lugar del micro, al ritmo real. Así se comprueba el seguimiento por voz sin hablar. El AVD `prompter_test` tiene cámara frontal emulada.

## Publicar una versión

Se distribuye por [DracApps](https://marcmayol.com/DracApps/) y se actualiza sola con el módulo `actualizador` (el mismo que Kuse), que lee `https://marcmayol.com/prompter/updates.json`.

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
2. `python scripts/publicar_release.py --dry-run --notas "Qué cambia…"`
3. `python scripts/publicar_release.py --notas "Qué cambia…"`

El script construye el APK firmado, comprueba versionCode, sha256 y que la firma es la de siempre (nunca la de debug), crea la Release y publica el manifiesto. La firma sale de `keystore.properties` (gitignored) apuntando a `C:/Users/marcm/prompter-release.jks`: **si se pierde esa keystore, ninguna instalación podrá volver a actualizarse.**
