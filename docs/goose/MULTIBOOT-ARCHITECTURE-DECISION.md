# Decisión de arquitectura multiboot — FASE 5F

## A — selector residente en Flash

**Ventajas:** UI local, acceso directo al boot contract y recuperación centralizada. **Requisitos:** conservar m-bootloader, mapa exacto de aplicación, XIP, vectores, watchdog, formato oficial, commit atómico y backup independiente. **Compatibilidad:** no demostrada para DARK-1 ni firmware original reubicado. **Hardware:** no requiere USB si usa SPI/microSD, pero una tarjeta FM-1 requiere modificación m-card documentada externamente. **Estado:** candidato de investigación; ninguna escritura autorizada.

## B — selector híbrido

**Ventajas:** cargador mínimo residente y datos/firmwares externos; reduce Flash residente. **Requisitos:** ABI de carga/retorno, SRAM suficiente, imágenes adaptadas y recuperación fuera del selector. **Compatibilidad:** únicamente firmwares preparados; leer un `.BIN/.FWS` por bloques no equivale a ejecutarlo. **Estado:** alternativa condicionada, no viable aún para firmware original.

## Decisión actual

Mantener core + FAT32 + MSC read-only como software de inspección y Cover Flow. No elegir A/B para instalación hasta verificar mapa, ABI, SRAM y recovery. m-bootloader 2.4 y `[0x00000,0x04000)` permanecen protegidos; DARK-1 no se toca. `MB_LAUNCH_BLOCKED` permanece.
