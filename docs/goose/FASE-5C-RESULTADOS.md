# FASE 5C — ELF pi32v2 enlazado y render embebido simulado

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Alcance:** simulación y validación de toolchain; ningún backend físico.

## Entregables

- `src/embedded_multiboot_sim.c`: entry point de simulación sin almacenamiento ni HAL físico.
- `src/embedded_multiboot.ld`: linker script aislado con origen `0x01C30000` y presupuesto de 64 KiB.
- `build/embedded_multiboot_pi32v2.o`: objeto pi32v2 separado.
- `build/embedded_multiboot_pi32v2.elf`: enlace independiente del núcleo y entry simulado.
- `build/embedded_multiboot_pi32v2.map`: mapa de secciones del enlace aislado.
- `tests/embedded_render_demo.c`: backend host que produce `build/embedded-coverflow-demo.svg` de 240×240.

## Render

El demo host carga un catálogo FM1CAT v1 de tres entradas mediante la HAL, cambia la selección, entra en `INSPECT` y ejecuta los callbacks del renderizador. El SVG contiene tarjeta central, laterales, estado y aviso `PLAY INSPECT ONLY`. El resultado es simulado y no representa una prueba ST7789 física.

## Toolchain y límites

El objeto pi32v2 se compila con `scripts/get-toolchain.sh` y flags freestanding. El ELF se enlaza con un script aislado, sin startup del FM-1 ni controladores LCD/USB. El mapa y los tamaños se publican como artefactos CI. Este enlace demuestra compatibilidad de compilación/enlace del prototipo, no un ABI de bootloader ni ejecución AC791N.

## Seguridad

No se enlazan comandos `0x25`/`0x30`, Flash, USB Host, GPIO, SPI, saltos arbitrarios ni carga RAM. `mb_prepare_launch()` sigue bloqueado. DARK-1, m-bootloader y `[0x00000,0x04000)` permanecen intactos.
