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
| Registro | Se exporta por el selector de Android. Incluye hora, VID/PID, códigos de operación, rangos de RAM, tamaños y resultados. Excluye claves, números de serie, datos de Flash, rutas de archivos y contenido de APP1. La APK carece de permiso de red. |

## Pendientes para un futuro modo físico

Quedan por validar en una unidad autorizada la identidad de placa frente a otros AC791N, la transición MIDI a ROM y su nuevo permiso, la reclamación de la interfaz y el comportamiento de la ROM bajo desconexión. Haría falta verificar de forma independiente la integridad de la Flash antes y después, el retorno a DARK-1, el encendido del diagnóstico en LCD y el comportamiento eléctrico al cambiar el rol USB. La app mantiene el modo físico bloqueado mientras estas comprobaciones están pendientes.

La huella fijada identifica el artefacto aprobado para evaluación en esta fase. El ensayo de hardware y la revisión de seguridad son requisitos distintos.
