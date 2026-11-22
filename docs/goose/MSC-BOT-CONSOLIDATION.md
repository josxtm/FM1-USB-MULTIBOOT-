# FASE 5F — consolidación MSC/BOT

## Decisión

`src/msc_bot.c/.h` es ahora la autoridad común para framing BOT: construcción de CBW, validación de CBW/CSW, residue/tag/status y política SCSI read-only. `src/storage.c` conserva únicamente enumeración USB, descriptors, endpoints, husb, timeouts y adaptación de errores físicos.

`storage.c` usa `mb_msc_build_cbw()`, `mb_msc_validate_cbw()`, `mb_msc_validate_csw()` y `mb_msc_read_only_cdb()`. Ya no valida CBW/CSW con reglas paralelas ni genera el wrapper por separado. `msc_bot` no conoce FAT32 ni registros USB.

## Correcciones

* LUN no cero y flags reservados se rechazan.
* Residue se comprueba sin overflow y contra bytes recibidos.
* Status CSW 0/1/2 se distingue de framing inválido.
* STALL durante data IN hace CLEAR_FEATURE/toggle reset y un único retry acotado.
* WRITE/format y CDB no admitidas se rechazan antes de transferir.

## Límites

El BOT RESET/STALL físico sigue en `storage.c` porque requiere control endpoint y husb. No se activó USB0 real. La HAL genérica y el emulador determinista ejercitan la misma política sin GPIO/VBUS.
