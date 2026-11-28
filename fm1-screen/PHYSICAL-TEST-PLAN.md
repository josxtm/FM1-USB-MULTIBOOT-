# Validación física en español

**Preparación**. Instala la APK del artefacto de Actions. Confirma `FM1 SCREEN v1.0 · comp. 015` en el panel de ajustes. Si la compilación instalada lleva otro certificado, exporta temas y perfiles antes de desinstalarla, instala la nueva e importa esos archivos. Usa un cable USB-C de datos con la tablet como host. Arranca el instrumento de forma normal con su firmware instalado. Desactiva DEMO y deja activado **Solo visualización**.

1. Conecta el FM-1 y concede permisos si Android los solicita. Abre **Conexión**, selecciona su puerto y anota el nombre que Android enumera. Escribe el firmware y la versión que indica el instrumento. Un perfil sugerido por el USB no certifica la versión.
2. Confirma que el audio sale del FM-1 por su salida habitual. Haz una prueba tocando pads físicos con el volumen de la tablet silenciado. Conserva el audio del instrumento durante las pruebas siguientes.
3. Para una edición con emisor, activa **Solicitar duplicación** con **Auto** o el receptor indicado en la tabla. Navega en el FM-1 durante 30 s. Comprueba imagen, colores, proporción, orientación y actualización de páginas.
4. Desliza entre FULL CONTROL, SCREEN + CONTROL y BIG SCREEN. Comprueba que la imagen continúa. Haz doble toque sobre el LCD en BIG SCREEN para entrar y salir de inmersivo. Prueba zoom y los botones de navegación.
5. Si la sesión anuncia fm1-base v2 con panel, pulsa y libera un pad físico y varias teclas a la vez. Verifica iluminación y activación de botones, octava y giro de siete encoders. Gira MASTER y comprueba su posición si el firmware la informa. Anota qué botones y parámetros no publican datos. La disponibilidad depende del firmware.
6. Si los pasos anteriores funcionan, desactiva **Solo visualización**. Prueba pad táctil corto, largo y un acorde. Comprueba su liberación y que el audio sigue saliendo del FM-1. Prueba un botón permitido y gira un encoder. No uses SAVE. Vuelve a **Solo visualización**.
7. Desconecta y reconecta el USB. Verifica recuperación de la imagen, panel sin notas sostenidas y control remoto desautorizado. Si alguna nota queda sostenida tras quitar el cable, libérala desde el FM-1 y anota el gesto.
8. Mantén diez minutos de sesión alternando actividad y reposo. Anota errores de imagen, latencia visible, sonido y reconexiones. Para latencia extremo a extremo, filma el LCD físico y la tablet simultáneamente a una frecuencia conocida y cuenta los cuadros entre cambios equivalentes.
9. En **Diagnóstico**, exporta los registros a un archivo de texto con el selector Android. Incluye uno antes del sondeo, otro después y otro al final si hubo incidencias. Adjunta versión, tiempo transcurrido, protocolo, tres capturas y resultado del audio. Opcionalmente exporta un PNG o un MP4 del LCD desde **Vista**.

| Edición ya instalada | Configuración y resultado que se debe comprobar |
|---|---|
| FRASIL 3.4.1 | USB, fm1-base, LCD y estados v2 si el Hello lo anuncia. Bluetooth está deshabilitado en su configuración de referencia. |
| SKIFF + Big Screen 2.1.1 | USB, Big Screen V1. Espera franjas que completan el LCD. Mantén el control táctil deshabilitado. |
| SKIFF estándar 2.7.2 | Identificar puerto y MIDI. El Cast publicado transporta eventos. Si falta el LCD, exporta el registro y la variante indicada. |
| DARK-1 1.0.7 | Conexión, identificación y audio. Aún se desconoce un emisor de LCD. Exporta el resultado de escucha. |
| POLLY 0.6.4 o LOOM-8 0.13/0.18.2 | USB, fm1-base. Comprueba primero el LCD y después el protocolo de panel. LOOM-8 0.19 estándar tiene mirroring desactivado. |
| Otros con Big Screen | Consulta la edición exacta en la matriz. V1 y V2 requieren que esa compilación incluya el emisor. |

Para BLE MIDI, usa un firmware que lo anuncie. Desconecta USB MIDI, selecciona **Bluetooth** y verifica detección, mensajes, recuperación y tasa de imagen de forma independiente. La recepción de notas no demuestra soporte LCD por BLE. [Matriz de firmwares](FIRMWARE-COMPATIBILITY.md).
