# Arquitectura Android de FM1 SCREEN

| Capa | Código | Responsabilidad |
|---|---|---|
| Núcleo Android | `:core/src/main` | Biblioteca Kotlin/JVM interna. SysEx, decodificación, framebuffer RGB565 de 240 × 240, sesión, comandos, modelos de perfil, roles de color y geometría. |
| Transport | `:app/transport` | Enumera USB Host, abre MIDI USB o BLE por Android MIDI, selecciona un puerto, gestiona permisos y reconexión. Proporciona reloj monotónico, envío de bytes y ticks serializados al núcleo. |
| GUI | `:app/ui` | Compose dibuja el panel adaptado de RemoteFM1Android. Los gestos llaman a `PanelOutput` y la interfaz recibe estados mediante `MirrorListener`. |
| Assets | `assets/` y `:app/src/main/res` | Perfiles y temas JSON en un directorio común. Fuentes OFL y recursos Android en la app. |
| Android | `:app` | Permisos, ciclo de vida, preferencias privadas, lectura JSON, captura PNG, codificación MP4 del LCD y exportación con el selector de documentos. |

La sesión recibe eventos en un hilo serial. Valida regiones y publica instantáneas RGB565 inmutables de 115.200 bytes. Android copia en una matriz reutilizable y actualiza un bitmap RGB565. La interfaz conserva la publicación más reciente cuando se acumulan actualizaciones. El diagnóstico distingue publicaciones sustituidas, bloques, sincronizaciones, FPS que dibuja el LCD y tiempos locales. La grabación MP4 usa otro hilo. El instrumento ejecuta el firmware, síntesis, DSP, secuenciador y audio.

Los perfiles declaran nombres y metadatos. El adaptador Android valida el JSON y el núcleo valida el modelo. Las opciones de control dependen de la identidad y de la negociación, con autorización expresa. El estado de cada control distingue telemetría del FM-1, interacción local provisional, valor inferido y valor desconocido. Los detentes inferidos de un encoder son relativos. Un potenciómetro informa un valor absoluto únicamente cuando el protocolo aporta telemetría.

La biblioteca interna usa el plugin Kotlin/JVM para ejecutar pruebas unitarias sin un emulador ni acceso al sistema MIDI. La app se compila con Kotlin y Jetpack Compose para móvil y tablet Android.
