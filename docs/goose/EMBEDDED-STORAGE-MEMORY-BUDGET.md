# Presupuesto de memoria de almacenamiento embebido

El presupuesto se mantiene como medición de linker, no como capacidad SRAM física aprobada.

* APP contiene text, rodata y `.data.app`, incluyendo el estado persistente de `mb_core`, renderer, teclas, reloj y adapter USB.
* LOW contiene BSS y queda separado de los topes de stack definidos por `multiboot_ram.ld`.
* FAT32 conserva un sector de 512 bytes en `struct fat32`; no se añade framebuffer completo.
* MSC/BOT usa CBW/CSW y buffers acotados; USB0 conserva sus buffers DMA existentes.
* SHA/FM1CAT usan los buffers existentes del core; el renderer usa una línea RGB565 de 480 bytes.
* El catálogo máximo continúa limitado a 64 entradas y 24 KiB.

El informe CI del target full midió `.image=22108` bytes, APP1 `22116` bytes y BSS `25896` bytes (`0x6528`). La imagen ocupa APP desde `0x01C30000` hasta `0x01C3565c`; BSS termina en `0x01C1AD28`, antes del límite linker `0x01C1E000`. El stack dinámico máximo no está demostrado por análisis automático de la toolchain; por eso los objetos grandes permanecen en estado persistente controlado y no en variables automáticas.

Si el target full excede APP/LOW, no se moverán direcciones arbitrariamente: se conservará DEMO/USB como arquitecturas separadas y se documentará la reducción o la necesidad de confirmar SRAM.
