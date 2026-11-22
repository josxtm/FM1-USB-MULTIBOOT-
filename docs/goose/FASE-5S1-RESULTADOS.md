# FASE 5S.1 — integración definitiva PRESETS + PLAY

## Encoder PRESETS

El decoder lógico existente se conservó y ahora forma parte del bucle de entrada mediante `encoder_scan` opcional. La HAL entrega A/B; cuatro transiciones forman un detente, los rebotes/transiciones inválidas se descartan y los eventos se convierten en NEXT/PREVIOUS.

La investigación no encontró evidencia suficiente para afirmar el GPIO físico del encoder PRESETS/encoder 6 del FM-1 instalado. No se inventó pinout ni se modificó `keys.c`. La integración física queda desacoplada hasta disponer de una HAL verificable.

## PLAY real en software

La ruta host ahora procesa PLAY como:

`PLAY → congelar selección actual → mb_verify_selected() → SHA-256 → mb_launch_policy → mb_prepare_launch()`

La selección no cambia durante la verificación. El resultado queda en `mb_ram_app_last_compatibility()` y en el estado core. Un APP/archivo con integridad correcta pero ABI no demostrado queda `INSPECTION_ONLY`; `mb_prepare_launch()` continúa devolviendo `MB_LAUNCH_BLOCKED`. REC/CANCEL permite volver desde estados de resultado.

Pulsaciones repetidas siguen filtradas por el adaptador de teclas y no existe salto físico.

## DEMO/USB

DEMO permanece disponible para host sin hardware. USB MSC es backend explícito y no se inicializa automáticamente. La prueba de entrada USB emulada recorre MSC/BOT→FAT32→INDEX.CSV→lista→encoder/PLAY→verificación→política. Errores de storage no hacen fallback silencioso.

## Interfaz

El footer textual muestra `PRESETS: ELEGIR` y `PLAY: INICIAR`. Se conserva la captura 240×240 y el renderer no usa framebuffer completo. Se mantienen pruebas de catálogos 0/1/2/3/64, renderer, encoder y flujo RAM host.

## Memoria y arquitectura

La medición 5S anterior fue `.image=22216` y APP1=22224 bytes, BSS=25896. 5S.1 enlaza policy/PLAY y mide `.image=23724` bytes, APP1=23732 bytes y BSS=25896 (`0x6528`); SHA-256 APP1 `bb20fca002bd0a4785c0c26a5634eb4a1eb3d9f9bb1c224ab45fc73c2725a6e8`. El aumento de 1508 bytes corresponde a la ruta de verificación/policy y estado de compatibilidad; no se amplió BSS. El mapa es virtual y no prueba SRAM. El selector no puede volver automáticamente si otro firmware reemplaza su misma área Flash: eso requiere un residente fuera de esa región o reinstalación/recovery externo. La instalación virtual no se presenta como rollback real.

## Validación y estado de entrega

Los tests host pasan para `ram-app-test`, `presets-test`, `list-renderer-test` y `launch-policy-test`. El flujo integral usa `encoder_scan`, verifica la selección y comprueba que PLAY mantenido no genera eventos repetidos antes del periodo de debounce/repetición. El renderer recibe ahora el estado de bloqueo (`BLOCKED: ...`) y muestra la razón de política; no se confunde una verificación SHA correcta con autorización de ejecución.

Los cuatro workflows del commit final `4d0dd7ca6dc260535add497ff9def2977b150ee0` terminaron correctamente:

- Embedded multiboot core — run `45058269731` — success.
- Cover Flow simulator — run `45058274349` — success; se conserva sólo como regresión.
- FM-1 RAM diagnostics — run `45058278771` — success.
- Android RAM Loader — run `45058282246` — success.

La validación pi32v2 completa se mantiene en CI; el entorno local no contiene el toolchain fijado. El remoto coincide con el commit indicado y PR #8 permanece OPEN/Draft.

## Bloqueos

ABI stage-2/run-ram, SRAM, Flash map, pinout PRESETS, reloj, recovery, USB físico y compatibilidad de firmwares siguen pendientes. No se modificó main, m-bootloader o DARK-1; no se escribió Flash ni se ejecutó FM1. PR #8 continúa Draft.
