# Fase 2 · Integración USB de FM1 RAM Loader

## Estado de la implementación

| Función | Evidencia |
|---|---|
| Detección MIDI y ROM | Política probada en JVM. La APK obtiene VID, PID e interfaces mediante Android USB Host API. |
| Permiso Android | Se comprueba la concesión del sistema, el dispositivo y la generación de la solicitud. Las reglas pasaron pruebas JVM. El diálogo y la reclamación de interfaz requieren prueba en tablet. |
| Firma USB de ROM | Se implementó una consulta SCSI INQUIRY de solo lectura para la interfaz específica `08/06/50`, con VID/PID `4C4A:8057`. El protocolo de consulta pasó con respuestas simuladas. La firma no distingue una ROM limpia de un cargador residente que reenvíe la consulta. |
| APP1 | Verifica formato 1, placa FM-1, CRC16-XMODEM, ventana RAM y SHA‑256 de la Fase 1. |
| Carga y salto | Implementados en simulación. El transporte Android solo admite INQUIRY. |
| Identificación de la Flash | Pendiente. La consulta identifica la familia ROM, y no distingue un FM‑1 de otros productos AC791N. El modelo de la Flash aportado por el usuario permanece como contexto. |
| Reenumeración y retorno a DARK‑1 | El monitor invalida la sesión ante retirada o cambio de dispositivo. La transición y el retorno tras una ejecución en RAM siguen pendientes en placa. |

## Corrección comp3 para Android 16

En la prueba de la comp2, la tablet detectó `1209:0001` en MIDI y `4C4A:8057` en ROM. Android mostró permiso otorgado. La aplicación agrupó varios fallos en «Interfaz USB ocupada o no disponible» antes de completar INQUIRY. Ese resultado por sí solo no identifica qué llamada falló ni demuestra que el controlador Mass Storage ocupara la interfaz.

| Paso | Evidencia en la consola de la comp3 |
|---|---|
| Enumeración | VID/PID, número de configuraciones, clases, números y ajustes alternativos de interfaz. Se busca una interfaz `08/06/50` con ajuste `0` y exactamente un Bulk IN y un Bulk OUT válidos. Se muestran los endpoints y sus tamaños máximos. |
| Permiso y apertura | Resultado del permiso y de `openDevice()`. Si devuelve `null`, se vuelve a comprobar la presencia y el permiso. `UsbManager` oculta la causa interna detrás de `null`. |
| Reclamación | Resultado booleano de `claimInterface(false)` y del intento exclusivo, cuando se haya autorizado. La API pública no expone `errno` ni quién ocupa una interfaz. |
| BOT | Endpoint, fase CBW, datos o CSW, bytes devueltos, tiempo transcurrido y límite aplicado. Un retorno negativo con el dispositivo presente se registra como fallo de transferencia sin atribuirlo a timeout o a un STALL. Se distinguen longitud, firma, etiqueta y estado/residuo CSW. |
| Cierre | Resultado de `releaseInterface()` si se obtuvo la interfaz y de `close()` incluso si la liberación falla. Un cambio de dispositivo o la cancelación invalida la sesión. Una llamada Bulk en curso termina con el límite de Android antes del cierre. |

La comp3 empieza con una reclamación ordinaria. Si Android la rechaza, la consulta con acceso exclusivo se ofrece solo para `4C4A:8057` con una configuración, una interfaz BOT y solo los dos endpoints Bulk. Exige confirmación para esa misma conexión y un permiso USB vigente. Evalúa los volúmenes extraíbles visibles a la app. Un volumen activo o cuyo estado no se pueda comprobar bloquea el intento. Si todavía falla la reclamación ordinaria, la app llama una vez a `claimInterface(true)`. En AOSP Android 16, esa opción intenta desvincular al controlador del kernel solo cuando la reclamación devuelve `EBUSY`. El sistema intenta vincularlo de nuevo al liberar con éxito, sin confirmación de que se haya restaurado el controlador. [Implementación AOSP Android 16](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/jni/android_hardware_UsbDeviceConnection.cpp)

Como alternativa sin desvincular el controlador, cierra otras aplicaciones USB, comprueba que no haya un volumen de ROM en uso y reconecta el cable para obtener una sesión nueva. Android ofrece permisos temporales por conexión. Abre de nuevo la ROM con **Conectar** y concede el permiso si se solicita. Para esta corrección se descartaron los reinicios de USB, los cambios de configuración o ajuste alternativo y el uso de controladores privados o acceso root. [API Android USB Host](https://developer.android.com/develop/connectivity/usb/host) · [Referencia de Android FM-1 Flasher](https://github.com/ekim-pl/android-fm1-flasher/blob/bfede50cfc9d7a848809980f5b300a465a04c364/app/src/main/java/pl/ekim/fm1_flasher/UsbBootConnection.kt)

## Referencias y compatibilidad

- **M-Flasher** `8fbece7`. Su ruta de escritorio presenta la ROM como BOT, divide la imagen en bloques de hasta 512 B, aplica la transformación CRC de ROM por bloque y usa `FB06` para RAM y `FB08` para el salto. Una desconexión en el salto deja su estado sin confirmar. Esta app reproduce la transferencia y el salto en el simulador, con el rango de la aplicación de la Fase 1 y sin reintentos automáticos. La referencia también comprueba si un cargador M-Flasher residente ocupa la ventana solicitada. Esa comprobación queda pendiente para cualquier implementación física. [Implementación de referencia](https://github.com/jvitkauskas/m-flasher/blob/8fbece7348d758ac284325eed455781675ac95ff/src/jl.rs)
- **Android FM-1 Flasher** `bfede50`. La app de referencia reclama la interfaz BOT, comprueba `WL82 / UBOOT1.00` y carga auxiliares de hash fijo. FM1 RAM Loader usa una consulta estándar aislada. Usa permiso USB explícito y reclama la interfaz sin forzar la desconexión de un controlador del sistema. Una interfaz ocupada se informa como error. [Referencia de conexión USB](https://github.com/ekim-pl/android-fm1-flasher/blob/bfede50cfc9d7a848809980f5b300a465a04c364/app/src/main/java/pl/ekim/fm1_flasher/UsbBootConnection.kt)
- **m-bootloader** `121cfbe`. La sección 11.8 define el tráiler APP1, placa 1, y la ventana de RAM. `0x01C7FD80` es el límite exclusivo. El binario analizado empieza en `0x01C30000`. El tráiler de 8 bytes viaja con la imagen. [Formato APP1](https://github.com/jvitkauskas/m-bootloader/blob/121cfbea1ef6a66df8c804ee45c1338bc16b0a53/docs/SPEC.md#118-ram-apps)
- **M-Unbricker** `bd5ea96`. El programa de referencia toma el control del LCD y USB y presupone el contexto de arranque desde ROM. Si una aplicación cambia el rol USB, el dispositivo anterior puede desaparecer desde la tablet. [Proyecto de referencia](https://github.com/jvitkauskas/m-unbricker/tree/bd5ea96ca2fb925e1647d233373bac7e3881a098)
- **DARK-1** `6c0cc34`. El firmware revisado distingue la entrada a UBOOT por estado en RAM retenida de la sesión OTA que prepara el cargador en Flash. Se verificó el flujo en el código fuente. Falta comprobar la revisión instalada y cómo Android entrega el permiso tras la reenumeración. [Entrada UBOOT](https://github.com/sparky480v/Dark-1/blob/6c0cc34677c4188575158c9ea5f53c95200be4a0/firmware/hal/fm1_sys.h#L119) · [OTA](https://github.com/sparky480v/Dark-1/blob/6c0cc34677c4188575158c9ea5f53c95200be4a0/firmware/src/ota.c)

## Pruebas y límites

El caso normal simula 18 comandos de carga y un salto. Verifica el eco de la ROM, la firma y etiqueta CSW, el residuo y la longitud. Los casos de error incluyen CBW ajenos a INQUIRY, respuesta corta, CSW erróneo o denegado, timeout, desconexión durante la escritura y desconexión después del salto. Un vector con CRC calculado de forma independiente comprueba la codificación y el orden de bytes; la prueba de fragmentación también verifica el bloque final de 252 B.

Java/Kotlin y GitHub Actions compilan la APK y ejecutan pruebas JVM y lint. Las pruebas de políticas no sustituyen la prueba del SDK USB real. Siguen pendientes el enlace Android–FM‑1, la identidad exclusiva de modelo, la ejecución desde ROM, el reinicio, el rol USB-C y VBUS.
