# FM1 SCREEN Android v1.0 · compilación 027

Referencia de trabajo en `feat/fm1-screen-android` desde `2ddfcfc`, compilación 021. Sigue abierto el [PR #25](https://github.com/josxtm/FM1-USB-MULTIBOOT-/pull/25).

## Cambios

- **Conexión** separa descriptor, identidad recibida, perfil y protocolo. Las peticiones y el control remoto siguen requiriendo autorización explícita. El alias exacto Hegelton Instruments Felucca exige VID/PID `1209:0001` y excluye identificadores de cargador e HYP-1.
- **Vista** permite cambiar entre las tres vistas mediante botones o deslizamiento con el panel lateral abierto. Mantiene la categoría seleccionada. Pantalla completa cierra el panel. Las etiquetas musicales compactas vienen de la 021.
- **FM1 LIVE** arranca en estático. La aplicación valida las tramas de telemetría sin construir estados visuales en ese modo. En dinámico agrupa estados absolutos y suma los cambios relativos de encoders en un único buzón. La autorización de control remoto se mantiene separada. Los mensajes Note On/Off se registran como MIDI y no se convierten en pulsaciones físicas.
- **Rendimiento y energía**. ULTRA LOW LATENCY es el valor para instalaciones nuevas. Se respetan preferencias antiguas. El LCD mantiene el filtro eficiente por defecto y la ruta original de regiones RGB565. La pantalla Android se mantiene encendida mientras la sesión solicitada y permitida siga activa, con opción para desactivar esa política.
- **Capturas**. PNG nativo de 240 × 240, ampliación sin suavizado de 960 × 960 y captura de la ventana Android mediante PixelCopy. La exportación escribe en MediaStore fuera del hilo principal. El selector de compartir puede poner la actividad en segundo plano. La grabación MP4 guarda solo vídeo y reutiliza los planos YUV cuando el bitmap permanece igual.
- **Colores**. Se conservaron los temas y los 15 roles. La cuadrícula usa muestras reales y nombres accesibles. CUSTOM permite HSV/HEX, vista previa, aceptar, cancelar, restaurar y deshacer. Se conserva la importación de versiones anteriores del esquema JSON.
- **Avanzado** contiene diagnóstico, exportación, consola, licencia y créditos. El análisis de 30 segundos recomienda según evidencias y mantiene las preferencias. Los datos faltantes quedan identificados. Los umbrales de CPU (40 y 70 %), cola (25 y 75 %) y latencia interna P95 (20 y 50 ms) son iniciales. CPU normalizada = tiempo de CPU del proceso dividido por tiempo de pared y por número de procesadores lógicos disponibles. Cola = mayor fracción utilizada entre su límite de bytes y de fragmentos.
- **Icono**. El archivo exacto aprobado no estaba en los recursos disponibles. Se mantuvo el icono de 021 y el generador preparado para el original. [Recursos del icono](../design/icon/README.md)

## Arquitectura y archivos

| Área | Archivos principales |
|---|---|
| Panel lateral y gestos | `Settings.kt`, `SettingsPages.kt`, `SettingsIcons.kt`, `Faceplate.kt` |
| Temas | `SettingsAppearance.kt`, `Themes.kt`, preferencias de `MainActivity.kt` |
| Diagnóstico | `SettingsDiagnostics.kt`, `AndroidLoad.kt`, `HealthAnalysis.kt` |
| Recepción y panel | `MidiTransport.kt`, `DeviceIdentity.kt`, `MirrorSession.kt`, `MirrorPackets.kt`, `MidiIngress.kt`, `PanelUpdates.kt` |
| Archivos multimedia | `MediaExport.kt`, `Recording.kt`, `diagnostic_paths.xml`, `MainActivity.kt` |
| Validación y publicación | `gradle.properties`, `android_smoke.py`, pruebas JVM y `.github/workflows/fm1-screen-android.yml` |

Android MIDI recibe fragmentos en una cola acotada. Su hilo decodifica y aplica parches RGB565 en orden. La entrega del estado acumulado del LCD continúa usando Choreographer. La telemetría activada tiene su propia entrega agrupada. Las capturas, la compresión, el almacenamiento, la codificación de vídeo y el cálculo de métricas usan trabajo fuera del hilo de dibujo. La latencia interna termina al registrar el dibujo Android.

## Compatibilidad y restricciones

| Firmware | LCD y transporte respaldados | Identidad y control | Evidencia |
|---|---|---|---|
| SKIFF Atlatl 1.2.5 | Big Screen V1 por USB MIDI. BLE LCD por confirmar. | INFO y paquete distinguen la variante. La salida gráfica no incluye telemetría de teclas, LED o mandos. | LCD confirmado por el propietario en 017 y 018. Prueba sintética de 027 y validación física de 027 pendiente. [Emisor](https://github.com/LIkst4/skiff-atlatl/tree/2d4e390bc024640d7c8ca0621fc475363be7dd6e) |
| HYP-1 | Escucha MIDI pasiva. LCD por SysEx sin evidencia compatible. | TX y control remoto quedan bloqueados por política. La app no activa SEL+GLO. | USB observado en 016 y bloqueo de TX bajo prueba. [Compatibilidad](../FIRMWARE-COMPATIBILITY.md) |
| FRASIL 3.4.1, POLLY 0.6.4 y variantes LOOM-8 con emisor | fm1-base por USB según configuración; verificación de LCD BLE pendiente. | Hello anuncia la versión del protocolo. Panel y control exigen fm1-base v2, mensajes reales y autorización. | Código fuente y pruebas de Hello, parches, ACK y telemetría. [Configuraciones por versión](../FIRMWARE-COMPATIBILITY.md) |
| Variantes Big Screen V1 documentadas | Recepción de bloques RGB565 por MIDI. Los firmwares sin emisor quedan en escucha. | Sin identificación genérica de firmware ni estado físico del panel en los bloques. | Código de implementaciones y decodificador con fixtures. [Spyglass](https://github.com/pfkellogg/fm1-mirror-spyglass-android/tree/a5ee5336a192d3ff1c2b71b83692e07601b935b6) |
| FOMAJAM con Big Screen V2 | Receptor de bloques y SYNC probado de forma sintética. USB/BLE físicos pendientes. | SYNC distingue límites de imagen. El protocolo gráfico no confirma control de panel. | [Especificación V2](https://github.com/konsumer/foma/tree/885100f027356014b42a1d9c7d9902eb3ac41801/bigscreen) |

Atlatl usa `FS 01` para inicio y repintado, `FS 00` para renovar, `FS 02` para consulta de sonido y `FL 01` para INFO del editor. Se conserva la renovación objetivo de 900 ms con expiración de emisor cercana a 3 s, Clock F8 intercalado, fragmentación y recuperación acotada. El sonido se procesa en el FM-1. Los caminos de descarga, flasheo, edición persistente y cambio del cargador quedan fuera del control de la app. [Auditoría de comunicaciones](FASE-3-COMUNICACIONES.md) · [Fuente fm1-base](https://github.com/charlesvestal/fm1-base/blob/e4863fab25fcb5cf2be6d625992a017e1217d781/device/mirror.c)

## Resultados de entrega

La validación se registra a partir de los artefactos del workflow, incluida la comprobación del binario instalado. El modo DEMO sólo está disponible mediante el extra interno de arranque de una APK debug. La GUI de producción carece de dicho interruptor.

Pruebas físicas pendientes en 027. Duplicación real y duración con Atlatl 1.2.5, BLE con firmware compatible, latencia óptica, energía, control físico simultáneo, captura durante tráfico USB y selector de compartir con una aplicación externa. [Guía física](../PHYSICAL-TEST-PLAN.md)
