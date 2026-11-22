# FM1 — microSD por USB-C frente a SPI

## Comparación

| Criterio | A: USB-C → lector MSC | B: SPI1 → microSD tipo m-card |
|---|---|---|
| Evidencia de placa | USB-C eléctrico no identificado | m-card publica PCB, BOM y netlist de referencia |
| Cambios físicos | lector/hub y solución de VBUS/CC | placa intercalada, FFC y compatibilidad mecánica |
| Software | USB host, descriptores, MSC/BOT, SCSI y FAT32 | inicialización SD SPI, bloque 512 y FAT32 |
| Reutilización | `husb.c` + `storage.c` + `msc_bot.c` + `fat32.c` | `fat32.c`; se necesita HAL SD/SPI nueva |
| Alimentación | riesgo alto: VBUS, corriente, backfeed y hub | m-card toma 3,3 V documentados, aún requiere medir margen |
| Pines | D+/D− y CC no confirmados | PC9/PC10/PC8/PC7 documentados para SPI1/LCD |
| Velocidad | USB full-speed teórico, lector depende del dispositivo | SPI lento inicial ~400 kHz; luego frecuencia documentada por firmware |
| Coste | lector/hub alimentado y cableado | PCB 30×25,2 mm, FFC, socket y componentes |
| Recuperación | añade estado USB y posibles bloqueos de attach | comparte bus LCD y exige aislamiento correcto de CS |
| Riesgo actual | alto/desconocido | medio, condicionado a revisión/mecánica y prototipo no probado |

## A — USB-C

Ventajas: no requiere diseñar una interfaz SD y el repositorio ya posee BOT read-only simulado. Bloqueos: la placa puede ser USB device-only, no se conoce VBUS host, y el firmware no debe asumir que `CON0=0x25` crea alimentación externa. La solución requeriría primero evidencia eléctrica y después una prueba exclusivamente de enumeración/lecturas.

No se propone conectar directamente un lector ni un hub en esta fase.

## B — SPI1 / m-card

La documentación de `m-bootloader` y `m-card` converge en una arquitectura concreta: microSD en modo SPI compartiendo el bus del panel, CS invertida respecto al LCD, DO compartido con D/C y resistencias para limitar contención. Es la alternativa mejor justificada documentalmente. Sin embargo, `m-card` se declara prototipo no probado y no existe evidencia de que la unidad del propietario tenga la misma revisión/J12/mecánica.

El software de este proyecto no debe reutilizar automáticamente el stage-2 del bootloader: el contrato de entrada, memoria, watchdog y recuperación son distintos. La integración futura debe ser una HAL de bloques SD read-only detrás de `fat32.c`, no una modificación del m-bootloader.

## Decisión 5I

No fabricar ni conectar todavía. Prioridad:

1. identificar PCB y J12 con fotografías;
2. verificar continuidad y ausencia de cortos sin alimentación;
3. revisar mecánica y alimentación de m-card;
4. sólo después decidir prototipo SPI;
5. dejar USB-C como alternativa separada hasta resolver CC/VBUS/rol.
