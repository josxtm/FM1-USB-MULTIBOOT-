# Evidencia eléctrica USB-C — FM-1

## Evidencia disponible

* Melodee documenta USB Host MIDI full-speed en USB0, pull-downs y `DEVCTL=3`, pero declara que el firmware no conduce VBUS y requiere hub/OTG alimentado externamente: [BUILDING.md](https://github.com/keremimo/melodee/blob/main/BUILDING.md#usb-midi-host).
* Su `fm1_usb.h` documenta una secuencia de registros de Host, no el esquema eléctrico concreto del FM-1.
* El m-bootloader documenta una modificación m-card SPI1 para SD; no prueba un lector USB-C MSC: [SPEC.md §11.5](https://github.com/jvitkauskas/m-bootloader/blob/main/docs/SPEC.md#115-sd-card-stage-2).

## Sin verificar

No hay esquema público confirmado en este repositorio para CC1/CC2, Rp/Rd, VBUS, limitación de corriente, backfeed, ESD, conector, hub o pinmux del ejemplar FM-1. El datasheet AC791N por sí solo no demuestra el circuito de la placa.

**Confianza:** controlador Host teórico/documentado: media; compatibilidad eléctrica de la placa: baja/desconocida; MSC físico: no demostrado. No conectar accesorios ni inyectar 5 V en esta fase.
