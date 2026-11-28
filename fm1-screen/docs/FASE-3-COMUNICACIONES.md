# Fase 3 · USB MIDI y pantalla real

## Resultado de la prueba 016

En la tablet con el FM-1 físico, HYP-1 se enumeró como `RedtailWorks HYP-1 FM-1` y SKIFF Atlatl v1.2.5 como `Felucca`. Ambos informaron VID/PID `1209:0001` y un puerto MIDI IN y OUT. En Atlatl se observaron 2.278 mensajes MIDI, unos 48 B/s y mensajes Clock `F8`. La aplicación no recibió píxeles. El visor externo Big Screen + Spyglass sí mostró el LCD inicialmente y se congeló alrededor de los cinco segundos, aun al apagar Smooth, reiniciar la duplicación o detener el secuenciador. Son resultados físicos comunicados por el propietario para la 016 y el visor de referencia.

En `MidiTransport.open` de la 016, `openOutputPort(0)` abre la ruta FM-1 → Android que recibió Clock. `openInputPort(0)`, que corresponde a Android → FM-1, quedaba condicionado a `Target.recognized`. Ni `Felucca` ni `RedtailWorks HYP-1 FM-1` pasaban el filtro de nombre. Por tanto, la app no intentaba abrir el puerto de envío y **Solicitar duplicación** quedaba deshabilitado. Una sesión con identificación elegible podía enviar órdenes antes de ver una imagen. El bloqueo comprobado estaba en la identificación del destino y la apertura del puerto, sin dependencia de un fotograma previo. [Transporte en 016](https://github.com/josxtm/FM1-USB-MULTIBOOT-/blob/837906647a34701af2e5657b6e7685e694cf82b9/fm1-screen/app/src/main/java/com/josxtm/fm1screen/transport/MidiTransport.kt#L323) · [Ajustes en 016](https://github.com/josxtm/FM1-USB-MULTIBOOT-/blob/837906647a34701af2e5657b6e7685e694cf82b9/fm1-screen/app/src/main/java/com/josxtm/fm1screen/ui/Settings.kt#L134).

## Fuentes examinadas

| Fuente y revisión | Archivos | Evidencia y licencia |
|---|---|---|
| [Skiff Atlatl V1.2.5, 2d4e390](https://github.com/LIkst4/skiff-atlatl/tree/2d4e390bc024640d7c8ca0621fc475363be7dd6e) | `firmware/src/mirror.c`, `screen_mirror/screen_mirror.c`, `main.c`, `usb.c`, `editor.c`, `ota.c`, `tools/build.py` | Emisor Big Screen V1, INFO y caducidad. GPL-3.0-only, componente `screen_mirror` MIT. |
| [Big Screen + Spyglass Android, a5ee533](https://github.com/pfkellogg/fm1-mirror-spyglass-android/tree/a5ee5336a192d3ff1c2b71b83692e07601b935b6) | `Codec.java`, `MainActivity.java`, `BigScreen2.java`, `Fm1Port.java` | Identificación por INFO más identidad de paquete, renovación alrededor de 900 ms y modos gráficos. GPL-3.0. |
| [FM-1 Mirror, 2f6dd20](https://github.com/pfkellogg/fm1-mirror/tree/2f6dd20f08ab3a9b592c50e59d9fa754eaa4635f) | `README.md` y referencias del emisor | El autor documenta Atlatl 1.2 con mirroring incorporado. Visor GPL-3.0 y emisor MIT. |
| [fm1-base, e4863fa](https://github.com/charlesvestal/fm1-base/blob/e4863fab25fcb5cf2be6d625992a017e1217d781/device/mirror.c) | `device/mirror.c` | Paquetes por región con secuencia, respuesta HELLO y panel remoto. MPL-2.0. |
| [Big Screen V2, 885100f](https://github.com/konsumer/foma/tree/885100f027356014b42a1d9c7d9902eb3ac41801/bigscreen) | `bigscreen/README.md` y `bigscreen.h` | HELLO, INFO por saludo, RECT y SYNC. MIT. |
| [RemoteFM1, 26338e5](https://github.com/Quixotic7/RemoteFM1/tree/26338e52a28c0ceba400384bc1d160ea03570fae) | `README.md` y firmware del puente | El flujo LCD USB Bulk examinado va del host al FM-1. GPL-3.0 y licencias por componente. La GUI mantiene su adaptación GPL de RemoteFM1Android. |
| [HYP-1, 9534914](https://github.com/RustyPawz/fm1-HYP1/tree/95349144a8787760622bc07628e9909b93d05852) | `app/fm1.toml`, `README.md` y [manual de Remote](https://rustypawz.github.io/fm1-HYP1/emu/) | Descriptor RedtailWorks. El modo Remote reproduce en el emulador y silencia el FM-1. El perfil examinado no habilita el emisor LCD de fm1-base. Referencia documental, GPL-3.0-only para la aplicación y licencias por componente. |

## Órdenes habilitadas para Felucca

| Mensaje | Dirección | Acción |
|---|---|---|
| `F0 7D 46 53 01 F7` | Android → FM-1 | Inicia Big Screen V1 y pide cubrir toda la pantalla. Se usa en el arranque o al resincronizar. |
| `F0 7D 46 53 00 F7` | Android → FM-1 | Renueva el emisor. La app usa un intervalo objetivo de 900 ms. |
| `F0 7D 46 53 02 F7` | Android → FM-1 según la fuente | Consulta solo el nombre del sonido. La app se limita a leer la respuesta `FS 02 + texto` que Atlatl envía tras los saludos normales. |
| `F0 7D 46 4C 01 F7` | Android → FM-1 | INFO del editor. Se envía una vez por apertura, espaciada de los saludos. Devuelve versión y capacidades del editor. |
| Comando `0x11` en la trama Felucca `00 59` y cuerpo vacío | Android → FM-1 | Consulta de solo lectura del identificador de paquete. Se envía una vez. Una respuesta `ota-…` bloquea el envío. |

V1 encapsula posiciones de fila y columna en dos bytes de siete bits. Cada RLE contiene un conteo de 1 a 127 y un color RGB565 en campos de 7, 7 y 2 bits. Atlatl limita los mensajes a 70 bytes. La respuesta INFO que empieza por `FELUCCA` no distingue las variantes. El prefijo documentado `FM-1_742…` del paquete y la versión recibida permiten mostrar SKIFF Atlatl. El perfil se ofrece también en selección manual. Un perfil importado o un par VID/PID compartido no autoriza transmitir.

La detección automática de un descriptor USB `Felucca` con `1209:0001` selecciona la política V1. Requiere un puerto de envío abierto y que el usuario active la solicitud. La selección explícita Big Screen V1 aplica la misma política. HYP-1 conserva la escucha pasiva. Big Screen V1 carece de un panel remoto documentado. Las órdenes de panel previas siguen limitadas a una sesión fm1-base v2 o posterior con permiso expreso.

## Congelación y recuperación

El código de Atlatl apaga el emisor 3.000 ms después del último saludo procesado. Su integración reserva 4 KiB para filas en `mirror.c` y utiliza una cola USB compartida de 64 palabras en `usb.c`. El envío no bloqueante puede devolver ocupado. Durante una reconstrucción completa, el envío puede esperar espacio y abortar después de 200 ms. El receptor de SysEx deja una trama entrante pendiente en el firmware. La congestión, la pérdida o el retraso de renovaciones son hipótesis comprobables. El visor de referencia ya envía saludos periódicos. Las pruebas físicas disponibles no establecen cuál de esos mecanismos provocó la congelación observada. [Integración de filas](https://github.com/LIkst4/skiff-atlatl/blob/2d4e390bc024640d7c8ca0621fc475363be7dd6e/firmware/src/mirror.c#L30) · [Salida USB](https://github.com/LIkst4/skiff-atlatl/blob/2d4e390bc024640d7c8ca0621fc475363be7dd6e/firmware/src/usb.c#L1039) · [Renovación del visor externo](https://github.com/pfkellogg/fm1-mirror-spyglass-android/blob/a5ee5336a192d3ff1c2b71b83692e07601b935b6/app/src/main/java/com/pfkellogg/fm1mirror/MainActivity.java#L624).

El receptor conserva el último LCD durante una interrupción. Un V1 sin respuesta periódica puede quedarse estático indefinidamente. Atlatl responde con el nombre del sonido a los saludos y permite distinguir actividad de sesión de cambios del LCD. Tras unos 4,6 s sin esa señal o ante cobertura parcial sin progreso, pérdida local o petición manual, se permite un repintado espaciado. El límite automático es tres repintados, con intervalos crecientes de unos 4, 8 y 16 s, redondeados al siguiente saludo. El restablecimiento manual tiene un intervalo mínimo entre mensajes. Una desconexión física cierra la sesión y abre otra con reintentos espaciados. El cierre o pausa dejan de renovar y permiten la caducidad del emisor.

La recepción usa una cola acotada de 64 KiB y 512 fragmentos, con un drenaje por lotes en el hilo MIDI. El ensamblador conserva SysEx ante bytes de tiempo real, incluido F8. Si se desborda la entrada, descarta la cola y el fragmento parcial y cuenta los bytes perdidos. Aplica solo bloques validados y publica imágenes agrupadas fuera del callback MIDI. El panel muestra bloques, publicaciones y dibujos por segundo. V1 carece de secuencia y marca de fotograma, así que esos contadores no estiman el FPS del LCD físico. Se contabiliza «pantalla completa» cuando se conoce la cobertura de 240 × 240.

El diagnóstico registra RX/TX de la API, última trama SysEx, solicitudes, respuestas, actividad, errores y reconexiones. Cada TX aceptado por la API se registra con hora UTC. La aceptación de la API no acredita que lo haya recibido el FM-1. Las respuestas del firmware y los píxeles son la evidencia de extremo a extremo. Clock se cuenta íntegramente y se registra por intervalos de cinco segundos. Los demás mensajes de gran volumen se muestrean. Exportar o compartir incluye el resumen y la ventana completa del registro persistente, hasta dos archivos de aproximadamente 1 MiB cada uno, con independencia del filtro visible.

## Validación de software y hardware

Las pruebas de protocolo usan vectores sintéticos derivados de la documentación, con fragmentación, concatenación, F8, paquetes corruptos, pantalla estática, pérdida de señal, resincronización y saturación. El ensayo acelerado modela 600 s. El ensayo de reloj real se activa con `FM1_WALL_CLOCK=1 ./gradlew :core:test --tests '*AtlatlSessionTest.tenMinuteWallClockRun' --rerun`. El workflow lo ejecuta en un trabajo separado, con XML descargable. Ningún ensayo sintético mide el cable, la cadencia del LCD, la CPU del FM-1 o la estabilidad física. [Plan físico](../PHYSICAL-TEST-PLAN.md).

## Verificación de publicación de la compilación 017

La ejecución PR 46100042294 del commit ea111c5 pasó las pruebas unitarias y Lint, pero omitió explícitamente el empaquetado APK, las pruebas visuales y el ensayo de duración por las condiciones del workflow. No equivale a una entrega instalable. Se requiere una ejecución push o workflow_dispatch con artefacto APK, checksum, firma y resultados de duración y emuladores. La validación física sigue pendiente.
