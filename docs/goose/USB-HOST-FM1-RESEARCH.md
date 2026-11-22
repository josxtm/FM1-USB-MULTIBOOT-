# FASE 5E — USB Host del FM-1

## Alcance y clasificación

Esta investigación es software/documentación; no se activó el USB Host físico, VBUS, CC ni ningún cableado. `src/husb.c` es evidencia propia de una implementación full-speed polled; `build/research/*` fue descargado temporalmente desde fuentes públicas y no se incorpora al firmware.

## Evidencia externa

* [Melodee USB MIDI host](https://github.com/keremimo/melodee/blob/main/BUILDING.md#usb-midi-host) y [fm1_usb.h](https://github.com/keremimo/melodee/blob/main/firmware/hal/fm1_usb.h) documentan USB0, PHY, pull-downs, `DEVCTL=3`, `CON0=0x25`, endpoints y enumeración MIDI. El firmware no conduce VBUS; exige hub/OTG con alimentación. La documentación reporta full-speed y MIDI 1.0, no MSC.
* [m-bootloader SPEC](https://github.com/jvitkauskas/m-bootloader/blob/main/docs/SPEC.md) dice explícitamente que ROM USB posee enumeración/BOT en modo download y que el SD del FM-1 requiere una modificación m-card por SPI1. No demuestra que una aplicación pueda usar USB Host MSC.
* [m-flasher USB](https://github.com/jvitkauskas/m-flasher/blob/main/src/usb.rs) es host USB del ordenador hacia el dispositivo, no host USB del FM-1.
* [Tessera](https://github.com/Rastapopolix/tessera) reutiliza el USB MIDI Host de Melodee; su documentación declara GPL-3.0-only.

## Conclusión

El AC791N tiene evidencia pública de un controlador USB Host full-speed y MIDI Host, pero no de driver MSC, hubs MSC, VBUS/CC de la placa FM-1 ni alimentación suficiente. MSC es técnicamente plausible como trabajo nuevo sobre el controlador, no demostrado. MIDI Host no se puede convertir automáticamente en MSC: requiere enumeración de interfaces clase 08/06/50, BOT y SCSI.

## Licencias

Melodee y Tessera: GPL-3.0-only; m-bootloader: MIT; m-flasher: MIT. No se copió código GPL. Este repositorio conserva sus propias licencias MIT y solo cita URLs.

## Pendiente

Medir/autorizar por separado CC, VBUS, protección, corriente, hubs y enumeración. Ninguna dirección de registro ni secuencia externa se convierte aquí en una constante física aprobada.
