# FASE 5P — imagen RAM mínima

`src/ram_minimal.c` es ahora una imagen completa, no sólo un objeto. `src/ram_minimal_start.S` establece los stacks experimentales y llama al entry; `src/ram_minimal.ld` define APP/LOW, dos segmentos PT_LOAD, BSS y asserts de tamaño.

## Artefactos

`make TOOLCHAIN=toolchain ram-minimal-image` produce:

* `build/ram_minimal_pi32v2.elf`
* `build/ram_minimal.flat.bin`
* `build/ram_minimal.bin` (APP1 experimental)
* `build/ram_minimal.map`, `.dis`, `.elf-report.txt` y `.SHA256SUMS.txt`

El objetivo usa `validate.py`, que comprueba máquina 241, entry `0x01C30000`, segmentos, correspondencia flat/ELF, BSS fuera de stack, región retenida, trailer APP1, CRC y placa. El APP1 sólo es un artefacto de simulación: el contrato físico de carga/entrada no está aprobado.

La rutina deja un rastro de entrada, heartbeat finito y estado final. Como run-ram no documenta retorno seguro, termina en un estado estable sin retornar al caller desconocido. Los valores son observables sólo mediante un harness autorizado; no se ha leído SRAM física.
