# FASE 5D — integración FAT32 y viabilidad multiboot

## Entrega

Se integró el core C99 existente con el parser FAT32 existente mediante `src/embedded_fat32_storage.c`. No se duplicó el parser ni se enlazó USB físico. `scripts/generate_fat32_image.py` produce una imagen FAT32 reproducible; `tests/fat32_multiboot_integration.c` ejecuta el núcleo real sobre sus sectores.

`make fat32-integration` pasó para catálogos de 0, 1, 2, 3 y 64 entradas. La prueba lee `FM1/INDEX.CSV`, recorre `FM1/FW`, procesa archivos fragmentados, calcula SHA-256 de bytes reales, navega, inspecciona, verifica y confirma `MB_LAUNCH_BLOCKED`. Desconexión y timeout simulados terminan en estados seguros (`NO_STORAGE` y `ERROR`).

Medidas actualizadas: `sizeof(mb_entry)=140`, `mb_catalog=8964`, `mb_core=9096`, `sizeof(fat32)=560`, `sizeof(mb_fat32_storage)=8`, catálogo 24.576 bytes, buffer SHA 512 y framebuffer 0. Esto no incluye stack físico ni demuestra SRAM disponible.

## Arquitectura

El candidato principal de investigación es un cargador residente en el área de aplicación que sustituya DARK-1, condicionado a mapa Flash, ABI, SRAM, vectores y recuperación. La alternativa es híbrida Flash+RAM+medio externo. RAM pura y XIP siguen sin evidencia física suficiente. El estudio no autoriza escritura, instalación ni ejecución.

## Evidencia y bloqueos

- **Físicamente verificado:** identidad MIDI `0x12`, bootloader reportado, capacidad/JEDEC reportados.
- **Verificado por simulación:** FAT32, directorios, fragmentación, catálogo, SHA, estados y fallos de bloques.
- **Verificado por análisis estático:** separación HAL, validación de rutas, límites FAT32, ELF aislado y bloqueo de launch.
- **Hipótesis:** origen linker, XIP, SRAM libre, cargador residente y sustitución de DARK-1.
- **Pendiente:** USB Host real, microSD, VBUS/CC, mapa Flash, ABI, recuperación y cualquier arranque.

No se realizó ninguna operación física destructiva. Los documentos `FAT32-INTEGRATION.md`, `FM1CAT-FINAL-CONFORMANCE.md`, `MULTIBOOT-ARCHITECTURE-FEASIBILITY.md`, `FLASH-RESIDENT-MULTIBOOT-STUDY.md`, `USB-C-HOST-REQUIREMENTS.md` y `RECOVERY-REQUIREMENTS.md` contienen los límites y gates.
