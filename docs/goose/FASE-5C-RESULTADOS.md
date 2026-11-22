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

## Validación CI

- Núcleo host, render SVG, objeto pi32v2, ELF enlazado y mapa: ✅ [run 44971600771](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44971600771)
- Checks de PR del núcleo, diagnóstico y Cover Flow: ✅ [core 44971604765](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44971604765) · [diagnóstico 44971604926](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44971604926) · [Cover Flow 44971605029](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44971605029)
- Android comp15 conservado: ✅ [run 44971604692](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44971604692)

La primera ejecución del ELF enlazado expuso referencias a `memcpy`/`memset`; se corrigió enlazando el runtime freestanding existente, sin usar libc host. La segunda ejecución fue satisfactoria.

## FASE 5C.2 — cierre técnico reproducible

Se añadieron `render-matrix`, `memory-report`, ASan/UBSan, validación XML y trazas end-to-end. La medición host reproducible es `mb_entry=140`, `mb_catalog=8964`, `mb_core=9096`, catálogo máximo 24.576 bytes, buffer de verificación 512 bytes y framebuffer RGB565 0. El renderer aplica escape XML defensivo y la matriz verifica 1/2/3/64 entradas. El parser rechaza bytes no ASCII en campos, EOF ambiguo, lecturas sin progreso, truncamiento, exceso, traversal y nombres fuera de `ID.BIN`/`ID.FWS`.

`PI32V2-LINK-REPORT.md`, `MEMORY-BUDGET.md`, `FM1CAT-CONFORMANCE-EXTENDED.md` y `COVERFLOW-EMBEDDED-VALIDATION.md` registran las mediciones, la matriz y las limitaciones. El presupuesto de 64 KiB del linker es virtual; no demuestra SRAM libre, stack disponible ni ABI del bootloader. FAT32/USB continúa siendo un límite simulado: el core se integra mediante HAL y las pruebas USB/FAT32 existentes permanecen host-only.

## Seguridad

No se enlazan comandos `0x25`/`0x30`, Flash, USB Host, GPIO, SPI, saltos arbitrarios ni carga RAM. `mb_prepare_launch()` sigue bloqueado. DARK-1, m-bootloader y `[0x00000,0x04000)` permanecen intactos. Ningún ELF se convierte en APP1 instalable ni se ejecuta físicamente.
