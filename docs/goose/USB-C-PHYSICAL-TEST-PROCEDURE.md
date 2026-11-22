# Procedimiento futuro USB-C — solo lectura

Este documento es preparación, no autorización ni resultado físico.

| Paso | Prerrequisito/evidencia | Aceptación | Abortar/riesgo |
|---|---|---|---|
| 1. Revisar esquema | pinout, CC1/CC2, VBUS, GND, protección | rol Host/Device documentado | detener si falta esquema |
| 2. Cableado | adaptador conocido, sin puentes improvisados | D+/D− y GND identificados | abortar ante duda de VBUS/backfeed |
| 3. Pasivo | autorización explícita y equipo limitado | CC/VBUS sin inyección, sin corto | detener ante tensión inesperada |
| 4. Alimentación | lector/hub autoalimentado y corriente limitada | no depende del FM1 para VBUS | no asumir que hub elimina riesgo |
| 5. Enumeración mínima | firmware aprobado y recovery independiente | solo descriptor/control | detener ante brownout/calor |
| 6. Interfaz MSC | descriptor 08/06/50 y bulk pair | no MIDI-only | no enviar write |
| 7. Capacidad | READ CAPACITY(10), block 512 | tamaño coherente | abortar si block !=512 |
| 8. Sector | READ(10) de LBA controlado | sector FAT esperado | desconectar ante errores |
| 9. Catálogo | `FM1/INDEX.CSV` | parseo/hash correcto | no ejecutar firmware |

Registrar instrumento, alimentación, cable, VID/PID, descriptors, errores, temperatura y motivo de aborto. No cargar APP1, no usar Flash/RAM commands y mantener `MB_LAUNCH_BLOCKED`.
