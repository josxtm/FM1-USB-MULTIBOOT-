# Pruebas adversariales FAT32 — 5D.1

`make fat32-integration` ejecuta casos deterministas sobre la imagen generada:

| Caso | Resultado comprobado |
|---|---|
| BPB inválido | `fat32_mount()` rechaza con `DIAG_FAT` |
| Tamaño de volumen incoherente | `DIAG_FAT` |
| Sector fuera de rango | `DIAG_RANGE`/`MB_IO_ERROR` |
| Archivo/directorio ausente | `MB_IO_MISSING`, nunca `MB_IO_EOF` |
| EOF válido | `MB_IO_EOF` sólo en offset igual al tamaño |
| Lectura fuera del archivo | `MB_IO_ERROR` |
| FAT cíclica | verificación `INCOMPATIBLE`, sin bucle |
| Cadena prematuramente terminada | `INCOMPATIBLE` |
| Hash incorrecto | `INCOMPATIBLE`, integrity permanece 0 |
| Timeout/desconexión | `ERROR`/`NO_STORAGE` según el resultado |
| Archivo fragmentado | lectura y SHA correctos |
| 0/1/2/3/64 entradas | catálogo y SVG correctos |

Las pruebas C existentes cubren además bytes extra, truncamiento, no progreso y límites de catálogo. `make fat32-sanitize` ejecuta la integración con AddressSanitizer y UndefinedBehaviorSanitizer. No se acepta ninguna integridad si la cadena FAT o el tamaño no llegan al EOF esperado.

Los casos MBR, FAT dañada, BOT, CSW, timeout y fichero fragmentado también permanecen cubiertos por `tests/usb_storage_test.c`; son simulaciones host-only.
