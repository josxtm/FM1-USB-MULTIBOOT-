# FASE 5I — resultados: PCB, USB-C y microSD

## Estado

Esta fase es de investigación documental y preparación de mediciones. No se conectó una microSD, lector USB, hub, adaptador USB-C, fuente externa ni instrumento a un FM-1. No se abrió ni modificó una unidad. `main` permanece intacto, el PR #8 sigue Draft y `MB_LAUNCH_BLOCKED` no cambia.

## Evidencia clasificada

### Verificada en fuentes públicas

- `m-card` publica una placa de 30 × 25,2 mm para FM-1, con licencia CC0-1.0.
- La placa se intercala entre J12 de la placa principal y el LCD mediante dos FFC de 10 vías; el README la califica como **prototipo no probado**.
- La tarjeta comparte SPI1 del LCD y especifica: PC9=CLK, PC10=DI/MOSI, PC8=DO/MISO compartido con LCD D/C y PC7=CS del LCD.
- La CS de la microSD es la inversión de CS del LCD. El diseño usa un 74LVC1G04, resistencias serie y pull-ups/pull-downs.
- `m-bootloader SPEC`, sección 11.5, documenta la misma arquitectura: microSD SPI, sin card-detect, arranque lento aproximado de 400 kHz y FAT32 con sectores de 512 bytes.
- El mismo SPEC indica que el firmware de stage 2 debe enlazarse para `0x01C28000`, con restricciones propias del m-bootloader. Eso no autoriza ni valida el multiboot de este repositorio.
- La documentación pública de Melodee demuestra USB0 full-speed y host MIDI, pero declara que el firmware no conduce VBUS y requiere alimentación externa para el accesorio.

### No verificada

- Revisión exacta de la PCB del FM-1 del propietario.
- Existencia, orientación y población de J12 en esa revisión.
- Conexión del USB-C al AC791N, CC1/CC2, VBUS, ESD, protección de backfeed y limitación de corriente.
- Disponibilidad física de SPI1 en pads accesibles y continuidad con J12.
- Pinmux efectivo de la unidad concreta.
- Margen mecánico para `m-card`, espesor de FFC y posición de la batería/audio-jacks.
- Compatibilidad eléctrica de cualquier lector USB-C con el FM-1.

## Conclusión provisional

La arquitectura SPI mediante una placa tipo `m-card` es la ruta con evidencia más concreta y menor incertidumbre de protocolo, pero el diseño público sigue marcado como prototipo no probado y requiere confirmar la revisión real. La ruta USB-C→MSC necesita resolver simultáneamente rol USB host, CC, VBUS, protección y alimentación; no puede aprobarse a partir del driver `husb.c`.

La primera acción recomendada es fotografía e identificación sin alimentación ni modificaciones, seguida de continuidad/resistencia únicamente si los puntos están identificados. No se recomienda fabricar, soldar ni alimentar un adaptador todavía.

## Cambios software

No fueron necesarios cambios de código en 5I. Los módulos existentes siguen separados: `husb.c`/`storage.c` para el camino USB futuro, `msc_bot.c`/`fat32.c` para transporte simulado y FAT32, y LCD/keys únicamente como HAL ya preparada. No se activa ninguna ruta física.

## Referencias

- https://github.com/jvitkauskas/m-card
- https://github.com/jvitkauskas/m-bootloader/blob/main/docs/SPEC.md#115-sd-card-stage-2
- https://github.com/keremimo/melodee/blob/main/BUILDING.md#usb-midi-host
- https://github.com/keremimo/melodee/blob/main/firmware/hal/fm1_usb.h
