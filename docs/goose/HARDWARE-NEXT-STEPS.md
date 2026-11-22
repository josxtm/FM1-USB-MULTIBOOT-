# Próximos pasos de hardware — puertas de seguridad

1. **Revisión documental**: confirmar esquema/CC, rol USB-C, VBUS, límites de corriente, ESD, backfeed y alimentación de hub; sin conectar el FM-1.
2. **Medición pasiva autorizada**: únicamente después de revisar el esquema, medir CC/VBUS sin inyectar alimentación ni activar Host.
3. **Prueba mínima separada**: si se autoriza, usar hub/lector autoalimentado y observar solo enumeración; detener ante brownout, calentamiento o desconexión. No enviar SCSI de escritura.
4. Implementar después el HAL MSC real y repetir pruebas de descriptor, BOT y FAT32; registrar cada resultado físico por separado de simulación.
5. Pantalla ST7789, botones/encoders, watchdog, ABI y memoria necesitan gates independientes.

No se ha realizado ninguno de los pasos 2–5. No hay afirmación de microSD real, Cover Flow físico, RAM execution, Flash map completo ni instalación recuperable.
