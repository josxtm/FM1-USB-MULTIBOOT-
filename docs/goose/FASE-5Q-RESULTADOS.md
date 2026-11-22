# FASE 5Q — integración USB MSC + FAT32 + Cover Flow

## Entrega

Se integró un adaptador explícito `embedded_usb_msc_storage` sobre las capas existentes. La arquitectura única es USB0/husb → storage/BOT → block device → FAT32 → `mb_storage` → FM1CAT/core → Cover Flow. No se duplicó USB, SCSI o FAT32.

El target `ram-app-pi32v2-full-image` enlaza realmente startup, aplicación, core, renderer, teclas, LCD, timer, runtime, adapter USB, FAT32, storage, MSC/BOT, husb y diagnóstico. Produce ELF, BIN, APP1, map, disassembly, reporte ELF y SHA.

## Modos

DEMO conserva DARK-1/MELODEE/TESSERA como nombres/datos de demostración sin binarios de terceros. USB se ofrece como backend explícito; `open()` sólo se invoca si un caller autorizado lo solicita. Enlazarlo no activa USB0. El host puede usar `mount()` con un block device emulado y recorre el mismo FAT32/mb_storage.

## Pruebas

`usb-storage-test` valida el adaptador común; `msc-fat32-test` valida BOT→FAT32→FM1CAT→core→renderer y fallos para 0/1/2/3/64. Se conservan sanitizers, pruebas de errores y Cover Flow. La ruta física de `usb_enumerate()` no se ejecutó.

## Estado

La imagen full es un candidato de software y su tamaño final será el reportado por el linker/CI. Si no cabe, no se cambian direcciones arbitrariamente. El mapa de memoria sigue siendo virtual hasta confirmar SRAM. El ABI run-ram/stage-2 sigue separado y `MB_LAUNCH_BLOCKED` no cambia.

Pendiente antes de Cover Flow físico: ABI del bootloader instalado, SRAM/stack, reloj, USB-C eléctrico, enumeración MSC, LCD/teclas y recuperación. Pendiente para firmwares reales: formato, ABI, relocación, vectores, dependencias, licencia y autorización independiente.
