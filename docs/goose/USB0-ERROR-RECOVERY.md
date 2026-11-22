# USB0/MSC/BOT — errores y recuperación

| Falla | Respuesta acotada |
|---|---|
| Descriptor truncado/clase/endpoints inválidos | Rechazo antes de configurar pipes |
| CBW inválido, LUN no cero, flags reservados | `MALFORMED`/`DIAG_UNSUPPORTED`, sin transferencia |
| STALL data IN/CSW | CLEAR_FEATURE, reset toggle, un retry; luego recovery |
| CSW signature/tag/residue inválido | BOT recovery y `DIAG_TRANSFER` |
| CSW status 1/2 | `DIAG_SCSI`, sin interpretar datos como válidos |
| Timeout | `DIAG_TIMEOUT`, recovery acotada |
| Desconexión | `DIAG_DISCONNECTED`, no se continúa en FAT32 |
| Sector/BPB/rango inválido | error FAT/range; nunca salto ni escritura |

La recuperación existente usa control request BOT reset y clear halt de ambos endpoints, y `husb_attached()` antes de operar. La duración exacta y la compatibilidad eléctrica necesitan validación física; ningún retry puede crear VBUS ni ejecutar código.
