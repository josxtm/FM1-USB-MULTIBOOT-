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

Android MIDI recibe fragmentos en una cola acotada. Su hilo decodifica y aplica parches RGB565 en orden. La entrega del estado acumulado del LCD continúa usando Choreographer. La telemetría activada tiene su propia entrega agrupada. PixelCopy obtiene la ventana de forma asíncrona. La compresión, el almacenamiento, la codificación de vídeo y el cálculo de métricas se ejecutan fuera del hilo de dibujo. Cambiar un tema solo actualiza estado de Compose y preferencias. La auditoría de código verificó que esa ruta carece de llamadas de reconexión MIDI. La latencia interna termina al registrar el dibujo Android.

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

### APK y ejecución

- Commit empaquetado y verificado `697cdfd30dcecfbd80a747aaa1ca46409ef80d16`. Identificador Android `com.josxtm.fm1screen`, `versionCode 27`, `versionName 1.0`, Android 10 o posterior.
- GitHub Actions `46167288809`, artefacto `15271339637`. El ZIP se descargó y su APK se verificó antes de instalarla en emuladores. [Distribución](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/46167288809/artifacts/15271339637)
- SHA-256 de la APK `00542138a9f4f16e8cdbc1ee75b75bbd14ed5a14f8299707f84345b47114e9aa`.
- SHA-256 del ZIP `8ae3f385547bfd67b315d9142b947f1b2ac92d68e0ef4a0796cbab69cecaea6a`.
- Certificado SHA-256 `3b021b4e969fd235810fecbd0aad173e14e44c4ccf65d221152f5e3e8d285590`. El certificado difiere del de la 021 de referencia. Exporta los temas y perfiles antes de una instalación limpia.

### Pruebas observadas

- **51 pruebas JVM superadas**. Android Lint terminó con 0 errores, 34 avisos y 5 sugerencias. La suite incluye filtros de envío, HYP-1 pasivo, ACK, decodificación, sincronización, telemetría, contraste y preservación del LCD. [Pruebas y Lint](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/46167288809/artifacts/15271599091)
- El ensayo Atlatl permaneció activo **600,026 s de reloj real**. Comprobó más de 15.000 bloques, Clock F8 intercalado, la cadencia de renovación, cobertura completa, pérdida nula en la entrada sintética y los 57.600 píxeles finales. [Ensayo de estabilidad](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/46167288809/artifacts/15272571094)
- Android 16 instaló la misma APK en dos emuladores. La tablet completó 100 ciclos y el móvil 12 ciclos horizontales más la comprobación en vertical. Se verificaron las tres vistas, los cuatro apartados, los gestos con el panel abierto, la persistencia, el diagnóstico y los temas. [Tablet](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/46168111397/artifacts/15272434403) · [Teléfono](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/46168111397/artifacts/15271884309)
- El emulador de tablet guardó los PNG de 240 × 240, 960 × 960 y de la ventana (1920 × 1200). El PNG ampliado coincide píxel a píxel con la ampliación del original. FFprobe verificó una única pista H.264 de 240 × 240 en el MP4. La grabación conserva el límite de 20 minutos. Los archivos provienen de DEMO. Compartir con una aplicación externa queda para comprobación física. [Archivos y capturas de tablet](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/46168111397/artifacts/15272434403)

La primera comprobación de capturas agotó su plazo de lectura ADB. La ejecución visual de repetición verificó el estado de almacenamiento y leyó los archivos con acceso del propietario cuando fue necesario. Instaló el mismo binario y comprobó su huella. [Validación visual 46168111397](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/46168111397)

### Comparativa de rendimiento

La reproducción JVM usó los mismos 9.232 bloques Atlatl sintéticos por perfil en 021 y 027. El hash final `81531C59` y todos los píxeles coincidieron. Los tiempos de la tabla usan el reloj virtual hasta presentación.

| Perfil | P50 / P95 / P99 en 021 | P50 / P95 / P99 en 027 |
|---|---:|---:|
| ULTRA LOW LATENCY | 8 / 15 / 15 ms | 8 / 15 / 15 ms |
| BALANCED | 17 / 28 / 30 ms | 17 / 28 / 30 ms |
| ECO | 40 / 71 / 77 ms | 40 / 71 / 77 ms |

En una segunda reproducción fm1-base se enviaron 12.000 parches y 12.000 estados de panel por ensayo. En ambos modos se mantuvieron 12.000 publicaciones y el mismo raster `AE885801`. Los callbacks del panel fueron 0 en estático y 12.000 en dinámico, antes de la agrupación de la interfaz. Las medianas de CPU JVM sobre cinco ensayos fueron 20,953 ms y 22,653 ms para el flujo completo, respectivamente. Este resultado mide el decodificador y los buzones, sin medir composición de Android, USB real ni latencia óptica.

[Comparación completa de fuentes frente a la 021](https://github.com/josxtm/FM1-USB-MULTIBOOT-/compare/2ddfcfc9116c4d798c62edb5290d520b3f4900c6...697cdfd30dcecfbd80a747aaa1ca46409ef80d16)
