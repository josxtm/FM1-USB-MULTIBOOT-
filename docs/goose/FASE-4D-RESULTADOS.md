# FASE 4D — Resultados

**Rama:** `goose/fase3b-4a-preflight`
**PR:** #8 (Draft)
**Build prevista:** `FM1_RAM_Loader_v1.0_comp11.apk` / `versionCode 11`

## Desarrollo

- Android MIDI conserva únicamente la consulta fija `0x12`.
- El informe técnico exportable registra versión real de APK, conexión, identidad no sensible y eventos de sesión. No incluye UID, claves, seriales ni credenciales.
- `MidiManager.DeviceCallback` cancela y cierra la sesión cuando Android retira el dispositivo.
- Añadido `FirmwareAssessment`: distingue BIN raw, contenedor FWS, integridad, compatibilidad y método de ejecución sin convertir integridad en ejecutabilidad.
- Añadido `CoverFlowModel`: estados NO_CARD, EMPTY, BROWSE, INSPECT y ERROR; navegación envolvente e inspección sin lanzamiento.
- Añadido `RamExecutionPlanner`: valida el APP1 conocido para simulación y rechaza XIP/FLASH/DESCONOCIDO como no evidenciados.
- Se conserva FM1CAT v1 y la política `UNVERIFIED`; los archivos originales no se transforman.

## Seguridad

No se habilitaron escritura/lectura Flash ambigua, `0x25`, `0x30`, ejecución física APP1, USB Host físico ni alimentación experimental. La región `[0x00000,0x04000)` y DARK-1 quedan protegidas.

## Estado

- **Software:** modelos de biblioteca, selector, planificación RAM y reporte MIDI implementados; tests añadidos.
- **Hardware previamente documentado:** bootloader 2.4-395E6CC, menú de recuperación y retorno a DARK-1.
- **Pendiente físico:** respuesta MIDI real, reservas RAM/ABI, VBUS/CC, lector microSD y recuperación integral.

La compatibilidad preliminar del protocolo 2.x no autoriza ejecutar firmware. La fase no declara multiboot físico funcional.
