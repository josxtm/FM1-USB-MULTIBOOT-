# Investigación comunitaria

## Fuentes prioritarias

- `jvitkauskas/m-bootloader`: SPEC, boot/recovery, stage-2, ROM USB.
- `jvitkauskas/m-flasher`: BANKCB, `.fwsc`, `flash.bin`, tablas y planificación de sectores.
- `jvitkauskas/m-card`: microSD SPI1 compartida con LCD; modificación física.
- `jvitkauskas/m-apps`: referencia de aplicaciones/slots, no prueba de multiboot completo persistente.
- `joakimeriksson/fm1-dock`: módulos/interfaz, no contrato de sustitución de `app.bin`.
- `jvitkauskas/m-unbricker` y `workeffortwaste/fm1-backup-restore`: recuperación/backup externo, no selector residente demostrado.
- `jvitkauskas/clean-flash`: operaciones de limpieza, no autorización de instalación.
- `kmplngj/fm1-bootloader`, `AL-255/FM-1-RE`, `charlesvestal/fm1-base`, `zvenson/dinghy`, `joakimeriksson/fm1-emu`: referencias de firmware, reverse engineering, runtime o emulación; no aportan un contrato probado para este selector.

## Licencias

No se incorporó código de terceros. Las fuentes se citan como investigación; antes de copiar cualquier implementación se deben revisar licencia, atribución y compatibilidad técnica. El código nuevo de este repositorio sigue siendo propio.

## Resultado

No se encontró una capacidad comunitaria que permita conservar un selector completo independiente después de sustituir la app, sin modificar bootloader/placa o depender de un host externo. m-apps/dock no se tratan como equivalentes a firmware multiboot.
