# Fase 2 · Informe de seguridad del cargador Android

**Resultado de esta revisión.** La APK permite identificación ROM de solo lectura y simulación. El botón **Cargar en RAM** está deshabilitado en el código. La evidencia de simulación y compilación se limita a software.

## Controles aplicados

| Superficie | Control |
|---|---|
| USB real | `InquiryWire` acepta un único CBW canónico para SCSI INQUIRY. Valida cada salida antes de transferirla. El comando y su estado se validan por separado. El canal no admite datos OUT, órdenes de escritura, carga en RAM ni salto. |
| Transporte simulado | `RamSimulation` recibe concretamente `SimulatedRom`. Esa clase escribe en un buffer del proceso y solo acepta INQUIRY, carga de RAM y salto simulado. El simulador rechaza otros opcodes y el salto exige haber recibido la imagen aprobada completa. |
| Archivo | Importación acotada mediante el selector de documentos. Solo se aprueba el SHA‑256 `810bb3673ef2fc75964724e07d42bb751d1e3e5e49056cdaccad40c3764ea246` de la Fase 1 con APP1 v1, placa 1, longitud par y CRC16 válido. Una imagen modificada con CRC reparado sigue rechazada. |
| Direcciones | Rango `[0x01C30000, 0x01C7FD80)`, entrada fija y bloques de hasta 512 B. Los límites se comprueban con aritmética sin desbordamiento y el último bloque conserva su tamaño real. |
| Permiso y ciclo de vida | El dispositivo tiene que coincidir con la solicitud y con `UsbManager.hasPermission`. Cambios de dispositivo cancelan la sesión lógica. Las transferencias de consulta usan tiempo de espera y se cierra la interfaz al terminar. |
| Acceso exclusivo comp3 | Requiere el fallo anterior de la reclamación normal, confirmación para la misma conexión, perfil ROM estricto, permiso vigente y ausencia de volúmenes extraíbles activos visibles para la app. La consulta real sigue limitada a un único CBW de INQUIRY. |
| Registro | Se exporta por el selector de Android. Incluye hora, VID/PID, códigos de operación, rangos de RAM, tamaños y resultados. Excluye claves, números de serie, datos de Flash, rutas de archivos y contenido de APP1. La APK carece de permiso de red. |

## Pendientes para un futuro modo físico

### Comp4 · Cargadores residentes y retorno

La prueba física comunicada de la comp3 obtuvo `WL82 / UBOOT1.00`, CSW válido y liberación en Android 16 mediante acceso exclusivo. La firma puede ser reenviada por código residente. La comp4 añade al simulador el reconocimiento `FC40` de M-Flasher, con eco, firma `MFLR`, versión y dirección big-endian. Se ensayan ambas clases de resultado, una ROM limpia conocida por el simulador y cargadores residentes o de estado desconocido. Una negativa o un fallo no es evidencia de ROM limpia. La política corta el ensayo antes de `FB06` y `FB08` en todos los casos no resueltos, incluso si se reconoce una ranura MFLR sin solapamiento. [M-Flasher run_ram e identify](https://github.com/jvitkauskas/m-flasher/blob/8fbece7348d758ac284325eed455781675ac95ff/src/jl.rs)

El transporte de Android sigue rechazando `FC40`, `FB06` y `FB08`. El diagnóstico enlaza el código a `0x01C30000`, reserva BSS a partir de `0x01C16800` y usa pilas por debajo de `0x01C20000`. Para habilitar una carga real se necesita evidencia independiente del estado de arranque y de estas reservas, una copia completa de Flash exportada y un retorno a DARK-1 reproducible con verificación del contenido. La pérdida del acuse de salto queda sin confirmar y no se reintenta. El retorno previsto del diagnóstico de la Fase 1 deja USB pasivo y requiere apagar y encender. La comp4 no envía un comando de reinicio ni intenta verificar Flash mediante un cargador adicional.

El listado de volúmenes que Android entrega a la app no permite ver toda la actividad del kernel ni confirmar que la interfaz pertenece al FM‑1. `claimInterface(true)` puede interrumpir el acceso de Android al almacenamiento asociado. Si el intento falla después de desvincular el controlador o `releaseInterface()` devuelve `false`, la app no puede garantizar que el sistema lo vuelva a vincular. El descriptor USB válido y la confirmación explícita limitan el intento a la consulta. La prueba en la tablet y su ROM física sigue pendiente.

Quedan por validar en una unidad autorizada la identidad de placa frente a otros AC791N, la transición MIDI a ROM y su nuevo permiso, la reclamación de la interfaz y el comportamiento de la ROM bajo desconexión. Antes de una carga física también es necesario descartar que un cargador residente ocupe la ventana RAM. Haría falta verificar de forma independiente la integridad de la Flash antes y después, el retorno a DARK-1, el encendido del diagnóstico en LCD y el comportamiento eléctrico al cambiar el rol USB. La app mantiene el modo físico bloqueado mientras estas comprobaciones están pendientes.

La huella fijada identifica el artefacto aprobado para evaluación en esta fase. El ensayo de hardware y la revisión de seguridad son requisitos distintos.
