# Procedimiento futuro para primera prueba RAM

**Estado: no autorizado y no ejecutado.** Este documento no es una instrucción para conectar ahora el FM-1.

## Precondiciones

1. Confirmar identidad del dispositivo y conservar la región protegida, DARK-1 y m-bootloader.
2. Obtener del propietario/documentación el caller real: stage-2 o run-ram, argumento, registros, reloj, vector, watchdog, stacks, BSS/.data y retorno.
3. Confirmar mapa SRAM y método de carga; revisar ELF, BIN, APP1, SHA, CRC, map y símbolos.
4. Tener host de recuperación probado por separado, procedimiento de modo descarga y medios para restaurar DARK-1 sin borrar región protegida.
5. Autorizar explícitamente una prueba RAM-only, sin Flash erase/write, sin SCSI write, sin USB Host del FM-1, sin VBUS/CC y sin modificación del bootloader.

## Secuencia propuesta

Primero transferir únicamente la imagen mínima mediante el canal oficialmente confirmado, con límites de tamaño, dirección, timeout y cancelación. Registrar identidad, bloques, ACKs y estado de recuperación. No emitir el salto hasta que el contrato lo autorice. Si se autoriza el salto, observar sólo el marcador/heartbeat definido y abortar ante pérdida de comunicación, watchdog, corriente o identidad inesperada.

## Interpretación

Marcador de entrada ausente: entrada no alcanzada o carga incorrecta. Heartbeat parcial: fallo durante la rutina. Estado final: ejecución de la rutina mínima, no prueba de Cover Flow. Timeout/pérdida de enlace: resultado inconcluso, nunca éxito. No se debe inferir escritura Flash ni éxito de firmware.

Después de una prueba se vuelve al modo descarga y se verifica identidad. Cover Flow, USB MSC y cualquier firmware permanecen bloqueados hasta una revisión independiente.
