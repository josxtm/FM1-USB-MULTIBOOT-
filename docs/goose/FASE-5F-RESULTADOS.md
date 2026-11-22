# FASE 5F — resultados

## Implementación

Se consolidó MSC/BOT sin crear un tercer controlador: `msc_bot.c` es la autoridad común de CBW/CSW, tags, residue, LUN, CDB read-only y construcción de CBW; `storage.c` mantiene enumeración, EP0, Bulk y recuperación dependiente de `husb`; `fat32.c` no conoce USB. Se añadió retry único de STALL en data IN y se conserva BOT reset/CLEAR_FEATURE.

La prueba `msc_fat32_integration` ejercita BOT simulado → bloques 512 B → FAT32 → `mb_fat32_storage` → FM1CAT/core → renderer y launch bloqueado para 0/1/2/3/64. La prueba USB existente cubre enumeración, descriptors, TUR, REQUEST SENSE, READ CAPACITY, READ(10), timeout, CSW, desconexión y FAT32.

## Bugs corregidos

* `storage.c` generaba y validaba framing en paralelo; ahora usa la política común.
* No se validaba LUN cero ni flags CBW reservados.
* Residue podía aceptarse con longitud recibida incoherente.
* Status CSW 2 se confundía con framing inválido.
* STALL de data IN se limpiaba sin reintento.

## Estado

Host y sanitizers pasan. El target pi32v2 incluye `msc_bot`, pero la toolchain no está instalada localmente; CI es la fuente reproducible del ELF/map. No se activó USB0 físico, VBUS/CC, microSD, Flash, RAM, APP1, DARK-1 ni m-bootloader. La cadena completa sobre controlador físico sigue pendiente.
