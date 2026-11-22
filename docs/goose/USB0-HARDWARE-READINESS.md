# FASE 5G — USB0 hardware readiness

## Estado

`src/husb.c` es un controlador USB0 Host full-speed polled, con EP0 y EP1 TX/RX, buffers DMA alineados, SIE indirecto por `CON1`, reloj PLL 48 MHz, sesión `DEVCTL=0x03`, reset de bus, control transfers y bulk transfers. `src/storage.c` añade enumeración MSC 08/06/50 y `msc_bot.c` añade framing común read-only.

Esto es código preparado para compilación y simulación, no validación del FM-1. No se ha activado USB Host físico.

## Auditoría de incertidumbres

| Elemento | Estado |
|---|---|
| offsets USB0 `0x11800`, IO `0x51000`, clock `0x10010` | código propio/documentación externa, no medido en FM-1 |
| `CON0=0x25`, pull-downs y `DEVCTL=3` | documentado por Melodee/SDK; aplicabilidad de placa no verificada |
| PLL 48 MHz | secuencia de código; reloj físico no medido |
| EP1 TX/RX y DMA | implementado y compilable; operación física no verificada |
| toggles DATA0/1 | gestionados por CSR1 y clear halt; trazas físicas inexistentes |
| VBUS/CC/pinmux | desconocido |
| lector MSC/hub | desconocido |

La implementación existente no debe describirse como USB Host funcionando en FM-1 hasta obtener una prueba reproducible.
