# FASE 5X.2-R — resultados

## Estado

La fase conserva la arquitectura, auditor y protecciones de FASE 5X.2, descarta SRAM como almacenamiento/ejecución de firmwares completos y consolida el objetivo `microSD → lista → PRESETS/PLAY → Flash virtual → reinicio simulado`.

La solución autónoma completa permanece **NO-GO** porque no existe evidencia de selector residente, recuperación USB-C preboot ni interceptación universal de botones tras sustituir `app.bin`.

## Implementado

- Siete documentos de arquitectura, persistencia, hardware microSD, seguridad Flash, investigación comunitaria, compatibilidad y resultados.
- FM1CAT/FAT32/MSC/BOT/selector textual/PRESETS/PLAY conservados.
- Instalador Flash virtual fail-closed conservado.
- Auditor Flash y fixtures sintéticos conservados.
- APP1 retenido únicamente como antecedente técnico.
- Sin nueva APK Android y sin Cover Flow en firmware final.

## Acceso microSD

USB-C requiere evidencia eléctrica de rol Host, CC, VBUS, backfeed, protección, HUSB y alimentación del lector. No se activa.

m-card/SPI tiene evidencia documental de stage-2 temporal, pero requiere modificación física y no proporciona selector residente. No se implementa ni se presenta como equivalente USB-C.

## Persistencia y botones

PRESETS/OCT−/OCT+/PLAY/REC son controles del menú que los escanea. No pueden presumirse globales en firmware propietario. El objetivo de interfaz se conserva, pero la persistencia no está demostrada.

## Validación

La suite existente y el auditor anterior permanecen como fuente de verdad. Pasaron localmente `make test`, `make firmware-tools-test`, `make flash-backup-audit-test`, `make boot-recovery-test`, `make virtual-installer-test` y `python3 -m unittest discover -s tests -p '*test.py'` con 19 tests Python OK.

Los cuatro workflows pasaron sobre `f8d89f73fe317f3d29c29746bbe931bc1b702ec0`:

- Embedded multiboot core — `45074703773` — success.
- Cover Flow simulator — `45074707434` — success, sólo regresión.
- FM-1 RAM diagnostics — `45074710419` — success.
- Android RAM Loader — `45074713391` — success.

La nueva fase no modifica código ejecutable ni envía comandos al FM1.

## Bloqueos

- selector residente o entry de recovery hacia él;
- USB-C Host físico y microSD preboot;
- ABI stage-2/run-ram/XIP para selector;
- recuperación independiente antes de instalar;
- formato y regiones individualmente autorizados para cada firmware.

## Recomendación FASE 5Y

Antes de cualquier fase física: aprobar un diagnóstico exclusivamente read-only del FM1 y un procedimiento de backup verificable. No crear writer, no ejecutar APP1, no instalar firmware y no modificar bootloader/DARK-1.

## Estado

- Rama: `feat/fm1-autonomous-flash-multiboot-5x2r`.
- Base: `goose/fase3b-4a-preflight`.
- PR nuevo: Draft hacia `goose/fase3b-4a-preflight` después del push.
- PR #8: se conserva sin fusionar.
- `MB_LAUNCH_BLOCKED`: activo.
- Flash física/USB Host/RAM execution: bloqueados.
