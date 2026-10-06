# Vosk llama a su librería nativa a través de JNA, que funciona por reflexión:
# si R8 renombra o quita estas clases, el reconocimiento falla en tiempo de ejecución.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
