# USB MSC/BOT — integración de solo lectura

## Capas

`USB Host → MSC/BOT → bloque 512 B → FAT32 → FM1CAT → core → renderer`. `src/storage.c` ya implementa enumeración de descriptors, interfaz 08/06/50, Bulk IN/OUT, CBW/CSW, TUR, REQUEST SENSE, READ CAPACITY(10), READ(10), STALL y errores. Sigue dependiendo de `husb` y no se enlaza con la ruta física del core.

FASE 5E añade `src/msc_bot.c/.h`: framing BOT independiente del controlador, validación bounded de CBW/CSW y política que admite TUR, REQUEST SENSE, INQUIRY, READ CAPACITY y READ(10), rechazando WRITE(10) y cualquier CDB no permitida. No contiene callbacks de VBUS, GPIO, USB o Flash. `mb_msc_bot_read10()` es una prueba de transporte sobre HAL, no activación física.

## Pruebas

`tests/msc_bot_test.c` verifica firma/tamaño/tag, dirección, CDB length, READ(10), rechazo de escritura, CSW tag/residue/status y estados de dispositivo. `tests/msc_fat32_integration.c` conecta el BOT simulado a un `block_device` real del parser FAT32 y valida `FM1/INDEX.CSV` sobre la misma imagen determinista. La integración FAT32 existente sigue usando imágenes 0/1/2/3/64, SHA-256 y `MB_LAUNCH_BLOCKED`; no se presenta como microSD física. ASan/UBSan y el target pi32v2 compilan esta capa cuando el toolchain está disponible.

## Política

No se implementan OUT de datos, WRITE(10), FORMAT, ERASE ni comandos de programación. Timeout, desconexión y STALL permanecen responsabilidades del callback/driver; el adaptador FAT32 conserva `MB_IO_MISSING` versus `MB_IO_EOF`.
