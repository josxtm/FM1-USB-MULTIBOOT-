# Requisitos USB-C Host y microSD

## Estado

El repositorio contiene análisis y pruebas host de USB MSC full-speed (class 08/subclass 06/protocol 50), BOT, SCSI de sólo lectura, FAT32, MBR/superfloppy y desconexión/timeout simulados. No se ha activado USB Host en el FM-1 ni se ha medido VBUS, CC, corriente, rol USB, protección o pinmux en la placa.

## Requisitos que deben demostrarse antes de conectar hardware

| Área | Requisito | Estado |
|---|---|---|
| Rol | AC791N y placa en modo Host compatible | pendiente físico |
| VBUS | fuente limitada, protección y ausencia de backfeed | pendiente; no inyectar 5 V |
| CC | identificación correcta de Rd/Rp y cable | pendiente |
| Señal | D+/D−, ESD, impedancia y pinmux | pendiente |
| Software | enumeración, descriptors, MSC/BOT, SCSI read-only | simulado |
| Medio | FAT32, desconexión, errores y capacidad | simulado |
| Energía | corriente de lector y comportamiento brownout | pendiente físico |

La capacidad teórica del procesador no demuestra capacidad de la placa FM-1. Un lector alimentado externamente o hub sólo sería evaluable tras revisión eléctrica independiente. Esta fase no conecta lectores, no activa VBUS y no modifica firmware.
