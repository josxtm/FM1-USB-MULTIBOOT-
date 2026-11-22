# FASE 5D.1 — cierre FAT32 → Cover Flow

La demostración integrada usa la misma imagen FAT32 para catálogo, navegación, verificación y render. `tests/fat32_multiboot_integration.c` monta los sectores mediante `fat32_mount()`, adapta el volumen a `mb_storage`, carga `FM1/INDEX.CSV`, navega con `mb_event()`, verifica `FM1/FW/F02.FWS` y llama al renderer del `mb_core` real.

`make fat32-integration` genera SVGs `build/fat32-coverflow-{0,1,2,3,64}.svg`. La validación XML confirma 240×240. Los callbacks producen 0, 1, 2 o 3 tarjetas conforme a la política existente; la biblioteca de 64 produce tres tarjetas, no 64 tarjetas simultáneas.

La prueba incluye BPB y volumen inválidos, desconexión, timeout, FAT cíclica, cadena prematuramente terminada y hash incorrecto. Missing y EOF se prueban por separado: el archivo inexistente produce `MB_IO_MISSING`; offset igual al tamaño produce `MB_IO_EOF`; offset fuera de rango produce `MB_IO_ERROR`.

La medición de la imagen de tres entradas es 26 sectores desde mount hasta verificación; la de 64 entradas es 60 sectores. Esto incluye relecturas de directorios/FAT porque el adaptador abre cada ruta de forma segura. `sizeof(fat32)=560`; no hay caché de cluster todavía. El coste es aceptable para la demostración, pero la lectura secuencial/caché queda como optimización futura para imágenes grandes.

El objetivo pi32v2 integrado se añadió separadamente y enlaza core, adaptador, FAT32, HAL ficticia y runtime. Si el enlace supera el assert de 64 KiB, el workflow conserva el error como bloqueo reproducible; nunca se considera SRAM física.
