# FASE 5O — validación de carga RAM

## Imagen Cover Flow

El linker experimental conserva APP `0x01C30000`, longitud `0x4FD78`, LOW `0x01C16800..0x01C1E000` para BSS y stacks en `0x01C1F800`/`0x01C20000`. `.image` contiene text, rodata y `.data.app`; el BSS es `NOLOAD`. Esta decisión hace que el estado persistente de ~9 KiB viaje en la imagen inicializada y evita solaparlo con BSS/stack.

`validate_ram_image.py` comprueba ELF32 little-endian, máquina 241, entry, PT_LOAD, correspondencia plana, BSS, exclusión de región retenida, trailer APP1, CRC, tabla de símbolos y símbolos prohibidos. Es un verificador estructural: nunca ejecuta ni autoriza la imagen.

La medición previa de CI para la app completa fue flat 15368 bytes, APP1 15376, BSS 25140 y estáticos 40508 bytes. Es una medición de link virtual, no evidencia de SRAM física.

## Candidato mínimo

`src/ram_minimal.c` y `tests/ram_minimal_test.c` están separados de Cover Flow y no enlazan LCD, USB, FAT32, timer, Flash ni política de lanzamiento. Escriben un marcador inequívoco sólo cuando se invoque su función; la prueba host sólo inspecciona símbolos/estado inicial y no llama la rutina infinita. `make ram-minimal-test` y `make ram-minimal-pi32v2-object` son gates reproducibles.

## Límites

Todavía falta validar con la toolchain del bootloader instalado el método de carga (offset, alineación, BSS clearing, `.data` copy, entry y vector). El tamaño enlazado no prueba que la región APP anunciada por el dispositivo sea realmente utilizable. Ningún BIN/APP1 fue enviado o ejecutado.
