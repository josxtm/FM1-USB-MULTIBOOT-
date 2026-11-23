# FM1 MULTIBOOT OS — Fase USB-C 2

## Alcance de este PR

**Entrega de ingeniería, no prueba física realizada.** Se ha desarrollado una ruta Android de laboratorio con **sólo** INQUIRY, FC40, FB06 (escritura a SRAM) y FB08 (salto único). Reutiliza el controlador USB existente, el explorador FM1CAT v2 del PR #21 y la aplicación Android RAM Loader. El gestor Android FM1 Manager To USB permanece sin cambios.

El artefacto auditado de [la CI USB-C 1](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/45253817835) es:

| Campo | Valor |
|---|---|
| Nombre | `FM1_USB_C_SD_READER_READONLY.bin` |
| SHA-256 | `cb04c2cef8ef9641603c68b2942c8d094fa87a9b5debec72d24cf7115bfd2025` |
| Longitud | 20.328 bytes |
| Entrada | `0x01C30000` |
| Formato | APP1, placa FM-1, formato 1, CRC16-XMODEM |
| Flash | No incluye ejecución de borrado/programación Flash |
| Estado físico | **No validado** |
| USB Host en ese APP1 | **Deshabilitado por falta de cualificación CC/VBUS** |

**Diferencia esencial:** validar una imagen y haber programado un transporte ROM de SRAM no prueba que ese mismo código pueda leer el lector microSD físico. La presencia de alternativas de adaptador no acredita una tensión o un rol USB-C adecuados. Ni la lectura de VID/PID ni INQUIRY prueban el estado real de VBUS.

## Cambios Android

- La variante separada **FM1 RAM Loader comp16** incorpora `UsbcRamTransport` y conserva la función antigua de simulación; **FM1 Manager To USB no es un cargador ROM**.
- La imagen admitida está fijada por SHA-256; se comprueban también tamaño, CRC, modelo y formato. No hay selector de direcciones, lista dinámica de comandos ni consola de SCSI arbitraria.
- Requisitos visibles de laboratorio, inicialmente todos desmarcados: entrada en ROM fría confirmada por observación independiente; copia completa de Flash comprobada y recuperable; recuperación probada; conexión externa CC/VBUS y protección cualificadas; autorización explícita de un único intento. El reclamo exclusivo de USB requiere su propia autorización adicional y ausencia de volúmenes extraíbles activos observables por Android.
- `4C4A:8057`, interfaz MSC `08/06/50`, un único dispositivo y un par Bulk IN/OUT no ambiguo. Permiso Android vigente y comprobación de que el dispositivo no fue reenumerado, antes y después de las transferencias.
- Una única sesión: INQUIRY válida; una negativa FC40 **exacta** (CSW status 1, residuo 16, sin datos); bloques FB06 sólo sobre `[0x01C30000, 0x01C34F68)` con tamaño y CRC por bloque; salto FB08 solamente después de haber completado la imagen exacta.
- Cualquier MFLR, respuesta ambigua, error de transferencia, desconexión o pérdida de permisos termina la sesión. Nunca reintentar FB08. Una falta de acuse no prueba que no haya arrancado.
- Los comandos FB00, FB01, FB04, FD05, FC09, actualizadores y acceso directo a Flash no se utilizan en este transporte.

La confirmación humana en la interfaz **no equivale a una medición independiente**. Corresponde al operador comprobar realmente los prerrequisitos; la app no puede certificar la instalación ni recuperar una unidad ante una anomalía física.

## Procedimiento para una futura prueba física autorizada

**No comenzar el ensayo si falta cualquier evidencia.** Este procedimiento no constituye autorización para alterar el firmware musical.

1. **Línea de base:** verificar que DARK-1 arranca normalmente, que las teclas y el audio funcionan, y que el m-bootloader muestra `2.4-395E6CC`, `FLASH 856014 1024K`, `APP OK` y `UPDATE NONE`. Si hay errores o una actualización pendiente, detenerse.
2. **Copia y recuperación:** conservar fuera del sintetizador y fuera de GitHub una copia de Flash íntegra, comprobada y atribuible a esta unidad, con recuperación ensayada por separado. Los hashes del respaldo, claves o serie del dispositivo no se registran en repos públicos.
3. **Conexión:** comprobar por procedimientos externos cualificados los roles USB-C/CC, 5 V VBUS bajo carga, corriente, protección contra retorno y alimentación del lector específico. No inyectar 5 V ni improvisar cableados.
4. **ROM fría:** desde apagado, mantener PLAY para entrar en recuperación; tras comprobar el estado, seleccionar `DOWNLOAD MODE` mediante el menú. No seleccionar funciones de actualización, instalación MIDI o Flash. Registrar la transición real desde el hardware. `WL82 / UBOOT1.00` por sí sola no prueba ROM limpia.
5. **Android:** conectar la tablet a ese FM1, abrir **FM1 RAM Loader comp16**, obtener permiso USB, usar `Conectar` y confirmar identidad ROM. Debe haber un solo dispositivo ROM; si Android no permite reclamar el puerto, parar salvo que la política de uso exclusivo se haya comprobado independientemente.
6. **Candidato:** extraer el APP1 exacto del artefacto GitHub Actions, cargarlo desde el selector de archivos y pulsar `Validar archivo`. Confirmar `Diagnóstico USB-C2 exacto, CRC/SHA válidos`. No elegir un firmware `.FWS`, `.UFW`, un `SLOT` musical ni el APP1 de otra fase.
7. **Autorización por intento:** tras completar las verificaciones independientes, registrar su cumplimiento en los controles de laboratorio, autorizar una única carga SRAM y, sólo si es imprescindible y seguro, el reclamo exclusivo. Pulsar **una vez** `Ejecutar prueba USB-C2 en RAM`.
8. **Interpretación:** `JUMP_ACKNOWLEDGED` confirma solamente el acuse ROM. `JUMP_UNCONFIRMED` implica **no repetir**; observar la pantalla del FM1. Para este APP1 el código de entrada actual mantiene USB Host deshabilitado (se espera `USB POWER UNVERIFIED` o el error equivalente), por lo que no debe afirmarse lectura de microSD.
9. **Salida:** REC si el diagnóstico responde, o parada mediante interruptor según [plan de recuperación 6B](FASE-6B-RECOVERY.md). Desconectar datos y realizar un apagado real. No ejecutar un segundo APP1, OTA, restauración automática ni comandos de prueba de Flash.
10. **Verificación posterior:** arrancar DARK-1, comprobar LCD, controles y audio, volver a entrar al m-bootloader para verificar identificación y `UPDATE NONE`, y regresar al arranque normal. Cualquier anomalía bloquea nuevas pruebas hasta investigación.

**Condición para pasar a lectura física:** además de un APP1 iniciado realmente, hace falta comprobar eléctricamente el rol host del FM1 y habilitar el controlador sólo mediante un procedimiento experimental expresamente revisado. El APP1 actual **no** habilita USB Host con una casilla Android; el canal cambia de dispositivo a Host cuando el Android se desconecta. La arquitectura futura debe resolver también el arranque autónomo, ya que `SD CARD` en el bootloader 2.4 usa SPI1 y no el puerto USB-C.

## Evidencias y pruebas

El código de transporte dispone de pruebas herméticas: negativa FC40, MFLR residente, respuestas ambiguas, corte de transferencia, salto sin acuse, sesión única, SHA256/CRC y garantía de lista positiva de opcodes. Las pruebas utilizan un APP1 sintético dentro del simulador y nunca acceden a dispositivos reales.

En GitHub Actions se ejecuta `assembleLoaderDebug`, `testLoaderDebugUnitTest` y `lintLoaderDebug`. La CI embebida compila y comprueba el APP1 experimental pi32v2, FM1CAT v1/v2 y el núcleo USB. A ninguna prueba CI se le atribuye evidencia de funcionamiento físico.

| Categoría | Estado |
|---|---|
| Implementación Android del transporte exclusivo SRAM | Implementada en esta rama |
| Compilación y pruebas de la variante comp16 | Pendientes hasta que termine la CI de este PR |
| Imagen APP1 de la etapa anterior | Recuperada y SHA/CRC comprobados |
| Carga de RAM en el FM1 físico | NO VERIFICADO |
| Detección física del lector USB-C | NO VERIFICADO |
| Montaje real de la microSD | NO VERIFICADO |
| Persistencia del selector entre firmwares | NO IMPLEMENTADA |

Esta fase no contiene instrucciones para flashear ni modificar m-bootloader, DARK-1 o zonas de calibración.
