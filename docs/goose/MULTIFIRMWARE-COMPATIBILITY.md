# Matriz de compatibilidad multifirmware

FM1CAT v1 registra presencia, tamaño y SHA-256. No convierte un archivo íntegro en ejecutable.

| Caso | Formato | Estado | Método | Evidencia necesaria |
|---|---|---|---|---|
| `ID.BIN` | bytes opacos/raw | `UNVERIFIED` | `UNKNOWN` | ABI, entry point, mapa y bootloader |
| `ID.FWS` | contenedor FWSC opaco | `UNVERIFIED` | `UNKNOWN` | especificación y validación independiente |
| APP1 aprobado | APP1 v1, placa 1, CRC/SHA conocidos | compatible solo con simulación | `RAM` | reservas SRAM y ABI del bootloader real |
| Firmware XIP | no definido por FM1CAT v1 | `UNVERIFIED` | `XIP` | direcciones Flash y organización real |
| Firmware Flash | no permitido | no evaluado | `FLASH` | recuperación integral y autorización separada |

## Reglas

- IDs, rutas, tamaños, duplicados y hashes se validan antes de publicar `INDEX.CSV`.
- `.BIN` y `.FWS` se copian byte a byte; nunca se desempaquetan ni modifican.
- `UNVERIFIED` es el único estado que la aplicación puede generar automáticamente.
- La integridad criptográfica solo demuestra que se releen los mismos bytes.
- La identidad del bootloader `2.x` es compatibilidad preliminar de protocolo, no autorización de ejecución.
- Cover Flow permite inspeccionar, pero PLAY no ejecuta ningún firmware.
