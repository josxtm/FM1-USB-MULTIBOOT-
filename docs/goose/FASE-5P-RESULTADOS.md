# FASE 5P — resultados

## Entregado

* El candidato mínimo pasó de objeto a imagen reproducible con startup, linker, ELF, BIN, APP1 experimental, map, disassembly, reporte de secciones, SHA-256 y validación estructural.
* `ram_minimal.c` ya no depende de un bucle infinito como única señal: registra entrada, ejecuta un heartbeat finito y deja un estado final estable. No inventa retorno para run-ram.
* Se añadió `ram_loader_dry_run.py`, que valida formato, CRC, SHA, bloques de 512 bytes, orden, destinos, ACK lógico, cancelación, timeout y reconstrucción en memoria.
* CI compila/valida la imagen mínima y ejecuta dry-runs normal y cancelado.
* Cover Flow, TMR0, APP1, stack, SRAM, recuperación y `MB_LAUNCH_BLOCKED` fueron revisados y documentados.

## Contrato

La imagen se etiqueta como candidata **run-ram experimental**. El stage-2 documentado usa un argumento MHz y retorno C; run-ram usa descriptor ROM y no ofrece retorno normal. La imagen no se considera compatible con ambos. Android puede simular el transporte, pero no puede demostrar qué caller usa el FM-1 2.4-395E6CC.

## Criterio de hardware

La imagen está compilable y estructuralmente validable, pero **no está lista para hardware**: faltan confirmación del ABI instalado, mapa SRAM, reloj heredado, vector, watchdog, stacks, método de carga y procedimiento de recuperación. No hubo conexión ni ejecución física.

## Seguridad

No se escribió Flash, no se sustituyó DARK-1, no se modificó m-bootloader, no se ejecutó RAM, no se activó USB Host y no se habilitó firmware launch. El estado sigue siendo `MB_LAUNCH_BLOCKED` y PR #8 sigue Draft.
