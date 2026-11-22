# FASE 5X.2-R — arquitectura

## Modelo objetivo

```text
microSD USB-C (pendiente eléctrico)
 → MSC/BOT read-only → FAT32 → FM1CAT v1
 → selector textual 240×240 / PRESETS / PLAY
 → inspector + SHA-256 + política
 → instalación virtual fail-closed
 → verificación → reinicio simulado
```

La SRAM no almacena ni ejecuta firmwares completos. Sólo se usan buffers acotados para lectura, hash y render.

## Decisión arquitectónica

El objetivo de instalación persistente exige un componente que siga siendo invocable después de sustituir `app.bin`. La evidencia pública actual sólo demuestra:

- bootloader y menú de recuperación;
- stage-2 temporal desde microSD SPI/m-card;
- ROM USB/download con host externo;
- aplicaciones XIP en la app area.

No demuestra un selector residente independiente ni un lector USB-C MSC preboot. Por tanto la arquitectura autónoma completa queda **NO-GO**. Se conserva el selector como software de inspección/instalación virtual y se bloquea la instalación física.

## Interfaces conservadas

FM1CAT, FAT32, MSC/BOT, SHA-256, renderer textual, PRESETS y PLAY permanecen. Cover Flow sólo existe como regresión histórica. APP1 queda como antecedente RAM experimental, no como mecanismo de firmware.
