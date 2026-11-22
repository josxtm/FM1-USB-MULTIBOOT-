# Plan de recuperación multiboot

## Requisitos

1. Backup íntegro verificado externamente antes de cualquier futura escritura; conservar `[0x00000,0x04000)` y metadatos.
2. El m-bootloader 2.4/M-Flasher debe seguir siendo un camino independiente: menú PLAY, REC/download mode y restauración oficial.
3. Dos imágenes o commit marker atómico, checksum antes de arrancar, rollback ante power loss, fallo de escritura o imagen corrupta, y límite estricto de regiones.
4. DARK-1 solo se estudiará como aplicación reemplazable; nunca como bootloader. La autorización de estudio no autoriza instalación.

## Evidencia

[m-bootloader README/SPEC](https://github.com/jvitkauskas/m-bootloader) documenta menú de recuperación, backup host y comprobación de firmware. [M-Flasher](https://github.com/jvitkauskas/m-flasher) verifica escrituras y mapas. Son fuentes externas; no se ejecutaron operaciones.

## Estado

La recuperación del nuevo selector es un diseño, no una propiedad demostrada. Un archivo DARK-1 descargable no prueba recuperación. Simulaciones futuras deberán modelar corrupción, interrupción, rollback y verificación post-write; ningún modelo sustituye una recuperación física autorizada.
