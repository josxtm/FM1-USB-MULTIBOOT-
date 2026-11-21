# FASE 4B — Resultados

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Build objetivo:** `FM1_RAM_Loader_v1.0_comp9.apk` / `versionCode 9`

## Cambios

- Eliminada la etiqueta residual de la versión anterior de la actividad y actualizada a comp9.
- `MbootQueryController` integrado en `MbootMidiActivity`: el controlador es ahora la única fuente de verdad para token, timeout, cancelación, respuesta, error y cierre de sesión.
- Eliminada la duplicación de `generation` y `Handler.postDelayed` de la actividad.
- El timer Android usa `Handler` como adaptador de `MbootQueryTimer`; los tests usan un timer falso, sin dormir ni depender del reloj real.
- Añadido flujo completo simulado: consulta fija `0x12`, emulador, respuesta fragmentada, collector, parser, compatibilidad preliminar y finalización del controlador.
- Mensajes explícitos para timeout, cancelación, desconexión, error de parser y respuesta completada.
- Compatibilidad 2.x presentada como compatibilidad preliminar de protocolo; nunca autoriza carga o ejecución.

## SHA-256 APP1

El artefacto actualmente versionado en `android/app/src/main/assets/` mide 8.956 bytes y produce:

```text
810bb3673ef2fc75964724e07d42bb751d1e3e5e49056cdaccad40c3764ea246
```

Coincide con `AppSlot.SHA256`. No se modificó la referencia. La comprobación adicional de ELF, mapa, tráiler APP1, CRC y origen del binario continúa en `make all`/CI; no se sustituyó el artefacto ni se ejecutó en hardware.

## Tests y CI

- Tests Kotlin, Lint y compilación Android: deben ejecutarse en GitHub Actions.
- Diagnóstico RAM/pi32v2 y hashes: workflow existente.
- Cover Flow: workflow manual y/o check de PR.
- Tests locales C/ASan/UBSan: conservados.

Los workflows no prueban hardware. La consulta física sigue limitada a `0x12`.

## Estado

### Software verificado

- Flujo Android completo mediante transporte MIDI simulado.
- Timeout, cancelación, desconexión y respuesta obsoleta.
- SysEx fragmentado y validación de identidad.
- Compatibilidad preliminar de versión 2.x.
- SHA-256 del APP1 versionado.
- APP1 simulado, catálogo FM1CAT, FAT32 y Cover Flow.

### Hardware previamente documentado

- m-bootloader 2.4-395E6CC presente.
- Menú de recuperación y retorno a DARK-1 mediante BOOT APP.
- Flash Puya `0x856014`, 1 MiB.

No se realizaron operaciones físicas en esta fase.

### Pendiente físico

- Respuesta real MIDI `0x12`.
- Mapa RAM/ABI y reservas SRAM del bootloader instalado.
- VBUS, CC y protección USB-C.
- Lectura de microSD mediante lector físico.
- Restauración integral de Flash.

Siguen bloqueados Flash, `0x25`, `0x30`, ejecución APP1 y cualquier modificación de DARK-1 o del bootloader.
