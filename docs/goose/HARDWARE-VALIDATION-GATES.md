# Gates de validación física

| Gate | Evidencia actual | Pendiente | Criterio de aceptación |
|---|---|---|---|
| MIDI MODE / `0x12` | **VALIDADO FÍSICAMENTE**: Android 16 respondió `RESPONSE`; m-bootloader `2.4-395e6cc`, protocolo 1, 1 MiB, JEDEC `0x856014` | Repetir solo si se necesita evidencia adicional; no implica ejecución | Identidad, versión y capacidad sin escribir |

| Recuperación | PLAY abre menú y BOOT APP vuelve a DARK-1 | Restauración integral | Copia/hash y restauración repetible |
| Bootloader | `2.4-395E6CC` observado | Reservas RAM/ABI | Mapa confirmado por evidencia independiente |
| USB-C VBUS | No caracterizado | Medición segura bajo carga | VBUS, CC, corriente y protección sin backfeed |
| USB Host | Simulado en software | Lector real | Enumeración, BOT, FAT32 y desconexión |
| microSD | Catálogo Android/simulador | Lectura desde FM-1 | `FM1/INDEX.CSV` secuencial y sin errores |
| APP1 RAM | ELF/CRC/SHA simulados | Carga/retorno físico | Solo tras autorización de seguridad independiente |
| Flash | Región protegida documentada | Recuperación completa | Nunca tocar `[0x00000,0x04000)` |

No conectar lectores con alimentación improvisada, no inyectar 5 V y no probar comandos Flash/RAM. Los gates físicos requieren intervención explícita del usuario; esta fase no ejecuta ninguno.
